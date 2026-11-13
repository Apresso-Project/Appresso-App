package com.appresso.app.tiemporeal

enum class EstadoConexion {
    /** No se quiere conexión (app en segundo plano o detenida). */
    DESCONECTADO,

    /** Abriendo el WebSocket y esperando el `CONNECTED` del servidor. */
    CONECTANDO,

    /** Conectado y suscrito: llegan eventos. */
    CONECTADO,

    /** Se cayó; hay un reintento programado (ver `esperaMs`). Aquí se muestra «Sin conexión» (RNF-21). */
    ESPERANDO_REINTENTO,
}

/** Lo que el cliente le avisa a la app. Se llama desde hilos de fondo: la app debe pasar a su hilo. */
interface EscuchaCliente {
    fun alCambiarEstado(estado: EstadoConexion, esperaMs: Long?, motivo: String?) {}

    fun alRecibirEvento(topico: String, evento: EventoTiempoReal) {}

    /**
     * La app debe volver a consultar el estado por REST (comandas del día y catálogo) porque pudo
     * perder eventos: se reconectó, la secuencia saltó o el servidor se reinició.
     */
    fun alNecesitarResincronizacion(motivo: String) {}
}

/**
 * Cliente STOMP sobre un WebSocket (SCRUM-29) con reconexión automática (SCRUM-30).
 *
 * Qué hace:
 * - Conecta, manda `CONNECT` con latidos cada [latidoMs] y espera `CONNECTED`.
 * - Se suscribe a los tópicos pedidos con [suscribir]. Las suscripciones se recuerdan y se
 *   **restauran solas** después de cada reconexión.
 * - Detecta caídas: cierre o error del socket, `ERROR` del servidor, falta de `CONNECTED` o 2,5
 *   latidos sin recibir nada.
 * - Reintenta con [PoliticaReintento] (1, 2, 4… 30 s ± 20 %). Al lograr conectar, el contador vuelve a 0.
 * - Ignora eventos repetidos (`id`) y avisa cuando hay que resincronizar por REST.
 *
 * Es seguro llamarlo desde cualquier hilo.
 *
 * @param detectarSaltos revisar que `secuencia` suba de 1 en 1. La secuencia del servidor es única
 * para todos los tópicos, así que solo tiene sentido si el cliente está suscrito a **todos**; si se
 * suscribe a solo uno, pasar `false` (la resincronización al reconectar sigue funcionando).
 */
class ClienteStomp(
    private val url: String,
    private val fabrica: FabricaConexion,
    private val programador: Programador,
    private val escucha: EscuchaCliente,
    private val politica: PoliticaReintento = PoliticaReintento(),
    private val latidoMs: Long = 10_000,
    private val esperaConnectedMs: Long = 10_000,
    private val detectarSaltos: Boolean = true,
) {
    private val lock = Any()

    private var estado = EstadoConexion.DESCONECTADO
    private var quiereConexion = false
    private var intento = 0
    private var generacion = 0 // cambia en cada conexión; ignora avisos de sockets viejos
    private var conexion: Conexion? = null
    private var haConectadoAntes = false
    private var ultimaActividadMs = 0L
    private var ultimaSecuencia: Long? = null

    private val suscripciones = LinkedHashMap<String, String>() // destino -> id
    private var contadorSuscripciones = 0
    private val idsVistos = LinkedHashSet<String>()

    private var tareaReintento: Cancelable? = null
    private var tareaConnected: Cancelable? = null
    private var tareaLatido: Cancelable? = null

    fun estadoActual(): EstadoConexion = synchronized(lock) { estado }

    /** Empieza a conectar y a reconectar solo hasta que se llame [detener]. */
    fun iniciar() {
        synchronized(lock) {
            if (quiereConexion) return@synchronized
            quiereConexion = true
            intento = 0
            conectar()
        }
    }

    /** Cierra la conexión y deja de reintentar (p. ej. la app pasa a segundo plano). */
    fun detener() {
        synchronized(lock) {
            quiereConexion = false
            cancelarTareas()
            generacion++
            val actual = conexion
            conexion = null
            if (actual != null) {
                if (estado == EstadoConexion.CONECTADO) actual.enviar(CodecoStomp.codificar(MarcoStomp("DISCONNECT")))
                actual.cerrar()
            }
            cambiarEstado(EstadoConexion.DESCONECTADO, null, null)
        }
    }

    /** Pide recibir los eventos de [destino]. Si ya hay conexión se suscribe en el acto. */
    fun suscribir(destino: String) {
        synchronized(lock) {
            if (suscripciones.containsKey(destino)) return@synchronized
            val id = "sub-${contadorSuscripciones++}"
            suscripciones[destino] = id
            if (estado == EstadoConexion.CONECTADO) enviarSuscripcion(destino, id)
        }
    }

    fun desuscribir(destino: String) {
        synchronized(lock) {
            val id = suscripciones.remove(destino) ?: return@synchronized
            if (estado == EstadoConexion.CONECTADO) {
                conexion?.enviar(CodecoStomp.codificar(MarcoStomp("UNSUBSCRIBE", mapOf("id" to id))))
            }
        }
    }

    /** Volvió la red (ConnectivityManager): reintenta ya, sin esperar el retroceso. */
    fun redDisponible() {
        synchronized(lock) {
            if (quiereConexion && estado == EstadoConexion.ESPERANDO_REINTENTO) {
                tareaReintento?.cancelar()
                intento = 0
                conectar()
            }
        }
    }

    /** Se perdió la red: no esperar a los latidos para darse cuenta. */
    fun redPerdida() {
        synchronized(lock) {
            if (quiereConexion && (estado == EstadoConexion.CONECTADO || estado == EstadoConexion.CONECTANDO)) {
                manejarCaida("Se perdió la red")
            }
        }
    }

    // ---------------------------------------------------------------- conexión

    private fun conectar() {
        generacion++
        val miGeneracion = generacion
        ultimaSecuencia = null
        cambiarEstado(EstadoConexion.CONECTANDO, null, null)
        conexion = fabrica.abrir(url, object : EscuchaConexion {
            override fun alAbrir() {
                synchronized(lock) {
                    if (miGeneracion == generacion) alAbrirSocket(miGeneracion)
                }
            }

            override fun alRecibirTexto(texto: String) {
                synchronized(lock) {
                    if (miGeneracion == generacion) alRecibirTextoSocket(texto)
                }
            }

            override fun alCerrar() {
                synchronized(lock) {
                    if (miGeneracion == generacion) manejarCaida("El servidor cerró la conexión")
                }
            }

            override fun alFallar(causa: Throwable) {
                synchronized(lock) {
                    if (miGeneracion == generacion) manejarCaida("Falló la conexión: ${causa.message ?: causa.javaClass.simpleName}")
                }
            }
        })
    }

    private fun alAbrirSocket(miGeneracion: Int) {
        ultimaActividadMs = programador.ahoraMs()
        conexion?.enviar(
            CodecoStomp.codificar(
                MarcoStomp(
                    "CONNECT",
                    linkedMapOf("accept-version" to "1.2", "host" to "appresso", "heart-beat" to "$latidoMs,$latidoMs"),
                ),
            ),
        )
        tareaConnected = programador.programar(esperaConnectedMs) {
            synchronized(lock) {
                if (miGeneracion == generacion && estado == EstadoConexion.CONECTANDO) {
                    manejarCaida("El servidor no respondió al CONNECT")
                }
            }
        }
    }

    private fun alRecibirTextoSocket(texto: String) {
        ultimaActividadMs = programador.ahoraMs()
        val marco = CodecoStomp.decodificar(texto) ?: return // latido
        when (marco.comando) {
            "CONNECTED" -> alConectado()
            "MESSAGE" -> alMensaje(marco)
            "ERROR" -> manejarCaida("Error del servidor: ${marco.cabeceras["message"] ?: marco.cuerpo}")
        }
    }

    private fun alConectado() {
        if (estado != EstadoConexion.CONECTANDO) return
        tareaConnected?.cancelar()
        intento = 0
        val esReconexion = haConectadoAntes
        haConectadoAntes = true
        cambiarEstado(EstadoConexion.CONECTADO, null, null)
        for ((destino, id) in suscripciones) enviarSuscripcion(destino, id) // restaura suscripciones
        programarLatido(generacion)
        if (esReconexion) {
            escucha.alNecesitarResincronizacion("Se reconectó: pudo perder eventos mientras estuvo sin conexión")
        }
    }

    private fun alMensaje(marco: MarcoStomp) {
        val evento = EventoTiempoReal.desdeJson(marco.cuerpo) ?: return
        if (!idsVistos.add(evento.id)) return // repetido
        if (idsVistos.size > 500) idsVistos.remove(idsVistos.first())

        val anterior = ultimaSecuencia
        ultimaSecuencia = evento.secuencia
        if (anterior != null) {
            if (evento.secuencia < anterior) {
                escucha.alNecesitarResincronizacion("El servidor se reinició (la secuencia bajó de $anterior a ${evento.secuencia})")
            } else if (detectarSaltos && evento.secuencia > anterior + 1) {
                escucha.alNecesitarResincronizacion("Se perdieron eventos (la secuencia saltó de $anterior a ${evento.secuencia})")
            }
        }
        escucha.alRecibirEvento(marco.cabeceras["destination"] ?: "", evento)
    }

    private fun enviarSuscripcion(destino: String, id: String) {
        conexion?.enviar(
            CodecoStomp.codificar(
                MarcoStomp("SUBSCRIBE", linkedMapOf("id" to id, "destination" to destino, "ack" to "auto")),
            ),
        )
    }

    // ---------------------------------------------------------------- latidos

    private fun programarLatido(miGeneracion: Int) {
        tareaLatido = programador.programar(latidoMs) {
            synchronized(lock) {
                if (miGeneracion != generacion || estado != EstadoConexion.CONECTADO) return@synchronized
                if (programador.ahoraMs() - ultimaActividadMs > latidoMs * 5 / 2) {
                    manejarCaida("No llegan latidos del servidor")
                } else {
                    conexion?.enviar("\n") // nuestro latido
                    programarLatido(miGeneracion)
                }
            }
        }
    }

    // ---------------------------------------------------------------- caída y reintento

    private fun manejarCaida(motivo: String) {
        cancelarTareas()
        generacion++ // los avisos del socket viejo ya no cuentan
        val vieja = conexion
        conexion = null
        vieja?.cerrar()

        if (!quiereConexion) {
            cambiarEstado(EstadoConexion.DESCONECTADO, null, motivo)
            return
        }
        val espera = politica.siguienteEsperaMs(intento)
        intento++
        cambiarEstado(EstadoConexion.ESPERANDO_REINTENTO, espera, motivo)
        tareaReintento = programador.programar(espera) {
            synchronized(lock) {
                if (quiereConexion && estado == EstadoConexion.ESPERANDO_REINTENTO) conectar()
            }
        }
    }

    private fun cancelarTareas() {
        tareaReintento?.cancelar()
        tareaConnected?.cancelar()
        tareaLatido?.cancelar()
        tareaReintento = null
        tareaConnected = null
        tareaLatido = null
    }

    private fun cambiarEstado(nuevo: EstadoConexion, esperaMs: Long?, motivo: String?) {
        estado = nuevo
        escucha.alCambiarEstado(nuevo, esperaMs, motivo)
    }
}
