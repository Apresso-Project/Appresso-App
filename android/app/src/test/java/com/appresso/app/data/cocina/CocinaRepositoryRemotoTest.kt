package com.appresso.app.data.cocina

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.CocinaApi
import java.time.Instant
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

/** Cada caso del contrato de `GET /api/comandas/cocina` (docs/api-menu-cocina.md) contra un servidor simulado. */
class CocinaRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: CocinaRepository

    private val listaJson = """
        [
          {"id":7,"mesaId":3,"mesa":"Mesa 3","estado":"ENVIADA","estadoCocina":"Pendiente",
           "fechaEnvio":"2026-11-22T20:15:03.412Z","items":[
             {"productoId":9,"producto":"Latte","cantidad":2,"observaciones":"sin azúcar","agregadoDespuesDeEnvio":false},
             {"productoId":4,"producto":"Croissant","cantidad":1,"observaciones":null,"agregadoDespuesDeEnvio":true}]},
          {"id":8,"mesaId":5,"mesa":"Mesa 5","estado":"EN_PREPARACION","estadoCocina":"En preparación",
           "fechaEnvio":"2026-11-22T20:16:00Z","items":[
             {"productoId":9,"producto":"Latte","cantidad":1,"observaciones":"  ","agregadoDespuesDeEnvio":false}]},
          {"id":9,"mesaId":6,"mesa":"Mesa 6","estado":"LISTA","estadoCocina":"Lista",
           "fechaEnvio":"2026-11-22T20:17:00Z","items":[]}
        ]
    """.trimIndent()

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        val almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.COCINERO, "Cocina")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = CocinaRepositoryRemoto(retrofit.create(CocinaApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `consultar usa GET api comandas cocina con el token y respeta el orden del servidor`() = runTest {
        servidor.enqueue(json(200, listaJson))

        val r = repositorio.consultar() as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/comandas/cocina", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
        assertEquals(listOf(7L, 8L, 9L), r.valor.map { it.id })
    }

    @Test
    fun `traduce los tres estados de cocina`() = runTest {
        servidor.enqueue(json(200, listaJson))

        val r = (repositorio.consultar() as ResultadoCatalogo.Exito).valor

        assertEquals(
            listOf(EstadoCocina.PENDIENTE, EstadoCocina.EN_PREPARACION, EstadoCocina.LISTA),
            r.map { it.estado },
        )
    }

    @Test
    fun `traduce mesa hora productos cantidades observaciones y la marca de agregado`() = runTest {
        servidor.enqueue(json(200, listaJson))

        val comanda = (repositorio.consultar() as ResultadoCatalogo.Exito).valor.first()

        assertEquals("Mesa 3", comanda.mesa)
        assertEquals(Instant.parse("2026-11-22T20:15:03.412Z"), comanda.fechaEnvio)
        assertEquals(
            listOf(
                ItemCocina("Latte", 2, "sin azúcar", agregado = false),
                ItemCocina("Croissant", 1, null, agregado = true),
            ),
            comanda.items,
        )
    }

    @Test
    fun `observaciones en blanco llegan como null`() = runTest {
        servidor.enqueue(json(200, listaJson))

        val item = (repositorio.consultar() as ResultadoCatalogo.Exito).valor[1].items.single()

        assertNull(item.observaciones)
    }

    @Test
    fun `las observaciones largas llegan completas`() = runTest {
        val larga = "sin hielo, ".repeat(9).trim() // 98 caracteres, cerca del máximo de 100
        servidor.enqueue(
            json(
                200,
                """[{"id":1,"mesaId":1,"mesa":"M","estado":"ENVIADA","estadoCocina":"Pendiente","fechaEnvio":null,
                "items":[{"productoId":1,"producto":"X","cantidad":1,"observaciones":"$larga","agregadoDespuesDeEnvio":false}]}]""",
            ),
        )

        val item = (repositorio.consultar() as ResultadoCatalogo.Exito).valor.single().items.single()

        assertEquals(larga, item.observaciones)
    }

    @Test
    fun `una fecha ausente o ilegible no tumba la comanda`() = runTest {
        servidor.enqueue(
            json(
                200,
                """[{"id":1,"mesa":"A","estado":"ENVIADA","fechaEnvio":null,"items":[]},
                    {"id":2,"mesa":"B","estado":"LISTA","fechaEnvio":"ayer","items":[]}]""",
            ),
        )

        val r = (repositorio.consultar() as ResultadoCatalogo.Exito).valor

        assertEquals(listOf(1L, 2L), r.map { it.id })
        assertTrue(r.all { it.fechaEnvio == null })
    }

    @Test
    fun `una comanda con un estado desconocido se deja fuera`() = runTest {
        servidor.enqueue(
            json(
                200,
                """[{"id":1,"mesa":"A","estado":"PENDIENTE","items":[]},{"id":2,"mesa":"B","estado":"ENVIADA","items":[]}]""",
            ),
        )

        val r = (repositorio.consultar() as ResultadoCatalogo.Exito).valor

        assertEquals(listOf(2L), r.map { it.id })
    }

    @Test
    fun `una lista vacia es un exito`() = runTest {
        servidor.enqueue(json(200, "[]"))

        val r = repositorio.consultar() as ResultadoCatalogo.Exito

        assertTrue(r.valor.isEmpty())
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
    fun `500 y un cuerpo ilegible devuelven ErrorDelServidor`() = runTest {
        servidor.enqueue(json(500, """{"mensaje":"Error interno"}"""))
        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.consultar())

        servidor.enqueue(json(200, "esto no es json"))
        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.consultar())
    }

    @Test
    fun `un fallo de red devuelve SinConexion sin lanzar`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(ResultadoCatalogo.SinConexion, repositorio.consultar())
    }
}
