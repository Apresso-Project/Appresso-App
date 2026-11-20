package com.appresso.app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ProductoCrearRequest
import com.appresso.app.data.catalogo.ProductoEditarRequest
import com.appresso.app.data.catalogo.ResultadoCatalogo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PestañaCatalogo {
    PRODUCTOS,
    CATEGORIAS,
}

data class CatalogoUiState(
    val cargando: Boolean = false,
    val categorias: List<Categoria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val pestañaSeleccionada: PestañaCatalogo = PestañaCatalogo.PRODUCTOS,
    val busqueda: String = "",
    val categoriaFiltroId: Long? = null,
    val esAdministrador: Boolean = true,
    val mostrarDialogoCrearCategoria: Boolean = false,
    val categoriaAEditar: Categoria? = null,
    val mostrarDialogoCrearProducto: Boolean = false,
    val productoAEditar: Producto? = null,
    val guardando: Boolean = false,
    val errorFormulario: String? = null,
    val erroresCampos: Map<String, String> = emptyMap(),
    val mensajeExito: String? = null,
    val errorGeneral: String? = null,
)

class CatalogoViewModel(
    private val repository: CatalogoRepository,
    private val esAdministrador: Boolean = true,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogoUiState(esAdministrador = esAdministrador))
    val uiState: StateFlow<CatalogoUiState> = _uiState.asStateFlow()

    init {
        cargarTodo()
    }

    fun cargarTodo() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, errorGeneral = null) }
            val resCats = repository.listarCategorias(todas = esAdministrador)
            val resProds = repository.listarProductos(todos = esAdministrador)

            when {
                resCats is ResultadoCatalogo.Exito && resProds is ResultadoCatalogo.Exito -> {
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            categorias = resCats.datos,
                            productos = resProds.datos,
                        )
                    }
                }
                resCats is ResultadoCatalogo.SinPermiso || resProds is ResultadoCatalogo.SinPermiso -> {
                    _uiState.update {
                        it.copy(cargando = false, errorGeneral = "No tienes permiso para ver el catálogo.")
                    }
                }
                resCats is ResultadoCatalogo.SinConexion || resProds is ResultadoCatalogo.SinConexion -> {
                    _uiState.update {
                        it.copy(cargando = false, errorGeneral = "Sin conexión con el servidor.")
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(cargando = false, errorGeneral = "Error al cargar el catálogo.")
                    }
                }
            }
        }
    }

    fun cambiarPestaña(pestaña: PestañaCatalogo) {
        _uiState.update { it.copy(pestañaSeleccionada = pestaña) }
    }

    fun onBusquedaCambio(nueva: String) {
        _uiState.update { it.copy(busqueda = nueva) }
    }

    fun filtrarPorCategoria(categoriaId: Long?) {
        _uiState.update { it.copy(categoriaFiltroId = categoriaId) }
    }

    // Categorías
    fun abrirCrearCategoria() {
        _uiState.update { it.copy(mostrarDialogoCrearCategoria = true, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun cerrarCrearCategoria() {
        _uiState.update { it.copy(mostrarDialogoCrearCategoria = false, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun abrirEditarCategoria(categoria: Categoria) {
        _uiState.update { it.copy(categoriaAEditar = categoria, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun cerrarEditarCategoria() {
        _uiState.update { it.copy(categoriaAEditar = null, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun guardarCategoria(nombre: String) {
        if (nombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El nombre de la categoría es obligatorio.") }
            return
        }
        if (nombre.trim().length > 80) {
            _uiState.update { it.copy(errorFormulario = "El nombre no puede exceder los 80 caracteres.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorFormulario = null, erroresCampos = emptyMap()) }
            when (val res = repository.crearCategoria(nombre.trim())) {
                is ResultadoCatalogo.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            mostrarDialogoCrearCategoria = false,
                            mensajeExito = "Categoría creada correctamente.",
                        )
                    }
                    cargarTodo()
                }
                is ResultadoCatalogo.Conflicto -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Ya existe una categoría con ese nombre.", erroresCampos = res.campos)
                    }
                }
                is ResultadoCatalogo.DatosInvalidos -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Nombre de categoría inválido.", erroresCampos = res.campos)
                    }
                }
                else -> {
                    _uiState.update { it.copy(guardando = false, errorFormulario = "Error al guardar categoría.") }
                }
            }
        }
    }

    fun renombrarCategoria(categoriaId: Long, nuevoNombre: String) {
        if (nuevoNombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El nombre de la categoría es obligatorio.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorFormulario = null, erroresCampos = emptyMap()) }
            when (val res = repository.renombrarCategoria(categoriaId, nuevoNombre.trim())) {
                is ResultadoCatalogo.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            categoriaAEditar = null,
                            mensajeExito = "Categoría renombrada correctamente.",
                        )
                    }
                    cargarTodo()
                }
                is ResultadoCatalogo.Conflicto -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Ya existe una categoría con ese nombre.", erroresCampos = res.campos)
                    }
                }
                else -> {
                    _uiState.update { it.copy(guardando = false, errorFormulario = "Error al renombrar categoría.") }
                }
            }
        }
    }

    fun toggleEstadoCategoria(categoria: Categoria) {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true) }
            val res = if (categoria.activa) {
                repository.desactivarCategoria(categoria.id)
            } else {
                repository.activarCategoria(categoria.id)
            }
            when (res) {
                is ResultadoCatalogo.Exito -> {
                    val accion = if (categoria.activa) "desactivada" else "activada"
                    _uiState.update { it.copy(cargando = false, mensajeExito = "Categoría $accion correctamente.") }
                    cargarTodo()
                }
                else -> _uiState.update { it.copy(cargando = false, errorGeneral = "No se pudo cambiar el estado de la categoría.") }
            }
        }
    }

    // Productos
    fun abrirCrearProducto() {
        _uiState.update { it.copy(mostrarDialogoCrearProducto = true, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun cerrarCrearProducto() {
        _uiState.update { it.copy(mostrarDialogoCrearProducto = false, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun abrirEditarProducto(producto: Producto) {
        _uiState.update { it.copy(productoAEditar = producto, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun cerrarEditarProducto() {
        _uiState.update { it.copy(productoAEditar = null, errorFormulario = null, erroresCampos = emptyMap()) }
    }

    fun guardarProducto(
        nombre: String,
        descripcion: String?,
        precio: Double,
        categoriaId: Long,
        imagenUrl: String?,
        existenciasIniciales: Int?,
        umbralMinimo: Int?,
    ) {
        if (nombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El nombre del producto es obligatorio.") }
            return
        }
        if (precio <= 0) {
            _uiState.update { it.copy(errorFormulario = "El precio debe ser mayor a 0.") }
            return
        }
        if (categoriaId <= 0) {
            _uiState.update { it.copy(errorFormulario = "Debes seleccionar una categoría válida.") }
            return
        }

        val req = ProductoCrearRequest(
            nombre = nombre.trim(),
            descripcion = descripcion?.trim()?.ifEmpty { null },
            precio = precio,
            categoriaId = categoriaId,
            imagenUrl = imagenUrl?.trim()?.ifEmpty { null },
            existenciasIniciales = existenciasIniciales,
            umbralMinimo = umbralMinimo,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorFormulario = null, erroresCampos = emptyMap()) }
            when (val res = repository.crearProducto(req)) {
                is ResultadoCatalogo.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            mostrarDialogoCrearProducto = false,
                            mensajeExito = "Producto creado correctamente.",
                        )
                    }
                    cargarTodo()
                }
                is ResultadoCatalogo.Conflicto -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Conflicto al crear producto.", erroresCampos = res.campos)
                    }
                }
                is ResultadoCatalogo.DatosInvalidos -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Datos de producto inválidos.", erroresCampos = res.campos)
                    }
                }
                else -> {
                    _uiState.update { it.copy(guardando = false, errorFormulario = "Error al crear producto.") }
                }
            }
        }
    }

    fun actualizarProducto(
        productoId: Long,
        nombre: String,
        descripcion: String?,
        precio: Double,
        categoriaId: Long,
        imagenUrl: String?,
        umbralMinimo: Int?,
    ) {
        if (nombre.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "El nombre del producto es obligatorio.") }
            return
        }
        if (precio <= 0) {
            _uiState.update { it.copy(errorFormulario = "El precio debe ser mayor a 0.") }
            return
        }

        val req = ProductoEditarRequest(
            nombre = nombre.trim(),
            descripcion = descripcion?.trim()?.ifEmpty { null },
            precio = precio,
            categoriaId = categoriaId,
            imagenUrl = imagenUrl?.trim()?.ifEmpty { null },
            umbralMinimo = umbralMinimo,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorFormulario = null, erroresCampos = emptyMap()) }
            when (val res = repository.editarProducto(productoId, req)) {
                is ResultadoCatalogo.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            productoAEditar = null,
                            mensajeExito = "Producto actualizado correctamente.",
                        )
                    }
                    cargarTodo()
                }
                is ResultadoCatalogo.Conflicto -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Conflicto al actualizar producto.", erroresCampos = res.campos)
                    }
                }
                is ResultadoCatalogo.DatosInvalidos -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Datos inválidos.", erroresCampos = res.campos)
                    }
                }
                else -> {
                    _uiState.update { it.copy(guardando = false, errorFormulario = "Error al editar producto.") }
                }
            }
        }
    }

    fun toggleEstadoProducto(producto: Producto) {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true) }
            val res = if (producto.activo) {
                repository.desactivarProducto(producto.id)
            } else {
                repository.activarProducto(producto.id)
            }
            when (res) {
                is ResultadoCatalogo.Exito -> {
                    val accion = if (producto.activo) "desactivado" else "activado"
                    _uiState.update { it.copy(cargando = false, mensajeExito = "Producto $accion correctamente.") }
                    cargarTodo()
                }
                else -> _uiState.update { it.copy(cargando = false, errorGeneral = "No se pudo cambiar el estado del producto.") }
            }
        }
    }

    fun limpiarMensajes() {
        _uiState.update { it.copy(mensajeExito = null, errorGeneral = null) }
    }

    companion object {
        fun factory(repository: CatalogoRepository, esAdministrador: Boolean = true): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogoViewModel(repository, esAdministrador) as T
            }
    }
}
