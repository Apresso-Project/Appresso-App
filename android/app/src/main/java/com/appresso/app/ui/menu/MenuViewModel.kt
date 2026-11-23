package com.appresso.app.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuRepository
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

data class MenuUiState(
    /** Solo categorías con productos disponibles, en el orden del servidor. */
    val categorias: List<MenuCategoria> = emptyList(),
    /** null = todas las categorías. */
    val filtroCategoriaId: Long? = null,
    val cargando: Boolean = false,
    /** Solo se muestra cuando no hay nada que enseñar; con datos viejos en pantalla no se avisa. */
    val error: ErrorMenu? = null,
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
 */
class MenuViewModel(
    private val repositorio: MenuRepository,
    cambios: Flow<Unit> = emptyFlow(),
) : ViewModel() {

    private val _estado = MutableStateFlow(MenuUiState())
    val estado: StateFlow<MenuUiState> = _estado.asStateFlow()

    init {
        cargar()
        viewModelScope.launch { cambios.collect { cargar(silencioso = true) } }
    }

    fun cargar() = cargar(silencioso = false)

    /** Vuelve a pedir el menú sin indicador de carga ni avisos de error (ver `AppressoRaiz`). */
    fun recargarEnSilencio() = cargar(silencioso = true)

    private fun cargar(silencioso: Boolean) {
        if (_estado.value.cargando) return
        if (!silencioso) _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = repositorio.consultar()) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(
                        cargando = false,
                        categorias = r.valor,
                        error = null,
                        // Si la categoría filtrada ya no tiene productos, se vuelve a «todas».
                        filtroCategoriaId = it.filtroCategoriaId?.takeIf { id -> r.valor.any { c -> c.id == id } },
                    )
                }

                else -> if (!silencioso) {
                    _estado.update { it.copy(cargando = false, error = r.aError()) }
                }
            }
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
