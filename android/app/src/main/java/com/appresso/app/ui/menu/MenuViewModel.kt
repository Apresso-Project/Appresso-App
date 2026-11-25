package com.appresso.app.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué no se pudo cargar el menú; el texto que ve la persona se arma en la pantalla. */
sealed interface ErrorMenu {
    data object SinConexion : ErrorMenu
    data object ErrorDelServidor : ErrorMenu
    data class SinPermiso(val mensajeServidor: String?) : ErrorMenu
}

/** Esperas entre reintentos tras fallar una actualización en silencio; la última se repite. */
internal val ESPERAS_REINTENTO_MS_MENU = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)

data class MenuUiState(
    /** Solo categorías con productos disponibles, en el orden del servidor. */
    val categorias: List<MenuCategoria> = emptyList(),
    /** null = todas las categorías. */
    val filtroCategoriaId: Long? = null,
    val cargando: Boolean = false,
    /** Solo se muestra cuando no hay nada que enseñar; con datos viejos en pantalla no se avisa. */
    val error: ErrorMenu? = null,
    /** La última actualización automática falló: se ve el último menú recibido, que puede estar atrasado. */
    val desactualizado: Boolean = false,
) {
    val visibles: List<MenuCategoria>
        get() = if (filtroCategoriaId == null) categorias else categorias.filter { it.id == filtroCategoriaId }
}

/**
 * Menú de solo lectura (SCRUM-91, RF-11): categorías con productos disponibles, su precio y sus
 * existencias actuales; lo agotado o desactivado no llega (lo deja fuera el backend, RF-13).
 *
 * [cambios] avisa que el catálogo o las existencias cambiaron en otro dispositivo; cada aviso
 * vuelve a pedir el menú en silencio, así el precio y el stock en pantalla se mantienen al día.
 *
 * Nunca hay dos consultas a la vez, así una respuesta vieja no pisa una nueva. Si llega un cambio
 * mientras se consulta (también durante la carga inicial), la consulta se repite al terminar: la
 * respuesta en curso pudo armarse antes del cambio y dejaría visible, por ejemplo, un producto que ya se
 * agotó. Si una actualización automática falla, se conserva el último menú, se avisa que puede estar
 * atrasado y se reintenta solo con esperas crecientes.
 */
class MenuViewModel(
    private val repositorio: MenuRepository,
    cambios: Flow<Unit> = emptyFlow(),
    private val esperasReintentoMs: List<Long> = ESPERAS_REINTENTO_MS_MENU,
) : ViewModel() {

    private val _estado = MutableStateFlow(MenuUiState())
    val estado: StateFlow<MenuUiState> = _estado.asStateFlow()

    private var enCurso = false
    private var otraVez = false
    private var reintento: Job? = null
    private var fallosSeguidos = 0
    private var actualizacionesActivas = true

    init {
        cargar()
        viewModelScope.launch { cambios.collect { recargarEnSilencio() } }
    }

    fun cargar() = cargar(silencioso = false)

    /** Vuelve a pedir el menú sin indicador de carga ni avisos de error (ver `AppressoRaiz`). */
    fun recargarEnSilencio() {
        if (actualizacionesActivas) cargar(silencioso = true)
    }

    fun pausarActualizaciones() {
        actualizacionesActivas = false
        otraVez = false
        reintento?.cancel()
        reintento = null
    }

    fun reanudarActualizaciones() {
        if (actualizacionesActivas) return
        actualizacionesActivas = true
        if (_estado.value.desactualizado) cargar(silencioso = true)
    }

    private fun cargar(silencioso: Boolean) {
        if (enCurso) {
            otraVez = true
            return
        }
        enCurso = true
        reintento?.cancel()
        reintento = null
        if (!silencioso) _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            var reintentar: Boolean
            try {
                var silenciosa = silencioso
                do {
                    otraVez = false
                    val resultado = repositorio.consultar()
                    val aplicado = aplicar(resultado, silenciosa)
                    reintentar = silenciosa && !aplicado && resultado.esTransitorio()
                    silenciosa = true
                } while (otraVez)
            } finally {
                enCurso = false
                _estado.update { it.copy(cargando = false) }
            }
            if (reintentar && actualizacionesActivas) {
                programarReintento()
            } else {
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
            cargar(silencioso = true)
        }
    }

    /** Devuelve true si el menú nuevo quedó en pantalla. */
    private fun aplicar(resultado: ResultadoCatalogo<List<MenuCategoria>>, silenciosa: Boolean): Boolean =
        when (resultado) {
            is ResultadoCatalogo.Exito -> {
                _estado.update {
                    it.copy(
                        cargando = false,
                        categorias = resultado.valor,
                        error = null,
                        desactualizado = false,
                        // Si la categoría filtrada ya no tiene productos, se vuelve a «todas».
                        filtroCategoriaId = it.filtroCategoriaId
                            ?.takeIf { id -> resultado.valor.any { c -> c.id == id } },
                    )
                }
                true
            }

            else -> {
                if (!silenciosa) {
                    _estado.update { it.copy(cargando = false, error = resultado.aError()) }
                } else if (resultado.esTransitorio()) {
                    _estado.update { it.copy(desactualizado = true) }
                }
                false
            }
        }

    fun filtrarPorCategoria(id: Long?) = _estado.update { it.copy(filtroCategoriaId = id) }

    /**
     * 401 no muestra nada (el cliente HTTP ya cierra la sesión). Un 403 o la falta de conexión
     * tampoco se repiten aquí si el cliente HTTP ya los avisó con su mensaje (`BusEventosRed`); la
     * pantalla igual deja el botón «Reintentar», así que se muestran solo si no hay nada que ver.
     */
    private fun ResultadoCatalogo<*>.aError(): ErrorMenu? = when (this) {
        is ResultadoCatalogo.Exito -> null
        ResultadoCatalogo.SesionInvalida -> null
        is ResultadoCatalogo.SinPermiso -> ErrorMenu.SinPermiso(mensaje)
        ResultadoCatalogo.SinConexion -> ErrorMenu.SinConexion
        // 400, 404 y 409 no existen en este endpoint: si llegan, algo falló en el servidor.
        else -> ErrorMenu.ErrorDelServidor
    }

    companion object {
        fun factory(repositorio: MenuRepository, cambios: Flow<Unit> = emptyFlow()): ViewModelProvider.Factory =
            viewModelFactory { initializer { MenuViewModel(repositorio, cambios) } }
    }
}
