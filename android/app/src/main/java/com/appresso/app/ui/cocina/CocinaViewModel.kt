package com.appresso.app.ui.cocina

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.cocina.CocinaRepository
import com.appresso.app.data.cocina.ComandaCocina
import com.appresso.app.tiemporeal.CambioCocina
import com.appresso.app.tiemporeal.MedidorCocina
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué no se pudo cargar la lista; el texto que ve la persona se arma en la pantalla. */
sealed interface ErrorCocina {
    data object SinConexion : ErrorCocina
    data object ErrorDelServidor : ErrorCocina
    data class SinPermiso(val mensajeServidor: String?) : ErrorCocina
}

data class CocinaUiState(
    /** De la más antigua a la más reciente por hora de envío (el orden lo da el servidor). */
    val comandas: List<ComandaCocina> = emptyList(),
    /** Solo la primera carga muestra el indicador; las del tiempo real son silenciosas. */
    val cargando: Boolean = false,
    /** Solo se muestra si no hay nada que enseñar: con una lista en pantalla se conserva y no se avisa. */
    val error: ErrorCocina? = null,
    /**
     * La última actualización automática falló: la lista que se ve es la última que llegó y puede estar
     * atrasada. Se reintenta sola; el aviso se quita cuando una consulta sale bien.
     */
    val desactualizada: Boolean = false,
)

/** Esperas entre reintentos tras fallar una actualización en silencio; la última se repite. */
internal val ESPERAS_REINTENTO_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)

/**
 * Pantalla de cocina en tiempo real (SCRUM-92, RF-23, RF-24, RF-26): las comandas activas y sus
 * productos, de solo lectura. La lista siempre sale de `GET /api/comandas/cocina`; los eventos del
 * WebSocket solo avisan que hay que volver a pedirla ([alCambiar], ver `cambiosDeCocina`), así la
 * pantalla nunca queda con una foto parcial. Con un [medidor], cada cambio se mide desde que el
 * servidor lo publicó hasta que la lista nueva está en pantalla (plazo de 3 s, SCRUM-93).
 *
 * Si llega un cambio mientras se está pidiendo la lista, la consulta se repite al terminar: el cambio
 * pudo ocurrir después de que el servidor armó esa respuesta, y perderlo dejaría a cocina con una
 * comanda en un estado viejo hasta el siguiente evento. Nunca hay dos consultas a la vez, así una
 * respuesta vieja no puede pisar una nueva.
 *
 * Si una actualización automática falla (sin conexión o error del servidor), la pantalla conserva la
 * última lista, avisa que puede estar atrasada ([CocinaUiState.desactualizada]) y reintenta sola con
 * esperas crecientes hasta lograrlo.
 */
class CocinaViewModel(
    private val repositorio: CocinaRepository,
    private val medidor: MedidorCocina? = null,
    private val esperasReintentoMs: List<Long> = ESPERAS_REINTENTO_MS,
) : ViewModel() {

    private val _estado = MutableStateFlow(CocinaUiState())
    val estado: StateFlow<CocinaUiState> = _estado.asStateFlow()

    private var enCurso = false
    private var otraVez = false
    private var reintento: Job? = null
    private var fallosSeguidos = 0

    /** Cambios recibidos que aún no se han visto reflejados en pantalla (para medir el plazo, SCRUM-93). */
    private val porMedir = ArrayList<CambioCocina>()

    init {
        consultar(silencioso = false)
    }

    fun cargar() = consultar(silencioso = false)

    /**
     * Cocina cambió (un evento del WebSocket o una reconexión): vuelve a pedir la lista sin indicador
     * de carga ni avisos de error. La pantalla lo llama mientras está visible (ver `AppressoRaiz`).
     */
    fun alCambiar(cambio: CambioCocina) {
        if (medidor != null) porMedir.add(cambio)
        consultar(silencioso = true)
    }

    private fun consultar(silencioso: Boolean) {
        if (enCurso) {
            otraVez = true
            return
        }
        enCurso = true
        reintento?.cancel()
        reintento = null
        if (!silencioso) _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            var fallo = false
            try {
                var silenciosa = silencioso
                do {
                    otraVez = false
                    // Los cambios que ya llegaron quedan cubiertos por esta consulta; los que lleguen
                    // mientras tanto esperan a la siguiente vuelta.
                    val cubiertos = porMedir.toList()
                    porMedir.clear()
                    val resultado = repositorio.consultar()
                    val mostrada = aplicar(resultado, silenciosa)
                    if (mostrada) cubiertos.forEach { medidor?.alMostrarse(it) }
                    fallo = !mostrada && resultado.esTransitorio()
                    silenciosa = true
                } while (otraVez)
            } finally {
                enCurso = false
                _estado.update { it.copy(cargando = false) }
            }
            // Una lista que no se pudo actualizar no se deja así hasta el próximo evento.
            if (fallo && silencioso) {
                programarReintento()
            } else if (!fallo) {
                fallosSeguidos = 0
            }
        }
    }

    private fun ResultadoCatalogo<*>.esTransitorio() =
        this == ResultadoCatalogo.SinConexion || this == ResultadoCatalogo.ErrorDelServidor

    private fun programarReintento() {
        val espera = esperasReintentoMs[minOf(fallosSeguidos, esperasReintentoMs.size - 1)]
        fallosSeguidos++
        reintento = viewModelScope.launch {
            delay(espera)
            reintento = null
            consultar(silencioso = true)
        }
    }

    /** Devuelve true si la lista nueva quedó en pantalla. */
    private fun aplicar(resultado: ResultadoCatalogo<List<ComandaCocina>>, silenciosa: Boolean): Boolean =
        when (resultado) {
            is ResultadoCatalogo.Exito -> {
                _estado.update {
                    it.copy(cargando = false, comandas = resultado.valor, error = null, desactualizada = false)
                }
                true
            }

            else -> {
                if (!silenciosa) {
                    _estado.update { it.copy(cargando = false, error = resultado.aError()) }
                } else if (resultado.esTransitorio()) {
                    _estado.update { it.copy(desactualizada = true) }
                }
                false
            }
        }

    /** 401 no muestra nada (el cliente HTTP cierra la sesión); 403 y sin conexión también los avisa ese cliente, pero aquí se explican porque la pantalla queda vacía. */
    private fun ResultadoCatalogo<*>.aError(): ErrorCocina? = when (this) {
        is ResultadoCatalogo.Exito -> null
        ResultadoCatalogo.SesionInvalida -> null
        is ResultadoCatalogo.SinPermiso -> ErrorCocina.SinPermiso(mensaje)
        ResultadoCatalogo.SinConexion -> ErrorCocina.SinConexion
        else -> ErrorCocina.ErrorDelServidor
    }

    companion object {
        fun factory(repositorio: CocinaRepository, medidor: MedidorCocina? = null): ViewModelProvider.Factory =
            viewModelFactory { initializer { CocinaViewModel(repositorio, medidor) } }
    }
}
