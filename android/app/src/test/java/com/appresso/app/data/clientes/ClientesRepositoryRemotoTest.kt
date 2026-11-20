package com.appresso.app.data.clientes

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** La búsqueda de clientes contra un servidor simulado que responde como el backend (docs/api-clientes.md). */
class ClientesRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: ClientesRepository

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        repositorio = ClientesRepositoryRemoto(
            ClienteHttp.crearRetrofit(servidor.url("/").toString(), tiempoLimiteSegundos = 2).create(ClientesApi::class.java),
        )
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    @Test
    fun `un 200 devuelve el cliente con sus puntos`() = runTest {
        servidor.enqueue(
            json(200, """{"id":7,"nombre":"Ana","apellido":"Pérez","documento":"1234567890","puntos":3}"""),
        )

        val resultado = repositorio.buscarPorDocumento("1234567890")

        assertEquals(ResultadoBusqueda.Encontrado(Cliente(7, "Ana", "Pérez", "1234567890", 3)), resultado)
        val solicitud = servidor.takeRequest()
        assertEquals("GET", solicitud.method)
        assertEquals("/api/clientes/1234567890", solicitud.path)
    }

    @Test
    fun `un 404 es cliente no encontrado`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"Cliente no encontrado"}"""))

        assertEquals(ResultadoBusqueda.NoEncontrado, repositorio.buscarPorDocumento("5555555555"))
    }

    @Test
    fun `un 403 trae el mensaje del servidor`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        assertEquals(
            ResultadoBusqueda.SinPermiso("No tienes permiso para realizar esta acción."),
            repositorio.buscarPorDocumento("1234567890"),
        )
    }

    @Test
    fun `un 500 es error del servidor y sin servidor es sin conexion`() = runTest {
        servidor.enqueue(json(500, "{}"))
        assertEquals(ResultadoBusqueda.ErrorDelServidor, repositorio.buscarPorDocumento("1234567890"))

        servidor.shutdown()
        assertEquals(ResultadoBusqueda.SinConexion, repositorio.buscarPorDocumento("1234567890"))
    }

    private fun json(codigo: Int, cuerpo: String) = MockResponse()
        .setResponseCode(codigo)
        .setHeader("Content-Type", "application/json")
        .setBody(cuerpo)
}
