package com.appresso.app.data.menu

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.MenuApi
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cada caso del contrato de `GET /api/menu` (docs/api-menu-cocina.md) contra un servidor simulado. */
class MenuRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: MenuRepository

    private val menuJson = """
        [
          {"id":1,"nombre":"Bebidas","productos":[
            {"id":9,"nombre":"Latte","descripcion":"Con leche","precio":6500.00,"imagenUrl":"https://img.test/l.png",
             "existencias":12,"disponibilidad":"DISPONIBLE"},
            {"id":10,"nombre":"Té","descripcion":null,"precio":3000,"imagenUrl":null,
             "existencias":2,"disponibilidad":"POCAS_UNIDADES"}]},
          {"id":2,"nombre":"Postres","productos":[
            {"id":20,"nombre":"Brownie","descripcion":"  ","precio":"4500.50","imagenUrl":"  ",
             "existencias":8,"disponibilidad":"DISPONIBLE"}]}
        ]
    """.trimIndent()

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        val almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.MESERO, "Ana")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = MenuRepositoryRemoto(retrofit.create(MenuApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `consultar usa GET api menu con el token y traduce categorias y productos`() = runTest {
        servidor.enqueue(json(200, menuJson))

        val r = repositorio.consultar() as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/menu", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
        assertEquals(listOf("Bebidas", "Postres"), r.valor.map { it.nombre })
        val latte = r.valor[0].productos[0]
        assertEquals(9L, latte.id)
        assertEquals("Con leche", latte.descripcion)
        assertEquals("https://img.test/l.png", latte.imagenUrl)
        assertEquals(12, latte.existencias)
        assertEquals(Disponibilidad.DISPONIBLE, latte.disponibilidad)
    }

    @Test
    fun `el precio se lee exacto venga como numero entero decimal o texto`() = runTest {
        servidor.enqueue(json(200, menuJson))

        val r = repositorio.consultar() as ResultadoCatalogo.Exito

        assertEquals(0, BigDecimal("6500").compareTo(r.valor[0].productos[0].precio))
        assertEquals(0, BigDecimal("3000").compareTo(r.valor[0].productos[1].precio))
        assertEquals(0, BigDecimal("4500.50").compareTo(r.valor[1].productos[0].precio))
    }

    @Test
    fun `pocas unidades se traduce y un valor desconocido cuenta como disponible`() = runTest {
        servidor.enqueue(json(200, menuJson))
        val te = (repositorio.consultar() as ResultadoCatalogo.Exito).valor[0].productos[1]
        assertEquals(Disponibilidad.POCAS_UNIDADES, te.disponibilidad)
        assertEquals(2, te.existencias)

        servidor.enqueue(json(200, """[{"id":1,"nombre":"B","productos":[
            {"id":1,"nombre":"X","precio":1000,"existencias":5,"disponibilidad":"OTRA"}]}]"""))
        val x = (repositorio.consultar() as ResultadoCatalogo.Exito).valor[0].productos[0]
        assertEquals(Disponibilidad.DISPONIBLE, x.disponibilidad)
    }

    @Test
    fun `descripcion e imagen en blanco llegan como null`() = runTest {
        servidor.enqueue(json(200, menuJson))

        val brownie = (repositorio.consultar() as ResultadoCatalogo.Exito).valor[1].productos[0]

        assertNull(brownie.descripcion)
        assertNull(brownie.imagenUrl)
    }

    @Test
    fun `una categoria sin productos no se muestra`() = runTest {
        servidor.enqueue(json(200, """[{"id":1,"nombre":"Vacía","productos":[]},{"id":2,"nombre":"Con","productos":[
            {"id":1,"nombre":"X","precio":1000,"existencias":5,"disponibilidad":"DISPONIBLE"}]}]"""))

        val r = repositorio.consultar() as ResultadoCatalogo.Exito

        assertEquals(listOf("Con"), r.valor.map { it.nombre })
    }

    @Test
    fun `un menu vacio es un exito con lista vacia`() = runTest {
        servidor.enqueue(json(200, "[]"))

        assertEquals(ResultadoCatalogo.Exito(emptyList<MenuCategoria>()), repositorio.consultar())
    }

    @Test
    fun `403 devuelve SinPermiso con el mensaje del servidor`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        assertEquals(
            ResultadoCatalogo.SinPermiso("No tienes permiso para realizar esta acción."),
            repositorio.consultar(),
        )
    }

    @Test
    fun `401 devuelve SesionInvalida`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Sesión no válida"}"""))

        assertEquals(ResultadoCatalogo.SesionInvalida, repositorio.consultar())
    }

    @Test
    fun `500 devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(json(500, """{"mensaje":"Error interno"}"""))

        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.consultar())
    }

    @Test
    fun `un cuerpo ilegible con 200 devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(json(200, "esto no es json"))

        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.consultar())
    }

    @Test
    fun `un fallo de red devuelve SinConexion sin lanzar`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertTrue(repositorio.consultar() == ResultadoCatalogo.SinConexion)
    }
}
