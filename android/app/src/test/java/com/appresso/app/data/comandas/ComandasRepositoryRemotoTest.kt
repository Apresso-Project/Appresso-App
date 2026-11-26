package com.appresso.app.data.comandas

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ComandasApi
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
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

/** Cada caso del contrato de mesas y comandas (docs/api-comandas.md) contra un servidor simulado. */
class ComandasRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: ComandasRepository

    private val comandaJson = """
        {"id":12,"mesaId":3,"mesaNombre":"Mesa 3","meseroId":5,"clienteId":null,"estado":"PENDIENTE",
         "lineas":[
           {"id":1,"productoId":7,"productoNombre":"Latte","cantidad":2,"precioUnitario":6500.00,"subtotal":13000.00,
            "observaciones":"sin azúcar","agregadoDespuesDeEnvio":false},
           {"id":2,"productoId":9,"productoNombre":"Brownie","cantidad":1,"precioUnitario":"4500.50","subtotal":"4500.50",
            "observaciones":"  ","agregadoDespuesDeEnvio":false}],
         "subtotal":17500.50,"descuento":0.00,"total":17500.50,
         "fechaCreacion":"2026-11-26T15:00:00Z","fechaEnvio":null}
    """.trimIndent()

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.MESERO, "Ana")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = ComandasRepositoryRemoto(retrofit.create(ComandasApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    // ---------------------------------------------------------------- mesas

    @Test
    fun `listar mesas usa GET api mesas con el token y traduce el estado`() = runTest {
        servidor.enqueue(
            json(
                200,
                """[{"id":1,"nombre":"Mesa 1","estado":"DISPONIBLE"},{"id":2,"nombre":"Terraza","estado":"OCUPADA"}]""",
            ),
        )

        val r = repositorio.listarMesas() as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/mesas", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
        assertEquals(listOf(Mesa(1, "Mesa 1", EstadoMesa.DISPONIBLE), Mesa(2, "Terraza", EstadoMesa.OCUPADA)), r.valor)
        assertTrue(r.valor[0].disponible)
        assertFalse(r.valor[1].disponible)
    }

    @Test
    fun `una mesa con estado desconocido se muestra ocupada`() = runTest {
        servidor.enqueue(json(200, """[{"id":1,"nombre":"Mesa 1","estado":"RESERVADA"}]"""))

        val r = repositorio.listarMesas() as ResultadoCatalogo.Exito

        assertEquals(EstadoMesa.OCUPADA, r.valor[0].estado)
    }

    @Test
    fun `crear mesa envia POST api mesas con el nombre sin espacios y la devuelve disponible`() = runTest {
        servidor.enqueue(json(201, """{"id":4,"nombre":"Mesa 4","estado":"DISPONIBLE"}""").setHeader("Location", "/api/mesas/4"))

        val r = repositorio.crearMesa("  Mesa 4 ") as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/mesas", pedido.path)
        assertEquals("""{"nombre":"Mesa 4"}""", pedido.body.readUtf8())
        assertEquals(Mesa(4, "Mesa 4", EstadoMesa.DISPONIBLE), r.valor)
    }

    @Test
    fun `crear mesa con nombre repetido es conflicto con el campo nombre`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Ya existe una mesa con ese nombre","campos":{"nombre":"Ya existe una mesa con ese nombre"}}"""))

        val r = repositorio.crearMesa("mesa 4") as ResultadoCatalogo.Conflicto

        assertEquals("Ya existe una mesa con ese nombre", r.campos["nombre"])
    }

    @Test
    fun `crear mesa con nombre vacio es dato invalido con el campo nombre`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Revisa los datos ingresados.","campos":{"nombre":"El nombre es obligatorio"}}"""))

        val r = repositorio.crearMesa(" ") as ResultadoCatalogo.DatosInvalidos

        assertEquals("El nombre es obligatorio", r.campos["nombre"])
    }

    @Test
    fun `crear mesa con un rol distinto al administrador es sin permiso y no cierra la sesion`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        val r = repositorio.crearMesa("Mesa 9")

        assertEquals(ResultadoCatalogo.SinPermiso("No tienes permiso para realizar esta acción."), r)
        assertEquals("tok.en.x", almacen.sesion.value?.token)
    }

    @Test
    fun `un 401 es sesion invalida y cierra la sesion`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró."}"""))

        assertEquals(ResultadoCatalogo.SesionInvalida, repositorio.listarMesas())
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `sin conexion y error del servidor no lanzan excepciones`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(ResultadoCatalogo.SinConexion, repositorio.listarMesas())

        servidor.enqueue(json(500, "{}"))
        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.listarMesas())
    }

    // ---------------------------------------------------------------- comandas

    @Test
    fun `crear comanda envia la mesa y los productos y lee subtotales y total exactos`() = runTest {
        servidor.enqueue(json(201, comandaJson))

        val r = repositorio.crearComanda(
            NuevaComanda(mesaId = 3, items = listOf(ItemPedido(7, 2, " sin azúcar "), ItemPedido(9, 1))),
        ) as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/comandas", pedido.path)
        // clienteId y recompensaId nulos no se envían; las observaciones van sin espacios de los extremos.
        assertEquals(
            """{"mesaId":3,"items":[{"productoId":7,"cantidad":2,"observaciones":"sin azúcar"},{"productoId":9,"cantidad":1}]}""",
            pedido.body.readUtf8(),
        )
        val comanda = r.valor
        assertEquals(12L, comanda.id)
        assertEquals("Mesa 3", comanda.mesaNombre)
        assertEquals(EstadoComanda.PENDIENTE, comanda.estado)
        assertEquals(BigDecimal("13000.00"), comanda.lineas[0].subtotal)
        assertEquals(BigDecimal("4500.50"), comanda.lineas[1].precioUnitario)
        assertEquals("sin azúcar", comanda.lineas[0].observaciones)
        assertNull(comanda.lineas[1].observaciones)
        assertEquals(BigDecimal("17500.50"), comanda.subtotal)
        assertEquals(BigDecimal("0.00"), comanda.descuento)
        assertEquals(BigDecimal("17500.50"), comanda.total)
        assertEquals(comanda.subtotal, comanda.lineas.fold(BigDecimal.ZERO) { s, l -> s + l.subtotal })
    }

    @Test
    fun `crear comanda con descuento envia cliente y recompensa`() = runTest {
        servidor.enqueue(json(201, comandaJson))

        repositorio.crearComanda(NuevaComanda(3, listOf(ItemPedido(7, 1)), clienteId = 12, recompensaId = 4))

        assertEquals(
            """{"mesaId":3,"items":[{"productoId":7,"cantidad":1}],"clienteId":12,"recompensaId":4}""",
            servidor.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun `la comanda vacia es rechazada con 400 y el campo items`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Revisa los datos ingresados.","campos":{"items":"La comanda debe tener al menos un producto"}}"""))

        val r = repositorio.crearComanda(NuevaComanda(3, emptyList())) as ResultadoCatalogo.DatosInvalidos

        assertEquals("La comanda debe tener al menos un producto", r.campos["items"])
    }

    @Test
    fun `mesa ocupada o sin stock es conflicto con el mensaje del servidor`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"La mesa ya tiene una comanda abierta"}"""))

        val r = repositorio.crearComanda(NuevaComanda(3, listOf(ItemPedido(7, 1)))) as ResultadoCatalogo.Conflicto

        assertEquals("La mesa ya tiene una comanda abierta", r.mensaje)
    }

    @Test
    fun `enviar a cocina usa PATCH estado con ENVIADA`() = runTest {
        servidor.enqueue(json(200, comandaJson.replace("\"PENDIENTE\"", "\"ENVIADA\"")))

        val r = repositorio.cambiarEstado(12, EstadoComanda.ENVIADA) as ResultadoCatalogo.Exito

        val pedido = servidor.takeRequest()
        assertEquals("PATCH", pedido.method)
        assertEquals("/api/comandas/12/estado", pedido.path)
        assertEquals("""{"estado":"ENVIADA"}""", pedido.body.readUtf8())
        assertEquals(EstadoComanda.ENVIADA, r.valor.estado)
    }

    @Test
    fun `una transicion no permitida es rechazada con 400`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Transición no permitida: PENDIENTE → LISTA"}"""))

        val r = repositorio.cambiarEstado(12, EstadoComanda.LISTA) as ResultadoCatalogo.DatosInvalidos

        assertEquals("Transición no permitida: PENDIENTE → LISTA", r.mensaje)
    }

    @Test
    fun `consultar comanda usa GET y un 404 es no encontrado`() = runTest {
        servidor.enqueue(json(200, comandaJson))
        servidor.enqueue(json(404, """{"mensaje":"Comanda no encontrada"}"""))

        assertTrue(repositorio.consultarComanda(12) is ResultadoCatalogo.Exito)
        assertEquals("/api/comandas/12", servidor.takeRequest().path)
        assertEquals(ResultadoCatalogo.NoEncontrado, repositorio.consultarComanda(99))
    }

    @Test
    fun `un estado de comanda desconocido es error del servidor`() = runTest {
        servidor.enqueue(json(200, comandaJson.replace("\"PENDIENTE\"", "\"PERDIDA\"")))

        assertEquals(ResultadoCatalogo.ErrorDelServidor, repositorio.consultarComanda(12))
    }
}
