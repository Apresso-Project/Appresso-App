package com.appresso.app.data.catalogo

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.ClienteHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogoRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: CatalogoRepository

    private val catBebidas = Categoria(id = 1, nombre = "Bebidas", activa = true, totalProductos = 2)
    private val catJson = """{"id":1,"nombre":"Bebidas","activa":true,"totalProductos":2}"""

    private val prodLatte = Producto(
        id = 10,
        categoriaId = 1,
        categoriaNombre = "Bebidas",
        categoriaActiva = true,
        nombre = "Café Latte",
        descripcion = "Café expreso con leche vaporizada",
        precio = 6500.0,
        imagenUrl = "https://ejemplo.com/latte.png",
        stockActual = 20,
        umbralMinimo = 5,
        activo = true,
    )
    private val prodJson = """
        {
          "id": 10,
          "categoriaId": 1,
          "categoriaNombre": "Bebidas",
          "categoriaActiva": true,
          "nombre": "Café Latte",
          "descripcion": "Café expreso con leche vaporizada",
          "precio": 6500.0,
          "imagenUrl": "https://ejemplo.com/latte.png",
          "stockActual": 20,
          "umbralMinimo": 5,
          "activo": true
        }
    """.trimIndent()

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.ADMINISTRADOR, "Admin")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = CatalogoRepositoryRemoto(retrofit.create(CatalogoApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `listarCategorias devuelve lista de categorias exitosamente`() = runTest {
        servidor.enqueue(json(200, "[$catJson]"))

        val resultado = repositorio.listarCategorias(todas = true)

        assertEquals(ResultadoCatalogo.Exito(listOf(catBebidas)), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/categorias?todas=true", pedido.path)
    }

    @Test
    fun `crearCategoria devuelve la categoria creada`() = runTest {
        servidor.enqueue(json(201, catJson))

        val resultado = repositorio.crearCategoria("Bebidas")

        assertEquals(ResultadoCatalogo.Exito(catBebidas), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/categorias", pedido.path)
    }

    @Test
    fun `crearCategoria con nombre duplicado devuelve Conflicto 409`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Ya existe una categoría con ese nombre."}"""))

        val resultado = repositorio.crearCategoria("Bebidas")

        assertTrue(resultado is ResultadoCatalogo.Conflicto)
        assertEquals("Ya existe una categoría con ese nombre.", (resultado as ResultadoCatalogo.Conflicto).mensaje)
    }

    @Test
    fun `renombrarCategoria modifica el nombre correctamente`() = runTest {
        servidor.enqueue(json(200, """{"id":1,"nombre":"Bebidas Calientes","activa":true,"totalProductos":2}"""))

        val resultado = repositorio.renombrarCategoria(1, "Bebidas Calientes")

        assertTrue(resultado is ResultadoCatalogo.Exito)
        assertEquals("Bebidas Calientes", (resultado as ResultadoCatalogo.Exito).datos.nombre)
        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/api/categorias/1", pedido.path)
    }

    @Test
    fun `desactivarCategoria llama endpoint de desactivacion`() = runTest {
        servidor.enqueue(json(200, ""))

        val resultado = repositorio.desactivarCategoria(1)

        assertEquals(ResultadoCatalogo.Exito(Unit), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/categorias/1/desactivar", pedido.path)
    }

    @Test
    fun `listarProductos devuelve productos del catalogo`() = runTest {
        servidor.enqueue(json(200, "[$prodJson]"))

        val resultado = repositorio.listarProductos(todos = true)

        assertEquals(ResultadoCatalogo.Exito(listOf(prodLatte)), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/productos?todos=true", pedido.path)
    }

    @Test
    fun `crearProducto envia la solicitud y retorna el producto creado`() = runTest {
        servidor.enqueue(json(201, prodJson))

        val req = ProductoCrearRequest(
            nombre = "Café Latte",
            descripcion = "Café expreso con leche vaporizada",
            precio = 6500.0,
            categoriaId = 1,
            imagenUrl = "https://ejemplo.com/latte.png",
            existenciasIniciales = 20,
            umbralMinimo = 5,
        )
        val resultado = repositorio.crearProducto(req)

        assertEquals(ResultadoCatalogo.Exito(prodLatte), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/productos", pedido.path)
    }

    @Test
    fun `editarProducto con id inexistente devuelve NoEncontrado 404`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"No se encontró el producto."}"""))

        val req = ProductoEditarRequest(
            nombre = "Café Latte",
            precio = 6500.0,
            categoriaId = 1,
        )
        val resultado = repositorio.editarProducto(999, req)

        assertTrue(resultado is ResultadoCatalogo.NoEncontrado)
        assertEquals("No se encontró el producto.", (resultado as ResultadoCatalogo.NoEncontrado).mensaje)
    }

    @Test
    fun `error de conexion se traduce a SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val resultado = repositorio.listarCategorias()

        assertEquals(ResultadoCatalogo.SinConexion, resultado)
    }
}
