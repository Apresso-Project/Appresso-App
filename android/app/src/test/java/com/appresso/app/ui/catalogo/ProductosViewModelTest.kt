package com.appresso.app.ui.catalogo

import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.DatosProducto
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ResultadoCatalogo
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
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
class ProductosViewModelTest {

    private val bebidas = Categoria(1, "Bebidas", true, 2)
    private val panes = Categoria(2, "Panes", true, 1)

    private fun producto(
        id: Long,
        nombre: String,
        categoria: Categoria = bebidas,
        stock: Int = 20,
        umbral: Int = 5,
        activo: Boolean = true,
        categoriaActiva: Boolean = true,
        imagen: String? = null,
    ) = Producto(
        id = id,
        categoriaId = categoria.id,
        categoriaNombre = categoria.nombre,
        categoriaActiva = categoriaActiva,
        nombre = nombre,
        descripcion = null,
        precio = BigDecimal("6500.00"),
        imagenUrl = imagen,
        stockActual = stock,
        umbralMinimo = umbral,
        activo = activo,
    )

    private val latte = producto(1, "Latte")
    private val mocha = producto(2, "mocha", stock = 3)
    private val croissant = producto(3, "Croissant", panes, stock = 0, activo = false)

    private val repo = RepositorioFalso()
    private lateinit var vm: ProductosViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.productos = ResultadoCatalogo.Exito(listOf(mocha, croissant, latte))
        repo.categorias = ResultadoCatalogo.Exito(listOf(panes, bebidas))
        vm = ProductosViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    private fun llenarFormularioValido() {
        vm.onNombreChange("Capuchino")
        vm.onPrecioChange("7500")
        vm.onCategoriaChange(1)
    }

    // ------------------------------------------------------------ lista

    @Test
    fun `al abrir pide todos los productos incluidos los fuera del menu y las categorias activas`() {
        assertEquals(listOf("Croissant", "Latte", "mocha"), estado.productos.map { it.nombre })
        assertEquals(listOf("Bebidas", "Panes"), estado.categorias.map { it.nombre })
        assertEquals(listOf(true), repo.pidioFueraDelMenu)
        assertEquals(listOf(false), repo.pidioInactivas)
        assertFalse(estado.cargando)
    }

    @Test
    fun `si falla la carga muestra el error y reintentar lo recupera`() {
        repo.productos = ResultadoCatalogo.ErrorDelServidor
        vm.cargar()
        assertEquals(ErrorProductos.ErrorDelServidor, estado.errorLista)

        repo.productos = ResultadoCatalogo.Exito(listOf(latte))
        vm.cargar()
        assertNull(estado.errorLista)
        assertEquals(listOf(latte), estado.productos)
    }

    @Test
    fun `si fallan las categorias tambien se avisa`() {
        repo.categorias = ResultadoCatalogo.SinConexion
        vm.cargar()
        assertEquals(ErrorProductos.SinConexion, estado.errorLista)
    }

    @Test
    fun `filtrar por categoria deja solo sus productos y null muestra todos`() {
        vm.filtrarPorCategoria(2)
        assertEquals(listOf("Croissant"), estado.visibles.map { it.nombre })
        vm.filtrarPorCategoria(null)
        assertEquals(3, estado.visibles.size)
    }

    @Test
    fun `si la categoria filtrada ya no tiene productos al recargar vuelve a todas`() {
        vm.filtrarPorCategoria(2)
        repo.productos = ResultadoCatalogo.Exito(listOf(latte))
        vm.cargar()
        assertNull(estado.filtroCategoriaId)
    }

    // ------------------------------------------------------------ existencias (RNF-17)

    @Test
    fun `el estado de existencias distingue agotado pocas unidades y disponible`() {
        assertEquals(EstadoExistencias.AGOTADO, producto(1, "A", stock = 0).estadoExistencias())
        assertEquals(EstadoExistencias.POCAS_UNIDADES, producto(1, "A", stock = 1, umbral = 5).estadoExistencias())
        assertEquals(EstadoExistencias.POCAS_UNIDADES, producto(1, "A", stock = 5, umbral = 5).estadoExistencias())
        assertEquals(EstadoExistencias.DISPONIBLE, producto(1, "A", stock = 6, umbral = 5).estadoExistencias())
    }

    // ------------------------------------------------------------ crear

    @Test
    fun `crear un producto envia los datos limpios y lo agrega a la lista`() {
        repo.creado = ResultadoCatalogo.Exito(producto(9, "Capuchino"))
        vm.nuevoProducto()
        vm.onNombreChange("  Capuchino ")
        vm.onDescripcionChange("   ")
        vm.onPrecioChange("7500,50")
        vm.onCategoriaChange(1)
        vm.onImagenChange(" https://img.test/c.png ")
        vm.onExistenciasChange("12")
        vm.onUmbralChange("")
        vm.guardar()

        val enviado = repo.datosEnviados.single()
        assertEquals("Capuchino", enviado.nombre)
        assertEquals(BigDecimal("7500.50"), enviado.precio)
        assertEquals(1L, enviado.categoriaId)
        assertNull(enviado.descripcion)
        assertEquals("https://img.test/c.png", enviado.imagenUrl)
        assertEquals(12, enviado.existenciasIniciales)
        assertNull(enviado.umbralMinimo)
        assertNull(estado.formulario)
        assertTrue(estado.productos.any { it.id == 9L })
    }

    @Test
    fun `con una sola categoria activa el formulario nuevo la deja elegida`() {
        repo.categorias = ResultadoCatalogo.Exito(listOf(bebidas))
        vm.cargar()
        vm.nuevoProducto()
        assertEquals(1L, estado.formulario?.categoriaId)
    }

    @Test
    fun `un formulario vacio marca nombre precio y categoria sin llamar al servidor`() {
        vm.nuevoProducto()
        vm.guardar()

        val errores = estado.formulario!!.errores
        assertEquals(ErrorCampo.Obligatorio, errores[CampoProducto.NOMBRE])
        assertEquals(ErrorCampo.Obligatorio, errores[CampoProducto.PRECIO])
        assertEquals(ErrorCampo.Obligatorio, errores[CampoProducto.CATEGORIA])
        assertTrue(repo.datosEnviados.isEmpty())
    }

    @Test
    fun `precios invalidos se rechazan en el telefono`() {
        listOf("abc", "0", "0.00", "-5", "6.500,00", "12.345", "1e3").forEach { texto ->
            vm.nuevoProducto()
            llenarFormularioValido()
            vm.onPrecioChange(texto)
            vm.guardar()
            assertEquals("precio '$texto'", ErrorCampo.PrecioInvalido, estado.formulario!!.errores[CampoProducto.PRECIO])
            vm.cerrarFormulario()
        }
        assertTrue(repo.datosEnviados.isEmpty())
    }

    @Test
    fun `precios validos se aceptan con punto o coma`() {
        listOf("6500" to "6500", "6500.5" to "6500.5", "0,50" to "0.50").forEach { (texto, esperado) ->
            repo.creado = ResultadoCatalogo.Exito(producto(9, "Capuchino"))
            vm.nuevoProducto()
            llenarFormularioValido()
            vm.onPrecioChange(texto)
            vm.guardar()
            assertEquals(BigDecimal(esperado), repo.datosEnviados.last().precio)
        }
    }

    @Test
    fun `los limites de longitud se validan`() {
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.onNombreChange("a".repeat(121))
        vm.onDescripcionChange("b".repeat(256))
        vm.onImagenChange("https://x.test/" + "c".repeat(500))
        vm.guardar()

        val errores = estado.formulario!!.errores
        assertEquals(ErrorCampo.DemasiadoLargo(120), errores[CampoProducto.NOMBRE])
        assertEquals(ErrorCampo.DemasiadoLargo(255), errores[CampoProducto.DESCRIPCION])
        assertEquals(ErrorCampo.DemasiadoLargo(500), errores[CampoProducto.IMAGEN])
        assertTrue(repo.datosEnviados.isEmpty())
    }

    @Test
    fun `la imagen debe ser un enlace http o https`() {
        listOf("foto.png", "ftp://x.test/a.png", "https://", "https://x.test/a b.png").forEach { texto ->
            vm.nuevoProducto()
            llenarFormularioValido()
            vm.onImagenChange(texto)
            vm.guardar()
            assertEquals("imagen '$texto'", ErrorCampo.UrlInvalida, estado.formulario!!.errores[CampoProducto.IMAGEN])
            vm.cerrarFormulario()
        }
    }

    @Test
    fun `existencias y umbral aceptan de 0 a un millon y nada mas`() {
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.onExistenciasChange("1000001")
        vm.onUmbralChange("-1")
        vm.guardar()
        assertEquals(ErrorCampo.NumeroInvalido(1_000_000), estado.formulario!!.errores[CampoProducto.EXISTENCIAS])
        assertEquals(ErrorCampo.NumeroInvalido(1_000_000), estado.formulario!!.errores[CampoProducto.UMBRAL])

        repo.creado = ResultadoCatalogo.Exito(producto(9, "Capuchino"))
        vm.onExistenciasChange("0")
        vm.onUmbralChange("1000000")
        vm.guardar()
        assertEquals(0, repo.datosEnviados.single().existenciasIniciales)
        assertEquals(1_000_000, repo.datosEnviados.single().umbralMinimo)
    }

    @Test
    fun `escribir en un campo limpia solo su error`() {
        vm.nuevoProducto()
        vm.guardar()
        vm.onNombreChange("Latte")
        val errores = estado.formulario!!.errores
        assertNull(errores[CampoProducto.NOMBRE])
        assertNotNull(errores[CampoProducto.PRECIO])
    }

    @Test
    fun `los errores por campo del servidor salen junto a su campo y conservan lo escrito`() {
        repo.creado = ResultadoCatalogo.Conflicto("Revisa", mapOf("categoriaId" to "La categoría está desactivada"))
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.guardar()

        val f = estado.formulario!!
        assertEquals(ErrorCampo.Servidor("La categoría está desactivada"), f.errores[CampoProducto.CATEGORIA])
        assertNull(f.error)
        assertEquals("Capuchino", f.nombre)
        assertFalse(f.guardando)
    }

    @Test
    fun `un error 400 con varios campos los muestra todos`() {
        repo.creado = ResultadoCatalogo.DatosInvalidos("Revisa", mapOf("precio" to "Mayor que cero", "imagenUrl" to "URL no válida"))
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.guardar()
        assertEquals(setOf(CampoProducto.PRECIO, CampoProducto.IMAGEN), estado.formulario!!.errores.keys)
    }

    @Test
    fun `un 403 al guardar no repite el aviso que ya mostro el cliente HTTP`() {
        repo.creado = ResultadoCatalogo.SinPermiso("No puedes")
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.guardar()
        assertNull(estado.formulario!!.error)
        assertFalse(estado.formulario!!.guardando)
    }

    @Test
    fun `un error del servidor al guardar queda en el formulario`() {
        repo.creado = ResultadoCatalogo.ErrorDelServidor
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.guardar()
        assertEquals(ErrorProductos.ErrorDelServidor, estado.formulario!!.error)
    }

    @Test
    fun `no se guarda dos veces ni se cierra el formulario mientras hay una peticion en curso`() {
        val espera = CompletableDeferred<ResultadoCatalogo<Producto>>()
        repo.creadoEnEspera = espera
        vm.nuevoProducto()
        llenarFormularioValido()
        vm.guardar()
        vm.guardar()
        assertEquals(1, repo.datosEnviados.size)
        assertTrue(estado.formulario!!.guardando)

        vm.cerrarFormulario()
        assertNotNull(estado.formulario)

        espera.complete(ResultadoCatalogo.Exito(producto(9, "Capuchino")))
        assertNull(estado.formulario)
    }

    // ------------------------------------------------------------ editar

    @Test
    fun `editar abre el formulario con los datos actuales y sin existencias`() {
        vm.editarProducto(producto(1, "Latte", imagen = "https://img.test/l.png"))
        val f = estado.formulario!!
        assertEquals("Latte", f.nombre)
        assertEquals("6500", f.precio)
        assertEquals(1L, f.categoriaId)
        assertEquals("https://img.test/l.png", f.imagenUrl)
        assertEquals("5", f.umbral)
        assertEquals("", f.existencias)
    }

    @Test
    fun `al editar no se envian existencias aunque el formulario las tuviera`() {
        repo.editado = ResultadoCatalogo.Exito(latte.copy(nombre = "Latte grande", precio = BigDecimal("7000")))
        vm.editarProducto(latte)
        vm.onNombreChange("Latte grande")
        vm.onPrecioChange("7000")
        vm.onExistenciasChange("999")
        vm.guardar()

        assertEquals(listOf(1L), repo.idsEditados)
        assertNull(repo.datosEnviados.single().existenciasIniciales)
        assertEquals("Latte grande", estado.productos.first { it.id == 1L }.nombre)
        assertEquals(3, estado.productos.size)
        assertNull(estado.formulario)
    }

    @Test
    fun `editar un producto de una categoria desactivada conserva su categoria en el formulario`() {
        val huerfano = producto(5, "Torta", categoria = Categoria(7, "Dulces", false, 1), categoriaActiva = false)
        vm.editarProducto(huerfano)
        assertEquals(7L, estado.formulario!!.categoriaId)
    }

    @Test
    fun `si el producto ya no existe avisa y recarga la lista`() {
        repo.editado = ResultadoCatalogo.NoEncontrado
        repo.productos = ResultadoCatalogo.Exito(listOf(mocha))
        vm.editarProducto(latte)
        vm.onNombreChange("Otro")
        vm.guardar()

        assertEquals(ErrorProductos.NoEncontrado, estado.formulario!!.error)
        assertEquals(listOf(mocha), estado.productos)
    }

    // ------------------------------------------------------------ desactivar / activar

    @Test
    fun `desactivar pide confirmacion y no llama al servidor hasta confirmar`() {
        vm.pedirDesactivar(latte)
        assertEquals(latte, estado.confirmarDesactivar)
        assertTrue(repo.desactivados.isEmpty())

        vm.cancelarDesactivar()
        assertNull(estado.confirmarDesactivar)
        assertTrue(repo.desactivados.isEmpty())
    }

    @Test
    fun `confirmar desactiva el producto y lo deja en la lista como fuera del menu`() {
        repo.desactivado = ResultadoCatalogo.Exito(latte.copy(activo = false))
        vm.pedirDesactivar(latte)
        vm.confirmarDesactivar()

        assertEquals(listOf(1L), repo.desactivados)
        val actualizado = estado.productos.first { it.id == 1L }
        assertFalse(actualizado.activo)
        assertFalse(actualizado.enElMenu)
        assertNull(estado.procesandoId)
    }

    @Test
    fun `activar no pide confirmacion`() {
        repo.activado = ResultadoCatalogo.Exito(croissant.copy(activo = true))
        vm.activar(croissant)
        assertEquals(listOf(3L), repo.activados)
        assertTrue(estado.productos.first { it.id == 3L }.activo)
    }

    @Test
    fun `un error al desactivar queda como error de accion y se puede descartar`() {
        repo.desactivado = ResultadoCatalogo.ErrorDelServidor
        vm.pedirDesactivar(latte)
        vm.confirmarDesactivar()
        assertEquals(ErrorProductos.ErrorDelServidor, estado.errorAccion)
        vm.descartarError()
        assertNull(estado.errorAccion)
    }

    @Test
    fun `un 403 al desactivar no deja mensaje propio porque el cliente ya lo mostro`() {
        repo.desactivado = ResultadoCatalogo.SinPermiso("No puedes")
        vm.pedirDesactivar(latte)
        vm.confirmarDesactivar()
        assertNull(estado.errorAccion)
        assertTrue(estado.productos.first { it.id == 1L }.activo)
    }

    @Test
    fun `un 401 no muestra ningun error porque la sesion ya se cerro`() {
        repo.productos = ResultadoCatalogo.SesionInvalida
        vm.cargar()
        assertNull(estado.errorLista)
    }

    @Test
    fun `mientras hay una accion en curso no se acepta otra`() {
        val espera = CompletableDeferred<ResultadoCatalogo<Producto>>()
        repo.desactivadoEnEspera = espera
        vm.pedirDesactivar(latte)
        vm.confirmarDesactivar()
        assertEquals(1L, estado.procesandoId)

        vm.pedirDesactivar(mocha)
        assertNull(estado.confirmarDesactivar)
        vm.activar(croissant)
        assertTrue(repo.activados.isEmpty())

        espera.complete(ResultadoCatalogo.Exito(latte.copy(activo = false)))
        assertNull(estado.procesandoId)
    }

    // ------------------------------------------------------------ tiempo real

    @Test
    fun `un aviso de cambios recarga la lista sin mostrar el indicador de carga`() {
        val avisos = MutableSharedFlow<Unit>()
        val conAvisos = ProductosViewModel(repo, avisos)
        repo.productos = ResultadoCatalogo.Exito(listOf(latte, producto(8, "Té")))

        runBlocking { avisos.emit(Unit) }

        assertEquals(listOf("Latte", "Té"), conAvisos.estado.value.productos.map { it.nombre })
        assertFalse(conAvisos.estado.value.cargando)
    }

    @Test
    fun `si la recarga por aviso falla se conserva la lista y no se muestra error`() {
        val avisos = MutableSharedFlow<Unit>()
        val conAvisos = ProductosViewModel(repo, avisos)
        repo.productos = ResultadoCatalogo.SinConexion

        runBlocking { avisos.emit(Unit) }

        assertEquals(3, conAvisos.estado.value.productos.size)
        assertNull(conAvisos.estado.value.errorLista)
    }

    // ------------------------------------------------------------ doble

    private class RepositorioFalso : CatalogoRepository {
        var productos: ResultadoCatalogo<List<Producto>> = ResultadoCatalogo.Exito(emptyList())
        var categorias: ResultadoCatalogo<List<Categoria>> = ResultadoCatalogo.Exito(emptyList())
        var creado: ResultadoCatalogo<Producto> = ResultadoCatalogo.ErrorDelServidor
        var creadoEnEspera: CompletableDeferred<ResultadoCatalogo<Producto>>? = null
        var editado: ResultadoCatalogo<Producto> = ResultadoCatalogo.ErrorDelServidor
        var desactivado: ResultadoCatalogo<Producto> = ResultadoCatalogo.ErrorDelServidor
        var desactivadoEnEspera: CompletableDeferred<ResultadoCatalogo<Producto>>? = null
        var activado: ResultadoCatalogo<Producto> = ResultadoCatalogo.ErrorDelServidor

        val pidioFueraDelMenu = mutableListOf<Boolean>()
        val pidioInactivas = mutableListOf<Boolean>()
        val datosEnviados = mutableListOf<DatosProducto>()
        val idsEditados = mutableListOf<Long>()
        val desactivados = mutableListOf<Long>()
        val activados = mutableListOf<Long>()

        override suspend fun listarProductos(categoriaId: Long?, incluirFueraDelMenu: Boolean): ResultadoCatalogo<List<Producto>> {
            pidioFueraDelMenu += incluirFueraDelMenu
            return productos
        }

        override suspend fun listarCategorias(incluirInactivas: Boolean): ResultadoCatalogo<List<Categoria>> {
            pidioInactivas += incluirInactivas
            return categorias
        }

        override suspend fun crearProducto(datos: DatosProducto): ResultadoCatalogo<Producto> {
            datosEnviados += datos
            return creadoEnEspera?.await() ?: creado
        }

        override suspend fun editarProducto(id: Long, datos: DatosProducto): ResultadoCatalogo<Producto> {
            idsEditados += id
            datosEnviados += datos
            return editado
        }

        override suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Producto> {
            desactivados += id
            return desactivadoEnEspera?.await() ?: desactivado
        }

        override suspend fun activarProducto(id: Long): ResultadoCatalogo<Producto> {
            activados += id
            return activado
        }

        override suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto> = error("no se usa")
        override suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria> = error("no se usa")
        override suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria> = error("no se usa")
        override suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Categoria> = error("no se usa")
        override suspend fun activarCategoria(id: Long): ResultadoCatalogo<Categoria> = error("no se usa")
    }
}
