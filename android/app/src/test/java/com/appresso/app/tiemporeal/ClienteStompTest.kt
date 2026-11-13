package com.appresso.app.tiemporeal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Reloj y tareas simulados: `avanzar(ms)` ejecuta lo que toque en orden, sin esperar de verdad. */
class ProgramadorFalso : Programador {
    private class Tarea(val cuando: Long, val orden: Int, val accion: Runnable, var cancelada: Boolean = false)

    var ahora = 0L
        private set
    private val tareas = ArrayList<Tarea>()
    private var contador = 0

    override fun ahoraMs() = ahora

    override fun programar(retrasoMs: Long, tarea: Runnable): Cancelable {
        val t = Tarea(ahora + retrasoMs, contador++, tarea)
        tareas.add(t)
        return Cancelable { t.cancelada = true }
    }

    fun pendientes() = tareas.count { !it.cancelada }

    fun avanzar(ms: Long) {
        val limite = ahora + ms
        while (true) {
            val siguiente = tareas.filter { !it.cancelada && it.cuando <= limite }
                .minWithOrNull(compareBy({ it.cuando }, { it.orden })) ?: break
            tareas.remove(siguiente)
            ahora = maxOf(ahora, siguiente.cuando)
            siguiente.accion.run()
        }
        ahora = limite
    }
}

/** Servidor falso: guarda lo que el cliente envía y permite simular lo que contesta o cuándo se cae. */
class FabricaFalsa : FabricaConexion {
    inner class SocketFalso(val escucha: EscuchaConexion) : Conexion {
        val enviados = ArrayList<MarcoStomp?>() // null = latido
        var cerrado = false

        override fun enviar(texto: String): Boolean {
            enviados.add(CodecoStomp.decodificar(texto))
            return true
        }

        override fun cerrar() {
            cerrado = true
        }

        fun comandosEnviados() = enviados.map { it?.comando ?: "LATIDO" }
        fun suscritos() = enviados.filter { it?.comando == "SUBSCRIBE" }.map { it!!.cabeceras["destination"] }

        // Lo que haría el servidor
        fun abrir() = escucha.alAbrir()
        fun responderConnected() = escucha.alRecibirTexto("CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\u0000")
        fun latidoDelServidor() = escucha.alRecibirTexto("\n")
        fun evento(destino: String, id: String, secuencia: Long, tipo: String = "COMANDA_CREADA") = escucha.alRecibirTexto(
            "MESSAGE\nsubscription:sub-0\nmessage-id:m$secuencia\ndestination:$destino\n\n" +
                """{"id":"$id","secuencia":$secuencia,"tipo":"$tipo","fecha":"2026-11-11T20:00:00Z","datos":{"comandaId":7,"estado":"PENDIENTE"}}""" +
                "\u0000",
        )
        fun caerse() = escucha.alFallar(java.io.IOException("se cayó"))
        fun cerrarElServidor() = escucha.alCerrar()
    }

    val sockets = ArrayList<SocketFalso>()
    val ultimo get() = sockets.last()

    override fun abrir(url: String, escucha: EscuchaConexion): Conexion = SocketFalso(escucha).also { sockets.add(it) }
}

class ClienteStompTest {

    private class Registro : EscuchaCliente {
        val estados = ArrayList<EstadoConexion>()
        val esperas = ArrayList<Long?>()
        val eventos = ArrayList<Pair<String, EventoTiempoReal>>()
        val resincronizaciones = ArrayList<String>()

        override fun alCambiarEstado(estado: EstadoConexion, esperaMs: Long?, motivo: String?) {
            estados.add(estado)
            if (estado == EstadoConexion.ESPERANDO_REINTENTO) esperas.add(esperaMs)
        }

        override fun alRecibirEvento(topico: String, evento: EventoTiempoReal) {
            eventos.add(topico to evento)
        }

        override fun alNecesitarResincronizacion(motivo: String) {
            resincronizaciones.add(motivo)
        }
    }

    private lateinit var reloj: ProgramadorFalso
    private lateinit var red: FabricaFalsa
    private lateinit var registro: Registro
    private lateinit var cliente: ClienteStomp

    @Before
    fun preparar() {
        reloj = ProgramadorFalso()
        red = FabricaFalsa()
        registro = Registro()
        // sin variación aleatoria para que las esperas sean exactas: 1, 2, 4 s…
        cliente = ClienteStomp("ws://test/ws/websocket", red, reloj, registro, PoliticaReintento(jitter = 0.0))
        cliente.suscribir(Topicos.COMANDAS)
        cliente.suscribir(Topicos.CATALOGO)
    }

    private fun conectarBien() {
        red.ultimo.abrir()
        red.ultimo.responderConnected()
    }

    // ------------------------------------------------------------ SCRUM-29: conexión y suscripción

    @Test
    fun alAbrirManda_CONNECT_conLatidos_YAlRecibirCONNECTED_SeSuscribeALosTopicos() {
        cliente.iniciar()
        assertEquals(EstadoConexion.CONECTANDO, cliente.estadoActual())

        red.ultimo.abrir()
        val connect = red.ultimo.enviados.single()!!
        assertEquals("CONNECT", connect.comando)
        assertEquals("1.2", connect.cabeceras["accept-version"])
        assertEquals("10000,10000", connect.cabeceras["heart-beat"])

        red.ultimo.responderConnected()
        assertEquals(EstadoConexion.CONECTADO, cliente.estadoActual())
        assertEquals(listOf(Topicos.COMANDAS, Topicos.CATALOGO), red.ultimo.suscritos())
    }

    @Test
    fun entregaLosEventosConSuTopico() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        val (topico, evento) = registro.eventos.single()
        assertEquals(Topicos.COMANDAS, topico)
        assertEquals("COMANDA_CREADA", evento.tipo)
        assertEquals(7L, evento.comanda()!!.comandaId)
    }

    @Test
    fun suscribirDespuesDeConectarEnviaSubscribeEnElActo() {
        cliente.iniciar()
        conectarBien()
        cliente.desuscribir(Topicos.CATALOGO)
        assertEquals("UNSUBSCRIBE", red.ultimo.enviados.last()!!.comando)
        cliente.suscribir(Topicos.CATALOGO)
        assertEquals(Topicos.CATALOGO, red.ultimo.enviados.last()!!.cabeceras["destination"])
    }

    @Test
    fun ignoraEventosRepetidosYBasura() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        red.ultimo.escucha.alRecibirTexto("MESSAGE\ndestination:/topic/comandas\n\nesto no es json\u0000")
        assertEquals(1, registro.eventos.size)
    }

    @Test
    fun detenerEnviaDISCONNECT_CierraYNoReintenta() {
        cliente.iniciar()
        conectarBien()
        val socket = red.ultimo
        cliente.detener()
        assertEquals("DISCONNECT", socket.enviados.last()!!.comando)
        assertTrue(socket.cerrado)
        assertEquals(EstadoConexion.DESCONECTADO, cliente.estadoActual())

        reloj.avanzar(120_000)
        assertEquals(1, red.sockets.size)
        assertEquals(0, reloj.pendientes())
    }

    @Test
    fun enviaSuLatidoCadaDiezSegundos() {
        cliente.iniciar()
        conectarBien()
        reloj.avanzar(10_000)
        assertEquals("LATIDO", red.ultimo.comandosEnviados().last())
        red.ultimo.latidoDelServidor()
        reloj.avanzar(10_000)
        assertEquals(2, red.ultimo.comandosEnviados().count { it == "LATIDO" })
    }

    // ------------------------------------------------------------ SCRUM-30: reconexión

    @Test
    fun siSeCaeReintentaConRetrocesoExponencial() {
        cliente.iniciar()
        // el servidor no está: cada intento falla al abrir
        for (esperado in listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000)) {
            red.ultimo.caerse()
            assertEquals(esperado, registro.esperas.last())
            assertEquals(EstadoConexion.ESPERANDO_REINTENTO, cliente.estadoActual())
            reloj.avanzar(esperado - 1)
            val intentosAntes = red.sockets.size
            reloj.avanzar(1)
            assertEquals(intentosAntes + 1, red.sockets.size)
        }
    }

    @Test
    fun alLograrConectarElRetrocesoVuelveAEmpezar() {
        cliente.iniciar()
        repeat(3) { red.ultimo.caerse(); reloj.avanzar(60_000) } // esperas 1, 2, 4 s
        conectarBien()
        red.ultimo.caerse()
        assertEquals(1_000L, registro.esperas.last())
    }

    @Test
    fun tras_reconectar_restauraLasSuscripciones_YPideResincronizar() {
        cliente.iniciar()
        conectarBien()
        assertTrue(registro.resincronizaciones.isEmpty()) // la primera conexión no necesita resincronizar

        red.ultimo.cerrarElServidor()
        reloj.avanzar(1_000)
        assertEquals(2, red.sockets.size)
        conectarBien()

        assertEquals(EstadoConexion.CONECTADO, cliente.estadoActual())
        assertEquals(listOf(Topicos.COMANDAS, Topicos.CATALOGO), red.ultimo.suscritos())
        assertEquals(1, registro.resincronizaciones.size)
        assertTrue(red.sockets[0].cerrado)
    }

    @Test
    fun siElServidorNoContestaCONNECTSeReintenta() {
        cliente.iniciar()
        red.ultimo.abrir() // el socket abre pero nadie contesta CONNECTED
        reloj.avanzar(10_000)
        assertEquals(EstadoConexion.ESPERANDO_REINTENTO, cliente.estadoActual())
        reloj.avanzar(1_000)
        assertEquals(2, red.sockets.size)
    }

    @Test
    fun sinLatidosDelServidorSeConsideraCaida() {
        cliente.iniciar()
        conectarBien()
        reloj.avanzar(20_000) // 2 latidos propios, nada del servidor: aún dentro del margen (25 s)
        assertEquals(EstadoConexion.CONECTADO, cliente.estadoActual())
        reloj.avanzar(10_000)
        assertEquals(EstadoConexion.ESPERANDO_REINTENTO, cliente.estadoActual())
    }

    @Test
    fun losLatidosDelServidorMantienenViva_LaConexion() {
        cliente.iniciar()
        conectarBien()
        repeat(12) {
            reloj.avanzar(10_000)
            red.ultimo.latidoDelServidor()
        }
        assertEquals(EstadoConexion.CONECTADO, cliente.estadoActual())
        assertEquals(1, red.sockets.size)
    }

    @Test
    fun unErrorDelServidorProvocaReintento() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.escucha.alRecibirTexto("ERROR\nmessage:algo falló\n\n\u0000")
        assertEquals(EstadoConexion.ESPERANDO_REINTENTO, cliente.estadoActual())
    }

    @Test
    fun siVuelveLaRedReconectaYaSinEsperar() {
        cliente.iniciar()
        repeat(4) { red.ultimo.caerse(); reloj.avanzar(60_000) } // ya va por esperas largas
        red.ultimo.caerse()
        assertEquals(16_000L, registro.esperas.last())
        val antes = red.sockets.size

        cliente.redDisponible()
        assertEquals(antes + 1, red.sockets.size)
        assertEquals(EstadoConexion.CONECTANDO, cliente.estadoActual())
        red.ultimo.caerse()
        assertEquals(1_000L, registro.esperas.last()) // el retroceso empezó de nuevo
    }

    @Test
    fun siSePierdeLaRedSeActuaAlInstante() {
        cliente.iniciar()
        conectarBien()
        cliente.redPerdida()
        assertEquals(EstadoConexion.ESPERANDO_REINTENTO, cliente.estadoActual())
        assertTrue(red.ultimo.cerrado)
    }

    @Test
    fun losAvisosDeUnSocketViejoNoHacenNada() {
        cliente.iniciar()
        conectarBien()
        val viejo = red.ultimo
        viejo.cerrarElServidor()
        reloj.avanzar(1_000)
        conectarBien()
        val intentos = registro.esperas.size
        viejo.caerse() // llega tarde un aviso del socket anterior
        viejo.cerrarElServidor()
        assertEquals(EstadoConexion.CONECTADO, cliente.estadoActual())
        assertEquals(intentos, registro.esperas.size)
    }

    // ------------------------------------------------------------ secuencia

    @Test
    fun unSaltoEnLaSecuenciaPideResincronizar() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        red.ultimo.evento(Topicos.COMANDAS, "b", 2)
        assertTrue(registro.resincronizaciones.isEmpty())
        red.ultimo.evento(Topicos.COMANDAS, "c", 5)
        assertEquals(1, registro.resincronizaciones.size)
        assertEquals(3, registro.eventos.size) // el evento igual se entrega
    }

    @Test
    fun siLaSecuenciaBajaElServidorSeReinicio() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.evento(Topicos.COMANDAS, "a", 40)
        red.ultimo.evento(Topicos.COMANDAS, "b", 1)
        assertEquals(1, registro.resincronizaciones.size)
    }

    @Test
    fun conUnSoloTopicoLosSaltosSonNormalesYNoSeAvisan() {
        val soloComandas = ClienteStomp("ws://test", red, reloj, registro, PoliticaReintento(jitter = 0.0), detectarSaltos = false)
        soloComandas.suscribir(Topicos.COMANDAS)
        soloComandas.iniciar()
        red.ultimo.abrir()
        red.ultimo.responderConnected()
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        red.ultimo.evento(Topicos.COMANDAS, "b", 9) // los 2..8 eran del catálogo
        assertFalse(registro.resincronizaciones.isNotEmpty())
    }

    @Test
    fun despuesDeReconectarLaPrimeraSecuenciaNoCuentaComoSalto() {
        cliente.iniciar()
        conectarBien()
        red.ultimo.evento(Topicos.COMANDAS, "a", 1)
        red.ultimo.cerrarElServidor()
        reloj.avanzar(1_000)
        conectarBien()
        val antes = registro.resincronizaciones.size // 1: por reconexión
        red.ultimo.evento(Topicos.COMANDAS, "b", 30)
        assertEquals(antes, registro.resincronizaciones.size)
    }
}
