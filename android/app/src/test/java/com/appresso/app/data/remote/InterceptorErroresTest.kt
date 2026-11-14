package com.appresso.app.data.remote

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InterceptorErroresTest {

    private val server = MockWebServer()
    private val bus = BusEventosRed()
    private val cliente = ClienteHttp.crearCliente(bus = bus)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun llamar(url: HttpUrl) {
        runCatching { cliente.newCall(Request.Builder().url(url).build()).execute().close() }
    }

    @Test
    fun respuesta403EnEndpointProtegidoEmiteProhibido() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val evento = async(start = CoroutineStart.UNDISPATCHED) { bus.eventos.first() }

        llamar(server.url("/api/inventario"))

        assertEquals(EventoRed.PROHIBIDO, withTimeout(2000) { evento.await() })
    }

    @Test
    fun perdidaDeConexionEmiteSinConexion() = runBlocking {
        val url = server.url("/api/inventario")
        server.shutdown() // el servidor deja de responder: simula la pérdida de red
        val evento = async(start = CoroutineStart.UNDISPATCHED) { bus.eventos.first() }

        llamar(url)

        assertEquals(EventoRed.SIN_CONEXION, withTimeout(2000) { evento.await() })
    }

    @Test
    fun elLoginNoEmiteEventos() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        // El tiempo límite va DENTRO del async: así la corrutina termina sola si no llega nada.
        val evento = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(300) { bus.eventos.first() }
        }

        llamar(server.url("/api/auth/login"))

        assertNull(evento.await())
    }
}