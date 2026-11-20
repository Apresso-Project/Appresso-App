package com.appresso.app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.DatosProducto
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ResultadoCatalogo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Campos del formulario de producto. */
enum class CampoProducto { NOMBRE, DESCRIPCION, PRECIO, CATEGORIA, IMAGEN, EXISTENCIAS, UMBRAL }

/** Problema de un campo; el texto que ve la persona se arma en la pantalla (`textoDe`). */
sealed interface ErrorCampo {
    data object Obligatorio : ErrorCampo
    data object PrecioInvalido : ErrorCampo
    data object UrlInvalida : ErrorCampo
    data class DemasiadoLargo(val maximo: Int) : ErrorCampo
    data class NumeroInvalido(val maximo: Int) : ErrorCampo

    /** Texto que envió el servidor (se muestra tal cual). */
    data class Servidor(val mensaje: String) : ErrorCampo
}

sealed interface ErrorProductos {
    data object SinConexion : ErrorProductos
    data object ErrorDelServidor : ErrorProductos
    data object NoEncontrado : ErrorProductos
    data class SinPermiso(val mensajeServidor: String?) : ErrorProductos
    data class DatosInvalidos(val mensajeServidor: String?) : ErrorProductos
}

/** Estado de las existencias de un producto (RNF-17): se distingue por texto, no solo por color. */
enum class EstadoExistencias { AGOTADO, POCAS_UNIDADES, DISPONIBLE }

fun Producto.estadoExistencias(): EstadoExistencias = when {
    stockActual <= 0 -> EstadoExistencias.AGOTADO
    stockActual <= umbralMinimo -> EstadoExistencias.POCAS_UNIDADES
    else -> EstadoExistencias.DISPONIBLE
}

data class FormularioProductoUiState(
    /** null = crear un producto nuevo; si no, el que se edita. */
    val editando: Producto? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val precio: String = "",
    val categoriaId: Long? = null,
    val imagenUrl: String = "",
    val existencias: String = "",
    val umbral: String = "",
    val errores: Map<CampoProducto, ErrorCampo> = emptyMap(),
    val error: ErrorProductos? = null,
    val guardando: Boolean = false,
)

data class ProductosUiState(
    val productos: List<Producto> = emptyList(),
    /** Categorías activas: las únicas donde se puede crear o mover un producto. */
    val categorias: List<Categoria> = emptyList(),
    /** null = todas las categorías. */
    val filtroCategoriaId: Long? = null,
    val cargando: Boolean = false,
    val errorLista: ErrorProductos? = null,
    val formulario: FormularioProductoUiState? = null,
    val confirmarDesactivar: Producto? = null,
    val procesandoId: Long? = null,
    val errorAccion: ErrorProductos? = null,
) {
    val visibles: List<Producto>
        get() = if (filtroCategoriaId == null) productos else productos.filter { it.categoriaId == filtroCategoriaId }
}

/**
 * Administración de productos (SCRUM-72, solo administrador): lista con los que están fuera del menú
 * incluidos, con imagen y existencias; crear, editar y desactivar/activar. Las existencias solo se
 * fijan al crear: después cambian con ajustes de inventario (RF-07, docs/api-catalogo.md).
 *
 * [cambios] avisa que el catálogo cambió en otro dispositivo; cada aviso recarga en silencio.
 */
class ProductosViewModel(
    private val repositorio: CatalogoRepository,
    cambios: Flow<Unit> = emptyFlow(),
) : ViewModel() {

    private val _estado = MutableStateFlow(ProductosUiState())
    val estado: StateFlow<ProductosUiState> = _estado.asStateFlow()

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
            val productos = repositorio.listarProductos(categoriaId = null, incluirFueraDelMenu = true)
            val categorias = repositorio.listarCategorias(incluirInactivas = false)
            if (productos is ResultadoCatalogo.Exito && categorias is ResultadoCatalogo.Exito) {
                _estado.update {
                    it.copy(
                        cargando = false,
                        productos = ordenar(productos.valor),
                        categorias = categorias.valor.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { c -> c.nombre }),
                        errorLista = null,
                        // Si la categoría filtrada desapareció, se vuelve a «todas».
                        filtroCategoriaId = it.filtroCategoriaId?.takeIf { id -> productos.valor.any { p -> p.categoriaId == id } },
                    )
                }
            } else if (!silencioso) {
                val error = (if (productos !is ResultadoCatalogo.Exito) productos else categorias).aError()
                _estado.update { it.copy(cargando = false, errorLista = error) }
            }
        }
    }

    fun filtrarPorCategoria(id: Long?) = _estado.update { it.copy(filtroCategoriaId = id) }

    fun descartarError() = _estado.update { it.copy(errorAccion = null) }

    // ---------------------------------------------------------------- desactivar / activar

    fun pedirDesactivar(producto: Producto) = _estado.update {
        if (it.procesandoId != null) it else it.copy(confirmarDesactivar = producto, errorAccion = null)
    }

    fun cancelarDesactivar() = _estado.update { it.copy(confirmarDesactivar = null) }

    fun confirmarDesactivar() {
        val producto = _estado.value.confirmarDesactivar ?: return
        _estado.update { it.copy(confirmarDesactivar = null) }
        cambiarEstado(producto.id) { repositorio.desactivarProducto(producto.id) }
    }

    /** Reactivar no pide confirmación: es reversible. */
    fun activar(producto: Producto) = cambiarEstado(producto.id) { repositorio.activarProducto(producto.id) }

    private fun cambiarEstado(id: Long, llamada: suspend () -> ResultadoCatalogo<Producto>) {
        if (_estado.value.procesandoId != null) return
        _estado.update { it.copy(procesandoId = id, errorAccion = null) }
        viewModelScope.launch {
            val r = llamada()
            if (r is ResultadoCatalogo.Exito) {
                _estado.update { it.copy(procesandoId = null, productos = reemplazar(it.productos, r.valor)) }
            } else {
                val error = r.aError(avisaElCliente = true)
                _estado.update { it.copy(procesandoId = null, errorAccion = error) }
                if (error == ErrorProductos.NoEncontrado) cargar(silencioso = true)
            }
        }
    }

    // ---------------------------------------------------------------- formulario

    fun nuevoProducto() = _estado.update {
        it.copy(formulario = FormularioProductoUiState(categoriaId = it.categorias.singleOrNull()?.id))
    }

    fun editarProducto(producto: Producto) = _estado.update {
        it.copy(
            formulario = FormularioProductoUiState(
                editando = producto,
                nombre = producto.nombre,
                descripcion = producto.descripcion.orEmpty(),
                precio = producto.precio.stripTrailingZeros().toPlainString(),
                categoriaId = producto.categoriaId,
                imagenUrl = producto.imagenUrl.orEmpty(),
                umbral = producto.umbralMinimo.toString(),
            ),
        )
    }

    fun cerrarFormulario() = _estado.update {
        if (it.formulario?.guardando == true) it else it.copy(formulario = null)
    }

    fun onNombreChange(v: String) = editar(CampoProducto.NOMBRE) { it.copy(nombre = v) }
    fun onDescripcionChange(v: String) = editar(CampoProducto.DESCRIPCION) { it.copy(descripcion = v) }
    fun onPrecioChange(v: String) = editar(CampoProducto.PRECIO) { it.copy(precio = v) }
    fun onCategoriaChange(id: Long) = editar(CampoProducto.CATEGORIA) { it.copy(categoriaId = id) }
    fun onImagenChange(v: String) = editar(CampoProducto.IMAGEN) { it.copy(imagenUrl = v) }
    fun onExistenciasChange(v: String) = editar(CampoProducto.EXISTENCIAS) { it.copy(existencias = v) }
    fun onUmbralChange(v: String) = editar(CampoProducto.UMBRAL) { it.copy(umbral = v) }

    /** Cambia un campo y le quita su error (y el general) para no dejar avisos viejos. */
    private fun editar(campo: CampoProducto, cambio: (FormularioProductoUiState) -> FormularioProductoUiState) =
        actualizarFormulario { cambio(it).copy(errores = it.errores - campo, error = null) }

    fun guardar() {
        val formulario = _estado.value.formulario ?: return
        if (formulario.guardando) return
        val editando = formulario.editando

        val resultado = validar(formulario)
        if (resultado.errores.isNotEmpty() || resultado.datos == null) {
            actualizarFormulario { it.copy(errores = resultado.errores, error = null) }
            return
        }
        val datos = resultado.datos

        actualizarFormulario { it.copy(guardando = true, errores = emptyMap(), error = null) }
        viewModelScope.launch {
            val r = if (editando == null) repositorio.crearProducto(datos) else repositorio.editarProducto(editando.id, datos)
            when (r) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(formulario = null, productos = reemplazar(it.productos, r.valor))
                }

                else -> {
                    val delServidor = when (r) {
                        is ResultadoCatalogo.DatosInvalidos -> r.campos
                        is ResultadoCatalogo.Conflicto -> r.campos
                        else -> emptyMap()
                    }
                    val errores = delServidor.mapNotNull { (clave, texto) ->
                        campoDelServidor(clave)?.let { it to ErrorCampo.Servidor(texto) }
                    }.toMap()
                    // Si el servidor señaló algún campo, el aviso general sobra.
                    val general = if (errores.isNotEmpty()) null else r.aError(avisaElCliente = true)
                    actualizarFormulario { it.copy(guardando = false, errores = errores, error = general) }
                    if (general == ErrorProductos.NoEncontrado) cargar(silencioso = true)
                }
            }
        }
    }

    private fun actualizarFormulario(cambio: (FormularioProductoUiState) -> FormularioProductoUiState) =
        _estado.update { estado -> estado.copy(formulario = estado.formulario?.let(cambio)) }

    /** Ver el comentario de `CategoriasViewModel.aError`: 401 no muestra nada; con [avisaElCliente], 403 y sin conexión tampoco. */
    private fun ResultadoCatalogo<*>.aError(avisaElCliente: Boolean = false): ErrorProductos? = when (this) {
        is ResultadoCatalogo.Exito -> null
        is ResultadoCatalogo.DatosInvalidos -> ErrorProductos.DatosInvalidos(mensaje)
        is ResultadoCatalogo.Conflicto -> ErrorProductos.DatosInvalidos(mensaje)
        ResultadoCatalogo.SesionInvalida -> null
        is ResultadoCatalogo.SinPermiso -> if (avisaElCliente) null else ErrorProductos.SinPermiso(mensaje)
        ResultadoCatalogo.NoEncontrado -> ErrorProductos.NoEncontrado
        ResultadoCatalogo.SinConexion -> if (avisaElCliente) null else ErrorProductos.SinConexion
        ResultadoCatalogo.ErrorDelServidor -> ErrorProductos.ErrorDelServidor
    }

    /** Resultado de validar el formulario: los datos listos para enviar o los errores por campo. */
    internal class Validacion(val datos: DatosProducto?, val errores: Map<CampoProducto, ErrorCampo>)

    companion object {
        const val MAX_NOMBRE = 120
        const val MAX_DESCRIPCION = 255
        const val MAX_IMAGEN = 500
        const val MAX_CANTIDAD = 1_000_000

        private val PRECIO = Regex("""\d{1,10}([.,]\d{1,2})?""")

        /** Mismas reglas que el backend (docs/api-catalogo.md); el servidor las vuelve a validar. */
        internal fun validar(f: FormularioProductoUiState): Validacion {
            val errores = linkedMapOf<CampoProducto, ErrorCampo>()
            val nombre = f.nombre.trim()
            val descripcion = f.descripcion.trim()
            val imagen = f.imagenUrl.trim()

            when {
                nombre.isEmpty() -> errores[CampoProducto.NOMBRE] = ErrorCampo.Obligatorio
                nombre.length > MAX_NOMBRE -> errores[CampoProducto.NOMBRE] = ErrorCampo.DemasiadoLargo(MAX_NOMBRE)
            }
            if (descripcion.length > MAX_DESCRIPCION) {
                errores[CampoProducto.DESCRIPCION] = ErrorCampo.DemasiadoLargo(MAX_DESCRIPCION)
            }

            val textoPrecio = f.precio.trim()
            val precio: BigDecimal? = when {
                textoPrecio.isEmpty() -> null.also { errores[CampoProducto.PRECIO] = ErrorCampo.Obligatorio }
                !PRECIO.matches(textoPrecio) -> null.also { errores[CampoProducto.PRECIO] = ErrorCampo.PrecioInvalido }
                else -> BigDecimal(textoPrecio.replace(',', '.')).also {
                    if (it.signum() <= 0) errores[CampoProducto.PRECIO] = ErrorCampo.PrecioInvalido
                }
            }

            if (f.categoriaId == null) errores[CampoProducto.CATEGORIA] = ErrorCampo.Obligatorio

            if (imagen.isNotEmpty()) {
                when {
                    imagen.length > MAX_IMAGEN -> errores[CampoProducto.IMAGEN] = ErrorCampo.DemasiadoLargo(MAX_IMAGEN)
                    !esUrlWeb(imagen) -> errores[CampoProducto.IMAGEN] = ErrorCampo.UrlInvalida
                }
            }

            // Las existencias solo se piden al crear; el umbral vacío es «no indicado».
            val existencias = if (f.editando == null) cantidad(f.existencias, CampoProducto.EXISTENCIAS, errores) else null
            val umbral = cantidad(f.umbral, CampoProducto.UMBRAL, errores)

            if (errores.isNotEmpty() || precio == null) return Validacion(null, errores)
            return Validacion(
                DatosProducto(
                    nombre = nombre,
                    precio = precio,
                    categoriaId = f.categoriaId!!,
                    descripcion = descripcion.ifEmpty { null },
                    imagenUrl = imagen.ifEmpty { null },
                    existenciasIniciales = existencias,
                    umbralMinimo = umbral,
                ),
                errores,
            )
        }

        private fun esUrlWeb(texto: String): Boolean {
            val minuscula = texto.lowercase()
            val resto = when {
                minuscula.startsWith("https://") -> texto.substring(8)
                minuscula.startsWith("http://") -> texto.substring(7)
                else -> return false
            }
            return resto.isNotEmpty() && resto.none { it.isWhitespace() }
        }

        /** Vacío = null (no indicado); si no, un entero entre 0 y [MAX_CANTIDAD]. */
        private fun cantidad(texto: String, campo: CampoProducto, errores: MutableMap<CampoProducto, ErrorCampo>): Int? {
            val limpio = texto.trim()
            if (limpio.isEmpty()) return null
            val valor = limpio.takeIf { s -> s.all { it.isDigit() } }?.toLongOrNull()
            if (valor == null || valor > MAX_CANTIDAD) {
                errores[campo] = ErrorCampo.NumeroInvalido(MAX_CANTIDAD)
                return null
            }
            return valor.toInt()
        }

        private fun campoDelServidor(clave: String): CampoProducto? = when (clave) {
            "nombre" -> CampoProducto.NOMBRE
            "descripcion" -> CampoProducto.DESCRIPCION
            "precio" -> CampoProducto.PRECIO
            "categoriaId" -> CampoProducto.CATEGORIA
            "imagenUrl" -> CampoProducto.IMAGEN
            "existenciasIniciales" -> CampoProducto.EXISTENCIAS
            "umbralMinimo" -> CampoProducto.UMBRAL
            else -> null
        }

        private fun ordenar(lista: List<Producto>) = lista.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })

        private fun reemplazar(lista: List<Producto>, nuevo: Producto) = ordenar(lista.filterNot { it.id == nuevo.id } + nuevo)

        fun factory(repositorio: CatalogoRepository, cambios: Flow<Unit> = emptyFlow()): ViewModelProvider.Factory =
            viewModelFactory { initializer { ProductosViewModel(repositorio, cambios) } }
    }
}
