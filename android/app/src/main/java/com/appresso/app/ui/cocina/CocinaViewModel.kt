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
)

/**
 * Pantalla de cocina en tiempo real (SCRUM-92, RF-23, RF-24, RF-26): las comandas activas y sus
 * productos, de solo lectura. La lista siempre sale de `GET /api/comandas/cocina`; los eventos del
 * WebSocket solo avisan que hay que volver a pedirla ([alCambiar], ver `cambiosDeCocina`), así la
 * pantalla nunca queda con una foto parcial.
 *
 * Si llega un cambio mientras se está pidiendo la lista, la consulta se repite al terminar: el cambio
 * pudo ocurrir después de que el servidor armó esa respuesta, y perderlo dejaría a cocina con una
 * comanda en un estado viejo hasta el siguiente evento.
 */
class CocinaViewModel(
    private val repositorio: CocinaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(CocinaUiState())
    val estado: StateFlow<CocinaUiState> = _estado.asStateFlow()

    private var enCurso = false
    private var otraVez = false

    init {
        consultar(silencioso = false)
    }

    fun cargar() = consultar(silencioso = false)

    /**
     * Cocina cambió (un evento del WebSocket o una reconexión): vuelve a pedir la lista sin indicador
     * de carga ni avisos de error. La pantalla lo llama mientras está visible (ver `AppressoRaiz`).
     */
    fun alCambiar(@Suppress("UNUSED_PARAMETER") cambio: CambioCocina) = consultar(silencioso = true)

    private fun consultar(silencioso: Boolean) {
        if (enCurso) {
            otraVez = true
            return
        }
        enCurso = true
        if (!silencioso) _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                var silenciosa = silencioso
                do {
                    otraVez = false
                    aplicar(repositorio.consultar(), silenciosa)
                    silenciosa = true
                } while (otraVez)
            } finally {
                enCurso = false
                _estado.update { it.copy(cargando = false) }
            }
        }
    }

    private fun aplicar(resultado: ResultadoCatalogo<List<ComandaCocina>>, silenciosa: Boolean) {
        when (resultado) {
            is ResultadoCatalogo.Exito -> _estado.update {
                it.copy(cargando = false, comandas = resultado.valor, error = null)
            }

            else -> if (!silenciosa) {
                _estado.update { it.copy(cargando = false, error = resultado.aError()) }
            }
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
        fun factory(repositorio: CocinaRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { CocinaViewModel(repositorio) } }
    }
}
