package com.appresso.app.data.catalogo

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.ClienteHttp
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cada caso del contrato (docs/api-catalogo.md) contra un servidor HTTP simulado. */
class CatalogoRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: CatalogoRepository

    private val bebidas = Categoria(3, "Bebidas", true, 4)
    private val bebidasJson = """{"id":3,"nombre":"Bebidas","activa":true,"totalProductos":4}"""

    private fun productoJson(
        activo: Boolean = true,
        categoriaActiva: Boolean = true,
        descripcion: String? = "Con leche",
        precio: String = "6500.50",
    ) = """{"id":9,"categoriaId":3,"categoriaNombre":"Bebidas","categoriaActiva":$categoriaActiva,""" +
        """"nombre":"Latte","descripcion":${descripcion?.let { "\"$it\"" }},"precio":$precio,""" +
        """"imagenUrl":null,"stockActual":12,"umbralMinimo":4,"activo":$activo}"""

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

    private fun cuerpoDe(pedido: okhttp3.mockwebserver.RecordedRequest) =
        Json.parseToJsonElement(pedido.body.readUtf8()).jsonObject

    // ------------------------------------------------------------ categorías

    @Test
    fun `listar categorias manda el token y no pide las inactivas por defecto`() = runTest {
        servidor.enqueue(json(200, "[$bebidasJson]"))

        val r = repositorio.listarCategorias()

        assertEquals(ResultadoCatalogo.Exito(listOf(bebidas)), r)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/categorias", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
    }

    @Test
    fun `listar categorias con inactivas agrega todas=true`() = runTest {
        servidor.enqueue(json(200, "[]"))

        repositorio.listarCategorias(incluirInactivas = true)

        assertEquals("/api/categorias?todas=true", servidor.takeRequest().path)
    }

    @Test
    fun `crear categoria envia el nombre y devuelve la categoria`() = runTest {
        servidor.enqueue(json(201, bebidasJson))

        val r = repositorio.crearCategoria("Bebidas")

        assertEquals(ResultadoCatalogo.Exito(bebidas), r)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/categorias", pedido.path)
        assertEquals("Bebidas", cuerpoDe(pedido)["nombre"]!!.jsonPrimitive.content)
    }

    @Test
    fun `renombrar usa PUT con el id`() = runTest {
        servidor.enqueue(json(200, bebidasJson.replace("Bebidas", "Cafés")))

        val r = repositorio.renombrarCategoria(3, "Cafés") as ResultadoCatalogo.Exito

        assertEquals("Cafés", r.valor.nombre)
        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/api/categorias/3", pedido.path)
        assertEquals("Cafés", cuerpoDe(pedido)["nombre"]!!.jsonPrimitive.content)
    }

    @Test
    fun `desactivar y activar categoria usan sus rutas`() = runTest {
        servidor.enqueue(json(200, bebidasJson.replace("true", "false")))
        servidor.enqueue(json(200, bebidasJson))

        val desactivada = repositorio.desactivarCategoria(3) as ResultadoCatalogo.Exito
        val activada = repositorio.activarCategoria(3) as ResultadoCatalogo.Exito

        assertFalse(desactivada.valor.activa)
        assertTrue(activada.valor.activa)
        val primera = servidor.takeRequest()
        assertEquals("POST", primera.method)
        assertEquals("/api/categorias/3/desactivar", primera.path)
        assertEquals("/api/categorias/3/activar", servidor.takeRequest().path)
    }

    @Test
    fun `un nombre repetido devuelve Conflicto con el campo nombre`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Revisa los datos ingresados.","campos":{"nombre":"Ya existe una categoría con ese nombre"}}"""))

        val r = repositorio.crearCategoria("bebidas")

        assertEquals(
            ResultadoCatalogo.Conflicto(
                "Revisa los datos ingresados.",
                mapOf("nombre" to "Ya existe una categoría con ese nombre"),
            ),
            r,
        )
    }

    @Test
    fun `un nombre invalido devuelve DatosInvalidos con el campo`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Revisa los datos ingresados.","campos":{"nombre":"no puede estar vacío"}}"""))

        val r = repositorio.crearCategoria("")

        assertEquals(
            ResultadoCatalogo.DatosInvalidos("Revisa los datos ingresados.", mapOf("nombre" to "no puede estar vacío")),
            r,
        )
    }

    // ------------------------------------------------------------ productos

    @Test
    fun `listar productos sin filtros ni fuera del menu no agrega parametros`() = runTest {
        servidor.enqueue(json(200, "[${productoJson()}]"))

        val r = repositorio.listarProductos() as ResultadoCatalogo.Exito

        assertEquals(1, r.valor.size)
        assertEquals("/api/productos", servidor.takeRequest().path)
    }

    @Test
    fun `listar productos de una categoria incluyendo los fuera del menu`() = runTest {
        servidor.enqueue(json(200, "[]"))

        repositorio.listarProductos(categoriaId = 3, incluirFueraDelMenu = true)

        assertEquals("/api/productos?categoriaId=3&todos=true", servidor.takeRequest().path)
    }

    @Test
    fun `el producto se traduce completo y el precio conserva sus decimales`() = runTest {
        servidor.enqueue(json(200, productoJson()))

        val p = (repositorio.obtenerProducto(9) as ResultadoCatalogo.Exito).valor

        assertEquals(9L, p.id)
        assertEquals(3L, p.categoriaId)
        assertEquals("Bebidas", p.categoriaNombre)
        assertEquals("Latte", p.nombre)
        assertEquals("Con leche", p.descripcion)
        assertEquals(BigDecimal("6500.50"), p.precio)
        assertNull(p.imagenUrl)
        assertEquals(12, p.stockActual)
        assertEquals(4, p.umbralMinimo)
        assertTrue(p.enElMenu)
        assertEquals("/api/productos/9", servidor.takeRequest().path)
    }

    @Test
    fun `un producto esta en el menu solo si el y su categoria estan activos`() = runTest {
        servidor.enqueue(json(200, productoJson(activo = false)))
        servidor.enqueue(json(200, productoJson(categoriaActiva = false)))

        val desactivado = (repositorio.obtenerProducto(9) as ResultadoCatalogo.Exito).valor
        val deCategoriaInactiva = (repositorio.obtenerProducto(9) as ResultadoCatalogo.Exito).valor

        assertFalse(desactivado.enElMenu)
        assertFalse(deCategoriaInactiva.enElMenu)
        assertTrue(deCategoriaInactiva.activo)
    }

    @Test
    fun `crear producto envia los datos y omite los opcionales vacios`() = runTest {
        servidor.enqueue(json(201, productoJson()))

        val r = repositorio.crearProducto(
            DatosProducto(
                nombre = "Latte",
                precio = BigDecimal("6500.50"),
                categoriaId = 3,
                descripcion = "   ",
                imagenUrl = null,
                existenciasIniciales = 12,
                umbralMinimo = null,
            ),
        )

        assertTrue(r is ResultadoCatalogo.Exito)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/productos", pedido.path)
        val cuerpo = cuerpoDe(pedido)
        assertEquals("Latte", cuerpo["nombre"]!!.jsonPrimitive.content)
        assertEquals("6500.50", cuerpo["precio"]!!.jsonPrimitive.content)
        assertEquals("3", cuerpo["categoriaId"]!!.jsonPrimitive.content)
        assertEquals("12", cuerpo["existenciasIniciales"]!!.jsonPrimitive.content)
        assertFalse(cuerpo.containsKey("descripcion"))
        assertFalse(cuerpo.containsKey("imagenUrl"))
        assertFalse(cuerpo.containsKey("umbralMinimo"))
    }

    @Test
    fun `editar producto no envia existencias`() = runTest {
        servidor.enqueue(json(200, productoJson()))

        repositorio.editarProducto(
            9,
            DatosProducto("Latte", BigDecimal("7000"), 3, "Con leche", "https://img.test/l.png", existenciasIniciales = 99, umbralMinimo = 5),
        )

        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/api/productos/9", pedido.path)
        val cuerpo = cuerpoDe(pedido)
        assertFalse(cuerpo.containsKey("existenciasIniciales"))
        assertEquals("https://img.test/l.png", cuerpo["imagenUrl"]!!.jsonPrimitive.content)
        assertEquals("5", cuerpo["umbralMinimo"]!!.jsonPrimitive.content)
    }

    @Test
    fun `desactivar y activar producto usan sus rutas`() = runTest {
        servidor.enqueue(json(200, productoJson(activo = false)))
        servidor.enqueue(json(200, productoJson()))

        val desactivado = (repositorio.desactivarProducto(9) as ResultadoCatalogo.Exito).valor
        val activado = (repositorio.activarProducto(9) as ResultadoCatalogo.Exito).valor

        assertFalse(desactivado.activo)
        assertTrue(activado.activo)
        assertEquals("/api/productos/9/desactivar", servidor.takeRequest().path)
        assertEquals("/api/productos/9/activar", servidor.takeRequest().path)
    }

    @Test
    fun `crear en una categoria desactivada devuelve Conflicto con categoriaId`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Revisa los datos ingresados.","campos":{"categoriaId":"La categoría está desactivada"}}"""))

        val r = repositorio.crearProducto(DatosProducto("Latte", BigDecimal("6500"), 3))

        assertEquals(
            ResultadoCatalogo.Conflicto(
                "Revisa los datos ingresados.",
                mapOf("categoriaId" to "La categoría está desactivada"),
            ),
            r,
        )
    }

    // ------------------------------------------------------------ errores comunes

    @Test
    fun `401 devuelve SesionInvalida y cierra la sesion`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró. Inicia sesión de nuevo."}"""))

        assertEquals(ResultadoCatalogo.SesionInvalida, repositorio.listarCategorias())
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `403 devuelve SinPermiso con el mensaje del servidor`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        val r = repositorio.crearCategoria("Postres")

        assertEquals(ResultadoCatalogo.SinPermiso("No tienes permiso para realizar esta acción."), r)
    }

    @Test
    fun `404 devuelve NoEncontrado`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"Categoría 3 no existe"}"""))

        assertEquals(ResultadoCatalogo.NoEncontrado, repositorio.desactivarCategoria(3))
    }

    @Test
    fun `un error 500 sin cuerpo devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(500))

        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.listarCategorias())
    }

    @Test
    fun `un 200 con cuerpo ilegible devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(json(200, """{"esto":"no es una lista"}"""))

        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.listarCategorias())
    }

    @Test
    fun `un 409 con cuerpo ilegible sigue siendo un conflicto sin mensaje`() = runTest {
        servidor.enqueue(json(409, "no es json"))

        assertEquals(ResultadoCatalogo.Conflicto(null, emptyMap()), repositorio.crearCategoria("X"))
    }

    @Test
    fun `sin conexion devuelve SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(ResultadoCatalogo.SinConexion, repositorio.listarCategorias())
    }
}
