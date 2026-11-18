package com.appresso.app.data.pagos

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.PagosApi
import java.math.BigDecimal
import java.time.Instant
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

/** Cada caso del contrato (docs/api-pagos.md) contra un servidor HTTP simulado. */
class PagosRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: PagosRepository

    private val operacion = "3f2b6c1e-8d4a-4c55-9a0b-1d2e3f4a5b6c"

    private fun pagoJson(medio: String = "EFECTIVO", valor: String = "12500.00") =
        """{"id":5,"comandaId":17,"mesa":"Mesa 3","valor":$valor,"medio":"$medio",""" +
            """"fecha":"2026-11-18T15:42:10.123Z","confirmadoPorId":4,"confirmadoPorNombre":"Ana Cajera",""" +
            """"identificadorOperacion":"$operacion","jornadaId":2,"comandaEstado":"CERRADA"}"""

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.CAJERO, "Ana")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = PagosRepositoryRemoto(retrofit.create(PagosApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `efectivo usa su ruta manda el token y no envia el valor`() = runTest {
        servidor.enqueue(json(201, pagoJson()))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion)

        val exito = r as ResultadoPago.Exito
        assertFalse(exito.repetido)
        assertEquals(5L, exito.pago.id)
        assertEquals(17L, exito.pago.comandaId)
        assertEquals("Mesa 3", exito.pago.mesa)
        assertEquals(0, BigDecimal("12500").compareTo(exito.pago.valor))
        assertEquals(MedioPago.EFECTIVO, exito.pago.medio)
        assertEquals(Instant.parse("2026-11-18T15:42:10.123Z"), exito.pago.fecha)
        assertEquals("Ana Cajera", exito.pago.confirmadoPorNombre)

        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/pagos/efectivo", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
        val cuerpo = Json.parseToJsonElement(pedido.body.readUtf8()).jsonObject
        assertEquals("17", cuerpo["comandaId"]!!.jsonPrimitive.content)
        assertEquals(operacion, cuerpo["identificadorOperacion"]!!.jsonPrimitive.content)
        assertFalse("el valor lo calcula el servidor", cuerpo.containsKey("valor"))
    }

    @Test
    fun `transferencia usa su propia ruta`() = runTest {
        servidor.enqueue(json(201, pagoJson(medio = "TRANSFERENCIA")))

        val r = repositorio.confirmar(17, MedioPago.TRANSFERENCIA, operacion) as ResultadoPago.Exito

        assertEquals(MedioPago.TRANSFERENCIA, r.pago.medio)
        assertEquals("/api/pagos/transferencia", servidor.takeRequest().path)
    }

    @Test
    fun `el valor con decimales se lee sin perder exactitud`() = runTest {
        servidor.enqueue(json(201, pagoJson(valor = "12500.55")))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion) as ResultadoPago.Exito

        assertEquals(BigDecimal("12500.55"), r.pago.valor)
    }

    @Test
    fun `un 200 es el reintento de un pago ya registrado`() = runTest {
        servidor.enqueue(json(200, pagoJson()))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion) as ResultadoPago.Exito

        assertTrue(r.repetido)
    }

    @Test
    fun `una fecha ilegible no impide mostrar el pago`() = runTest {
        servidor.enqueue(json(201, pagoJson().replace("2026-11-18T15:42:10.123Z", "ayer")))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion) as ResultadoPago.Exito

        assertNull(r.pago.fecha)
    }

    @Test
    fun `409 devuelve el mensaje del servidor`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Esta comanda ya fue pagada."}"""))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion)

        assertEquals(ResultadoPago.Conflicto("Esta comanda ya fue pagada."), r)
    }

    @Test
    fun `403 devuelve SinPermiso con el mensaje del servidor`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion)

        assertEquals(ResultadoPago.SinPermiso("No tienes permiso para realizar esta acción."), r)
    }

    @Test
    fun `401 devuelve SesionInvalida y cierra la sesion`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró. Inicia sesión de nuevo."}"""))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion)

        assertEquals(ResultadoPago.SesionInvalida, r)
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `404 devuelve NoEncontrada`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"Comanda 17 no existe"}"""))

        assertEquals(ResultadoPago.NoEncontrada, repositorio.confirmar(17, MedioPago.EFECTIVO, operacion))
    }

    @Test
    fun `400 devuelve DatosInvalidos`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"La solicitud no tiene un formato válido."}"""))

        val r = repositorio.confirmar(17, MedioPago.EFECTIVO, operacion)

        assertEquals(ResultadoPago.DatosInvalidos("La solicitud no tiene un formato válido."), r)
    }

    @Test
    fun `un error 500 sin cuerpo devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(500))

        assertEquals(ResultadoPago.ErrorDelServidor, repositorio.confirmar(17, MedioPago.EFECTIVO, operacion))
    }

    @Test
    fun `un 201 con cuerpo ilegible devuelve ErrorDelServidor`() = runTest {
        servidor.enqueue(json(201, """{"esto":"no es un pago"}"""))

        assertEquals(ResultadoPago.ErrorDelServidor, repositorio.confirmar(17, MedioPago.EFECTIVO, operacion))
    }

    @Test
    fun `un 409 con cuerpo ilegible sigue siendo un conflicto sin mensaje`() = runTest {
        servidor.enqueue(json(409, "no es json"))

        assertEquals(ResultadoPago.Conflicto(null), repositorio.confirmar(17, MedioPago.EFECTIVO, operacion))
    }

    @Test
    fun `sin conexion devuelve SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(ResultadoPago.SinConexion, repositorio.confirmar(17, MedioPago.EFECTIVO, operacion))
    }
}
