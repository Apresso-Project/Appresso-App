package com.appresso.app.data.cuenta

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.EliminarMisDatosRequest
import com.appresso.app.data.remote.MisDatosApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** La eliminación contra un servidor simulado que responde como el backend (docs/api-clientes.md). */
class MisDatosRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: MisDatosRepository

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        repositorio = MisDatosRepositoryRemoto(
            ClienteHttp.crearRetrofit(servidor.url("/").toString(), tiempoLimiteSegundos = 2).create(MisDatosApi::class.java),
        )
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    @Test
    fun `un 204 es eliminado y se envia la contrasena`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(204))

        assertEquals(ResultadoEliminacion.Eliminado, repositorio.eliminar("Clave-1234"))

        val solicitud = servidor.takeRequest()
        assertEquals("POST", solicitud.method)
        assertEquals("/api/clientes/yo/eliminacion", solicitud.path)
        assertEquals("""{"contrasena":"Clave-1234"}""", solicitud.body.readUtf8())
    }

    @Test
    fun `un 400 y un 423 traen el mensaje del servidor`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"La contraseña no es correcta.","campos":{"contrasena":"x"}}"""))
        servidor.enqueue(json(423, """{"mensaje":"Cuenta bloqueada temporalmente."}"""))
        servidor.enqueue(json(400, "no es json"))

        assertEquals(ResultadoEliminacion.Rechazado("La contraseña no es correcta."), repositorio.eliminar("x"))
        assertEquals(ResultadoEliminacion.Rechazado("Cuenta bloqueada temporalmente."), repositorio.eliminar("x"))
        assertEquals(ResultadoEliminacion.Rechazado(null), repositorio.eliminar("x"))
    }

    @Test
    fun `un 500 es error del servidor y sin servidor es sin conexion`() = runTest {
        servidor.enqueue(json(500, "{}"))
        assertEquals(ResultadoEliminacion.ErrorDelServidor, repositorio.eliminar("x"))

        servidor.shutdown()
        assertEquals(ResultadoEliminacion.SinConexion, repositorio.eliminar("x"))
    }

    @Test
    fun `la contrasena no aparece en el texto de la solicitud`() {
        assertFalse(EliminarMisDatosRequest("Clave-1234").toString().contains("Clave-1234"))
    }

    private fun json(codigo: Int, cuerpo: String) = MockResponse()
        .setResponseCode(codigo)
        .setHeader("Content-Type", "application/json")
        .setBody(cuerpo)
}
