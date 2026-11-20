package com.appresso.app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.ResultadoCatalogo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Problema del nombre de la categoría en el formulario. El ViewModel no conoce textos. */
sealed interface ErrorNombre {
    data object Obligatorio : ErrorNombre
    data object DemasiadoLargo : ErrorNombre
    data object Repetido : ErrorNombre

    /** Texto que envió el servidor (se muestra tal cual). */
    data class Servidor(val mensaje: String) : ErrorNombre
}

/** Problema general de una operación (no asociado al nombre). */
sealed interface ErrorCategorias {
    data object SinConexion : ErrorCategorias
    data object ErrorDelServidor : ErrorCategorias
    data object NoEncontrada : ErrorCategorias
    data class SinPermiso(val mensajeServidor: String?) : ErrorCategorias
    data class DatosInvalidos(val mensajeServidor: String?) : ErrorCategorias
}

data class FormularioCategoriaUiState(
    /** null = crear una categoría nueva; si no, la que se renombra. */
    val editando: Categoria? = null,
    val nombre: String = "",
    val errorNombre: ErrorNombre? = null,
    val error: ErrorCategorias? = null,
    val guardando: Boolean = false,
)

data class CategoriasUiState(
    val categorias: List<Categoria> = emptyList(),
    val cargando: Boolean = false,
    val errorLista: ErrorCategorias? = null,
    val formulario: FormularioCategoriaUiState? = null,
    /** Categoría para la que se pide confirmar la desactivación. */
    val confirmarDesactivar: Categoria? = null,
    /** Categoría con una desactivación o activación en curso. */
    val procesandoId: Long? = null,
    /** Error de la última desactivación/activación; se descarta con [CategoriasViewModel.descartarError]. */
    val errorAccion: ErrorCategorias? = null,
)

/**
 * Administración de categorías del catálogo (SCRUM-71, solo administrador): lista con las
 * desactivadas incluidas, crear, renombrar, desactivar y reactivar. Nada se borra.
 *
 * [cambios] avisa cuando el catálogo cambió en otro dispositivo (eventos `CATEGORIA_*` y
 * `PRODUCTO_*` de tiempo real; ver `cambiosDeCatalogo`). Cada aviso vuelve a pedir la lista por REST
 * sin mostrar el indicador de carga y sin tocar el formulario abierto.
 */
class CategoriasViewModel(
    private val repositorio: CatalogoRepository,
    cambios: Flow<Unit> = emptyFlow(),
) : ViewModel() {

    private val _estado = MutableStateFlow(CategoriasUiState())
    val estado: StateFlow<CategoriasUiState> = _estado.asStateFlow()

    init {
        cargar()
        viewModelScope.launch { cambios.collect { cargar(silencioso = true) } }
    }

    // ---------------------------------------------------------------- lista

    fun cargar() = cargar(silencioso = false)

    /**
     * Vuelve a pedir la lista sin indicador de carga ni avisos de error. La pantalla lo llama con
     * cada cambio de tiempo real mientras está visible (ver `AppressoRaiz`).
     */
    fun recargarEnSilencio() = cargar(silencioso = true)

    private fun cargar(silencioso: Boolean) {
        if (_estado.value.cargando) return
        if (!silencioso) _estado.update { it.copy(cargando = true, errorLista = null) }
        viewModelScope.launch {
            when (val r = repositorio.listarCategorias(incluirInactivas = true)) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(cargando = false, categorias = ordenar(r.valor), errorLista = null)
                }

                // Una actualización en segundo plano que falla no molesta: se conserva la lista.
                else -> if (!silencioso) {
                    val error = r.aError()
                    _estado.update { it.copy(cargando = false, errorLista = error) }
                }
            }
        }
    }

    fun descartarError() = _estado.update { it.copy(errorAccion = null) }

    // ---------------------------------------------------------------- desactivar / activar

    fun pedirDesactivar(categoria: Categoria) = _estado.update {
        if (it.procesandoId != null) it else it.copy(confirmarDesactivar = categoria, errorAccion = null)
    }

    fun cancelarDesactivar() = _estado.update { it.copy(confirmarDesactivar = null) }

    fun confirmarDesactivar() {
        val categoria = _estado.value.confirmarDesactivar ?: return
        _estado.update { it.copy(confirmarDesactivar = null) }
        cambiarEstado(categoria.id) { repositorio.desactivarCategoria(categoria.id) }
    }

    /** Reactivar no pide confirmación: es reversible y devuelve los productos al menú. */
    fun activar(categoria: Categoria) = cambiarEstado(categoria.id) { repositorio.activarCategoria(categoria.id) }

    private fun cambiarEstado(id: Long, llamada: suspend () -> ResultadoCatalogo<Categoria>) {
        if (_estado.value.procesandoId != null) return
        _estado.update { it.copy(procesandoId = id, errorAccion = null) }
        viewModelScope.launch {
            val r = llamada()
            if (r is ResultadoCatalogo.Exito) {
                // La lista se actualiza con la respuesta, sin volver a pedirla.
                _estado.update { it.copy(procesandoId = null, categorias = reemplazar(it.categorias, r.valor)) }
            } else {
                val error = r.aError(avisaElCliente = true)
                _estado.update { it.copy(procesandoId = null, errorAccion = error) }
                if (error == ErrorCategorias.NoEncontrada) cargar(silencioso = true)
            }
        }
    }

    // ---------------------------------------------------------------- formulario

    fun nuevaCategoria() = _estado.update { it.copy(formulario = FormularioCategoriaUiState()) }

    fun renombrarCategoria(categoria: Categoria) = _estado.update {
        it.copy(formulario = FormularioCategoriaUiState(editando = categoria, nombre = categoria.nombre))
    }

    fun onNombreChange(valor: String) = actualizarFormulario { it.copy(nombre = valor, errorNombre = null, error = null) }

    fun cerrarFormulario() = _estado.update {
        if (it.formulario?.guardando == true) it else it.copy(formulario = null)
    }

    fun guardar() {
        val estado = _estado.value
        val formulario = estado.formulario ?: return
        if (formulario.guardando) return
        val nombre = formulario.nombre.trim()
        val editando = formulario.editando

        validar(nombre, estado.categorias, editando?.id)?.let { problema ->
            actualizarFormulario { it.copy(errorNombre = problema, error = null) }
            return
        }
        // Renombrar con el mismo nombre no es un cambio: no se llama al servidor.
        if (editando != null && nombre == editando.nombre) {
            _estado.update { it.copy(formulario = null) }
            return
        }

        actualizarFormulario { it.copy(guardando = true, errorNombre = null, error = null) }
        viewModelScope.launch {
            val r = if (editando == null) {
                repositorio.crearCategoria(nombre)
            } else {
                repositorio.renombrarCategoria(editando.id, nombre)
            }
            when (r) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(formulario = null, categorias = reemplazar(it.categorias, r.valor))
                }

                else -> {
                    val delServidor = when (r) {
                        is ResultadoCatalogo.DatosInvalidos -> r.campos["nombre"]
                        is ResultadoCatalogo.Conflicto -> r.campos["nombre"]
                        else -> null
                    }
                    // Si el servidor señaló el nombre, el aviso general sobra.
                    val errorNombre = delServidor?.let { ErrorNombre.Servidor(it) }
                    val general = if (errorNombre != null) null else r.aError(avisaElCliente = true)
                    actualizarFormulario { it.copy(guardando = false, errorNombre = errorNombre, error = general) }
                    if (general == ErrorCategorias.NoEncontrada) cargar(silencioso = true)
                }
            }
        }
    }

    private fun actualizarFormulario(cambio: (FormularioCategoriaUiState) -> FormularioCategoriaUiState) =
        _estado.update { estado -> estado.copy(formulario = estado.formulario?.let(cambio)) }

    /**
     * Un 401 (token vencido) no muestra nada: el cliente HTTP ya cerró la sesión y la app vuelve al
     * login. Con [avisaElCliente], la falta de conexión y el 403 tampoco se repiten aquí, porque el
     * cliente HTTP ya los avisó en pantalla (`AppressoRaiz`); la lista los conserva para ofrecer
     * «Reintentar».
     */
    private fun ResultadoCatalogo<*>.aError(avisaElCliente: Boolean = false): ErrorCategorias? = when (this) {
        is ResultadoCatalogo.Exito -> null
        is ResultadoCatalogo.DatosInvalidos -> ErrorCategorias.DatosInvalidos(mensaje)
        // Un conflicto sin campo (no debería pasar con categorías) se muestra como mensaje del servidor.
        is ResultadoCatalogo.Conflicto -> ErrorCategorias.DatosInvalidos(mensaje)
        ResultadoCatalogo.SesionInvalida -> null
        is ResultadoCatalogo.SinPermiso -> if (avisaElCliente) null else ErrorCategorias.SinPermiso(mensaje)
        ResultadoCatalogo.NoEncontrado -> ErrorCategorias.NoEncontrada
        ResultadoCatalogo.SinConexion -> if (avisaElCliente) null else ErrorCategorias.SinConexion
        ResultadoCatalogo.ErrorDelServidor -> ErrorCategorias.ErrorDelServidor
    }

    companion object {
        /** Igual que el servidor (docs/api-catalogo.md). */
        const val MAX_NOMBRE = 80

        /** Mismas reglas que el backend; el servidor las vuelve a validar. */
        internal fun validar(nombre: String, existentes: List<Categoria>, idPropio: Long?): ErrorNombre? = when {
            nombre.isEmpty() -> ErrorNombre.Obligatorio
            nombre.length > MAX_NOMBRE -> ErrorNombre.DemasiadoLargo
            existentes.any { it.id != idPropio && it.nombre.equals(nombre, ignoreCase = true) } -> ErrorNombre.Repetido
            else -> null
        }

        private fun ordenar(lista: List<Categoria>) = lista.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })

        /** Cambia la categoría si ya estaba en la lista o la agrega; mantiene el orden por nombre. */
        private fun reemplazar(lista: List<Categoria>, nueva: Categoria) =
            ordenar(lista.filterNot { it.id == nueva.id } + nueva)

        fun factory(repositorio: CatalogoRepository, cambios: Flow<Unit> = emptyFlow()): ViewModelProvider.Factory =
            viewModelFactory { initializer { CategoriasViewModel(repositorio, cambios) } }
    }
}
