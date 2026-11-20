package com.appresso.app.ui.catalogo

import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ProductoCrearRequest
import com.appresso.app.data.catalogo.ProductoEditarRequest
import com.appresso.app.data.catalogo.ResultadoCatalogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogoViewModelTest {

    private val catBebidas = Categoria(id = 1, nombre = "Bebidas", activa = true, totalProductos = 1)
    private val prodLatte = Producto(
        id = 10,
        categoriaId = 1,
        categoriaNombre = "Bebidas",
        categoriaActiva = true,
        nombre = "Café Latte",
        precio = 6500.0,
        stockActual = 20,
        umbralMinimo = 5,
        activo = true,
    )

    private val repo = RepositorioCatalogoFalso()
    private lateinit var viewModel: CatalogoViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.respuestaCategorias = ResultadoCatalogo.Exito(listOf(catBebidas))
        repo.respuestaProductos = ResultadoCatalogo.Exito(listOf(prodLatte))
        viewModel = CatalogoViewModel(repo, esAdministrador = true)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    @Test
    fun `al iniciar carga categorias y productos`() {
        val state = viewModel.uiState.value
        assertFalse(state.cargando)
        assertEquals(1, state.categorias.size)
        assertEquals("Bebidas", state.categorias[0].nombre)
        assertEquals(1, state.productos.size)
        assertEquals("Café Latte", state.productos[0].nombre)
    }

    @Test
    fun `guardarCategoria rechaza si el nombre esta vacio o excede 80 caracteres`() {
        viewModel.guardarCategoria("")
        assertNotNull(viewModel.uiState.value.errorFormulario)
        assertEquals("El nombre de la categoría es obligatorio.", viewModel.uiState.value.errorFormulario)

        val nombreLargo = "A".repeat(81)
        viewModel.guardarCategoria(nombreLargo)
        assertNotNull(viewModel.uiState.value.errorFormulario)
        assertEquals("El nombre no puede exceder los 80 caracteres.", viewModel.uiState.value.errorFormulario)
        assertNull(repo.ultimaCategoriaCreada)
    }

    @Test
    fun `guardarCategoria exitoso llama al repositorio y recarga el catalogo`() {
        val nuevaCat = Categoria(id = 2, nombre = "Postres", activa = true)
        repo.respuestaCrearCategoria = ResultadoCatalogo.Exito(nuevaCat)

        viewModel.guardarCategoria("Postres")

        val state = viewModel.uiState.value
        assertNull(state.errorFormulario)
        assertFalse(state.mostrarDialogoCrearCategoria)
        assertEquals("Categoría creada correctamente.", state.mensajeExito)
        assertEquals("Postres", repo.ultimaCategoriaCreada)
    }

    @Test
    fun `renombrarCategoria modifica la categoria exitosamente`() {
        val catRenombrada = catBebidas.copy(nombre = "Bebidas Frías")
        repo.respuestaRenombrarCategoria = ResultadoCatalogo.Exito(catRenombrada)

        viewModel.renombrarCategoria(1, "Bebidas Frías")

        val state = viewModel.uiState.value
        assertNull(state.errorFormulario)
        assertNull(state.categoriaAEditar)
        assertEquals("Categoría renombrada correctamente.", state.mensajeExito)
        assertEquals(1L, repo.ultimoCategoriaRenombradaId)
        assertEquals("Bebidas Frías", repo.ultimoNombreRenombrado)
    }

    @Test
    fun `toggleEstadoCategoria desactiva categoria activa`() {
        repo.respuestaDesactivarCategoria = ResultadoCatalogo.Exito(Unit)

        viewModel.toggleEstadoCategoria(catBebidas)

        val state = viewModel.uiState.value
        assertEquals("Categoría desactivada correctamente.", state.mensajeExito)
        assertEquals(1L, repo.ultimoCategoriaDesactivadaId)
    }

    @Test
    fun `guardarProducto rechaza si falta nombre o precio es invalido`() {
        viewModel.guardarProducto(
            nombre = "",
            descripcion = null,
            precio = 5000.0,
            categoriaId = 1,
            imagenUrl = null,
            existenciasIniciales = 10,
            umbralMinimo = 2,
        )
        assertEquals("El nombre del producto es obligatorio.", viewModel.uiState.value.errorFormulario)

        viewModel.guardarProducto(
            nombre = "Cappuccino",
            descripcion = null,
            precio = 0.0,
            categoriaId = 1,
            imagenUrl = null,
            existenciasIniciales = 10,
            umbralMinimo = 2,
        )
        assertEquals("El precio debe ser mayor a 0.", viewModel.uiState.value.errorFormulario)

        assertNull(repo.ultimoProductoCreado)
    }

    @Test
    fun `guardarProducto exitoso llama al repositorio`() {
        repo.respuestaCrearProducto = ResultadoCatalogo.Exito(prodLatte)

        viewModel.guardarProducto(
            nombre = "Café Latte",
            descripcion = "Expreso con leche",
            precio = 6500.0,
            categoriaId = 1,
            imagenUrl = null,
            existenciasIniciales = 20,
            umbralMinimo = 5,
        )

        val state = viewModel.uiState.value
        assertNull(state.errorFormulario)
        assertFalse(state.mostrarDialogoCrearProducto)
        assertEquals("Producto creado correctamente.", state.mensajeExito)
        assertNotNull(repo.ultimoProductoCreado)
        assertEquals("Café Latte", repo.ultimoProductoCreado!!.nombre)
    }

    @Test
    fun `actualizarProducto modifica el producto existente`() {
        repo.respuestaEditarProducto = ResultadoCatalogo.Exito(prodLatte.copy(precio = 7000.0))

        viewModel.actualizarProducto(
            productoId = 10,
            nombre = "Café Latte",
            descripcion = "Expreso con leche",
            precio = 7000.0,
            categoriaId = 1,
            imagenUrl = null,
            umbralMinimo = 5,
        )

        val state = viewModel.uiState.value
        assertNull(state.errorFormulario)
        assertNull(state.productoAEditar)
        assertEquals("Producto actualizado correctamente.", state.mensajeExito)
        assertEquals(10L, repo.ultimoProductoEditadoId)
        assertEquals(7000.0, repo.ultimoProductoEditado!!.precio, 0.01)
    }

    @Test
    fun `cambiar pestaña actualiza pestañaSeleccionada en uiState`() {
        viewModel.cambiarPestaña(PestañaCatalogo.CATEGORIAS)
        assertEquals(PestañaCatalogo.CATEGORIAS, viewModel.uiState.value.pestañaSeleccionada)
    }

    private class RepositorioCatalogoFalso : CatalogoRepository {
        var respuestaCategorias: ResultadoCatalogo<List<Categoria>> = ResultadoCatalogo.Exito(emptyList())
        var respuestaProductos: ResultadoCatalogo<List<Producto>> = ResultadoCatalogo.Exito(emptyList())

        var respuestaCrearCategoria: ResultadoCatalogo<Categoria>? = null
        var respuestaRenombrarCategoria: ResultadoCatalogo<Categoria>? = null
        var respuestaDesactivarCategoria: ResultadoCatalogo<Unit> = ResultadoCatalogo.Exito(Unit)

        var respuestaCrearProducto: ResultadoCatalogo<Producto>? = null
        var respuestaEditarProducto: ResultadoCatalogo<Producto>? = null
        var respuestaDesactivarProducto: ResultadoCatalogo<Unit> = ResultadoCatalogo.Exito(Unit)

        var ultimaCategoriaCreada: String? = null
        var ultimoCategoriaRenombradaId: Long? = null
        var ultimoNombreRenombrado: String? = null
        var ultimoCategoriaDesactivadaId: Long? = null

        var ultimoProductoCreado: ProductoCrearRequest? = null
        var ultimoProductoEditadoId: Long? = null
        var ultimoProductoEditado: ProductoEditarRequest? = null
        var ultimoProductoDesactivadoId: Long? = null

        override suspend fun listarCategorias(todas: Boolean): ResultadoCatalogo<List<Categoria>> = respuestaCategorias

        override suspend fun obtenerCategoria(id: Long): ResultadoCatalogo<Categoria> = ResultadoCatalogo.ErrorDelServidor

        override suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria> {
            ultimaCategoriaCreada = nombre
            return respuestaCrearCategoria ?: ResultadoCatalogo.ErrorDelServidor
        }

        override suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria> {
            ultimoCategoriaRenombradaId = id
            ultimoNombreRenombrado = nombre
            return respuestaRenombrarCategoria ?: ResultadoCatalogo.ErrorDelServidor
        }

        override suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Unit> {
            ultimoCategoriaDesactivadaId = id
            return respuestaDesactivarCategoria
        }

        override suspend fun activarCategoria(id: Long): ResultadoCatalogo<Unit> = ResultadoCatalogo.Exito(Unit)

        override suspend fun listarProductos(categoriaId: Long?, todos: Boolean): ResultadoCatalogo<List<Producto>> = respuestaProductos

        override suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto> = ResultadoCatalogo.ErrorDelServidor

        override suspend fun crearProducto(solicitud: ProductoCrearRequest): ResultadoCatalogo<Producto> {
            ultimoProductoCreado = solicitud
            return respuestaCrearProducto ?: ResultadoCatalogo.ErrorDelServidor
        }

        override suspend fun editarProducto(id: Long, solicitud: ProductoEditarRequest): ResultadoCatalogo<Producto> {
            ultimoProductoEditadoId = id
            ultimoProductoEditado = solicitud
            return respuestaEditarProducto ?: ResultadoCatalogo.ErrorDelServidor
        }

        override suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Unit> {
            ultimoProductoDesactivadoId = id
            return respuestaDesactivarProducto
        }

        override suspend fun activarProducto(id: Long): ResultadoCatalogo<Unit> = ResultadoCatalogo.Exito(Unit)
    }
}
