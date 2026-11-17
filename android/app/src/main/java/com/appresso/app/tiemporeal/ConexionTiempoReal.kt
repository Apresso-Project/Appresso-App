package com.appresso.app.tiemporeal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de la conexión tal como lo muestra la pantalla («Sin conexión», «Reintentando en 4 s»…). */
data class InfoConexion(
    val estado: EstadoConexion = EstadoConexion.DESCONECTADO,
    val esperaMs: Long? = null,
    val motivo: String? = null,
)

/**
 * Punto de entrada para la app (SCRUM-29 y SCRUM-30): une [ClienteStomp] con Android.
 *
 * - **Ciclo de vida:** con [adjuntar], se conecta cuando la pantalla pasa a primer plano (`onStart`)
 *   y se desconecta en segundo plano (`onStop`) para no gastar batería ni datos.
 * - **Red:** con `ConnectivityManager.NetworkCallback`, al volver la red reconecta de inmediato y
 *   al perderla lo detecta sin esperar los latidos.
 * - **Salida:** [estado] (StateFlow, para el indicador «Sin conexión»), [eventos] (cada evento
 *   recibido) y [resincronizar] (la app debe consultar por REST comandas y catálogo).
 *
 * Uso típico:
 * ```
 * val tiempoReal = ConexionTiempoReal(applicationContext, "ws://192.168.1.10:8080/ws/websocket")
 * tiempoReal.adjuntar(lifecycle)
 * lifecycleScope.launch { tiempoReal.eventos.collect { actualizarPantalla(it) } }
 * lifecycleScope.launch { tiempoReal.resincronizar.collect { recargarPorRest() } }
 * ```
 * Los flujos se emiten desde un hilo de fondo; `collect` desde `lifecycleScope` ya los lleva al hilo principal.
 */
class ConexionTiempoReal(
    context: Context,
    url: String,
    private val topicos: Set<String> = Topicos.TODOS,
    fabrica: FabricaConexion = FabricaOkHttp(),
) : DefaultLifecycleObserver {

    private val _estado = MutableStateFlow(InfoConexion())
    val estado: StateFlow<InfoConexion> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoTiempoReal>(extraBufferCapacity = 256)
    val eventos: SharedFlow<EventoTiempoReal> = _eventos.asSharedFlow()

    private val _resincronizar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val resincronizar: SharedFlow<String> = _resincronizar.asSharedFlow()

    private val cliente = ClienteStomp(
        url = url,
        fabrica = fabrica,
        programador = ProgramadorExecutor(),
        escucha = object : EscuchaCliente {
            override fun alCambiarEstado(estado: EstadoConexion, esperaMs: Long?, motivo: String?) {
                _estado.value = InfoConexion(estado, esperaMs, motivo)
            }

            override fun alRecibirEvento(topico: String, evento: EventoTiempoReal) {
                _eventos.tryEmit(evento)
            }

            override fun alNecesitarResincronizacion(motivo: String) {
                _resincronizar.tryEmit(motivo)
            }
        },
        // La secuencia es global: solo se puede vigilar si se escuchan todos los tópicos.
        detectarSaltos = topicos == Topicos.TODOS,
    ).also { c -> topicos.forEach(c::suscribir) }

    private val conectividad = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callbackRegistrado = false

    private val callbackRed = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = cliente.redDisponible()

        override fun onLost(network: Network) = cliente.redPerdida()
    }

    /** Conecta (y reconecta solo) hasta que se llame [detener]. */
    fun iniciar() {
        if (!callbackRegistrado) {
            conectividad.registerDefaultNetworkCallback(callbackRed)
            callbackRegistrado = true
        }
        cliente.iniciar()
    }

    /** Cierra la conexión y deja de reintentar. */
    fun detener() {
        if (callbackRegistrado) {
            try {
                conectividad.unregisterNetworkCallback(callbackRed)
            } catch (e: IllegalArgumentException) {
                // ya estaba desregistrado
            }
            callbackRegistrado = false
        }
        cliente.detener()
    }

    /** Conecta en `onStart` y desconecta en `onStop` de [lifecycle]. */
    fun adjuntar(lifecycle: Lifecycle) = lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) = iniciar()

    override fun onStop(owner: LifecycleOwner) = detener()

    companion object {
        /** Para pantallas que todavía no tienen conexión. */
        val SIN_CONEXION: StateFlow<InfoConexion> = MutableStateFlow(InfoConexion())
    }
}
