package com.appresso.app.data.clientes

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClientesRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: ClientesRepository

    private val cliente1 = Cliente(
        id = 1,
        nombre = "Carlos",
        apellido = "Mora",
        documento = "100200300",
        celular = "3001112233",
        puntos = 0,
        aceptoTratamientoDatos = true,
        fechaAceptacionDatos = "2026-11-17T20:00:00Z",
    )

    private val cliente1Json = """
        {
          "id": 1,
          "nombre": "Carlos",
          "apellido": "Mora",
          "documento": "100200300",
          "celular": "3001112233",
          "puntos": 0,
          "aceptoTratamientoDatos": true,
          "fechaAceptacionDatos": "2026-11-17T20:00:00Z"
        }
    """.trimIndent()

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.MESERO, "Mesero")) }
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = ClientesRepositoryRemoto(retrofit.create(ClientesApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `listar devuelve la lista de clientes exitosamente`() = runTest {
        servidor.enqueue(json(200, "[$cliente1Json]"))

        val resultado = repositorio.listar()

        assertEquals(ResultadoClientes.Exito(listOf(cliente1)), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/clientes", pedido.path)
    }

    @Test
    fun `registrar con exito devuelve el cliente creado`() = runTest {
        servidor.enqueue(json(201, cliente1Json))

        val req = ClienteCrearRequest("Carlos", "Mora", "100200300", "3001112233", true)
        val resultado = repositorio.registrar(req)

        assertEquals(ResultadoClientes.Exito(cliente1), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        assertEquals("/api/clientes", pedido.path)
    }

    @Test
    fun `registrar documento duplicado devuelve Conflicto 409`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Ya existe un cliente con ese documento."}"""))

        val req = ClienteCrearRequest("Carlos", "Mora", "100200300", "3001112233", true)
        val resultado = repositorio.registrar(req)

        assertTrue(resultado is ResultadoClientes.Conflicto)
        assertEquals("Ya existe un cliente con ese documento.", (resultado as ResultadoClientes.Conflicto).mensaje)
    }

    @Test
    fun `buscarPorDocumento devuelve el cliente cuando existe`() = runTest {
        servidor.enqueue(json(200, cliente1Json))

        val resultado = repositorio.buscarPorDocumento("100200300")

        assertEquals(ResultadoClientes.Exito(cliente1), resultado)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/clientes/100200300", pedido.path)
    }

    @Test
    fun `buscarPorDocumento devuelve NoEncontrado cuando no existe`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"No se encontró ningún cliente con ese documento."}"""))

        val resultado = repositorio.buscarPorDocumento("999999")

        assertTrue(resultado is ResultadoClientes.NoEncontrado)
        assertEquals("No se encontró ningún cliente con ese documento.", (resultado as ResultadoClientes.NoEncontrado).mensaje)
    }

    @Test
    fun `error de red devuelve SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val resultado = repositorio.buscarPorDocumento("100200300")

        assertEquals(ResultadoClientes.SinConexion, resultado)
    }
}
