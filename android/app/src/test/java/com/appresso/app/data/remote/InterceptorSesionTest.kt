package com.appresso.app.data.remote

import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Prueba el token en las peticiones y el regreso al login con 401 (SCRUM-46) contra un servidor
 * HTTP simulado.
 */
class InterceptorSesionTest {

    private val sesion = Sesion("token-de-ana", Rol.MESERO, "Ana")

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesion
    private lateinit var cliente: OkHttpClient

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria()
        cliente = ClienteHttp.crearCliente(tiempoLimiteSegundos = 3, almacenSesion = almacen)
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    // ---------------------------------------------------------------- el token en la petición

    @Test
    fun `con sesion cada peticion lleva Authorization Bearer con el token`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(200))

        llamar("/api/comandas")

        assertEquals("Bearer token-de-ana", servidor.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `sin sesion la peticion no lleva Authorization`() {
        servidor.enqueue(MockResponse().setResponseCode(401))

        llamar("/api/comandas")

        assertNull(servidor.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `el login nunca lleva Authorization aunque haya una sesion`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(200))

        llamar("/api/auth/login")

        assertNull(servidor.takeRequest().getHeader("Authorization"))
    }

    // ---------------------------------------------------------------- 401 → login

    @Test
    fun `un 401 en un endpoint protegido cierra la sesion`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(401))

        val codigo = llamar("/api/comandas")

        assertEquals(401, codigo)
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `un 401 del login no toca la sesion`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(401))

        llamar("/api/auth/login")

        assertEquals(sesion, almacen.sesion.value)
    }

    @Test
    fun `un 403 no cierra la sesion`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(403))

        val codigo = llamar("/api/caja/saldos")

        assertEquals(403, codigo)
        assertEquals(sesion, almacen.sesion.value)
    }

    @Test
    fun `un 200 no cambia la sesion`() {
        almacen.guardar(sesion)
        servidor.enqueue(MockResponse().setResponseCode(200))

        llamar("/api/comandas")

        assertEquals(sesion, almacen.sesion.value)
    }

    @Test
    fun `el 401 de un token viejo no cierra una sesion nueva`() {
        val nueva = Sesion("token-nuevo", Rol.CAJERO, "Ana")
        almacen.guardar(sesion)
        // Mientras la petición con el token viejo está en camino, el usuario vuelve a iniciar sesión.
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                almacen.guardar(nueva)
                return MockResponse().setResponseCode(401)
            }
        }

        llamar("/api/comandas")

        assertEquals(nueva, almacen.sesion.value)
    }

    // ---------------------------------------------------------------- ayudas

    private fun llamar(ruta: String): Int {
        val solicitud = Request.Builder().url(servidor.url(ruta)).get().build()
        return cliente.newCall(solicitud).execute().use { it.code }
    }
}
