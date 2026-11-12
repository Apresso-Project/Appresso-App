package com.appresso.app.data.auth

import com.appresso.app.data.remote.ClienteHttp
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba el repositorio contra un servidor HTTP simulado: cada caso del contrato
 * (docs/api-auth.md) responde lo que respondería el backend real.
 */
class AuthRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: AuthRepository

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        repositorio = repositorioApuntandoA(servidor)
    }

    @After
    fun detener() {
        // Ya puede estar apagado si la prueba simuló un servidor caído.
        runCatching { servidor.shutdown() }
    }

    // ---------------------------------------------------------------- 200

    @Test
    fun `200 devuelve la sesion con token rol y nombre`() = runTest {
        servidor.enqueue(json(200, """{"token":"abc.def.ghi","rol":"MESERO","nombre":"Ana"}"""))

        val resultado = repositorio.iniciarSesion("ana@appresso.test", "clave-1")

        assertEquals(ResultadoLogin.Exito(Sesion("abc.def.ghi", Rol.MESERO, "Ana")), resultado)
    }

    @Test
    fun `la solicitud es un POST a api auth login con los campos correo y contrasena`() = runTest {
        servidor.enqueue(json(200, """{"token":"t","rol":"CAJERO","nombre":"Ana"}"""))

        repositorio.iniciarSesion("ana@appresso.test", "clave-1")

        val solicitud = servidor.takeRequest()
        assertEquals("POST", solicitud.method)
        assertEquals("/api/auth/login", solicitud.path)
        assertTrue(solicitud.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val cuerpo = Json.parseToJsonElement(solicitud.body.readUtf8()).jsonObject
        assertEquals("ana@appresso.test", cuerpo["correo"]?.jsonPrimitive?.content)
        assertEquals("clave-1", cuerpo["contrasena"]?.jsonPrimitive?.content)
        assertEquals(setOf("correo", "contrasena"), cuerpo.keys)
    }

    @Test
    fun `una respuesta 200 con campos nuevos del backend se sigue entendiendo`() = runTest {
        servidor.enqueue(
            json(200, """{"token":"t","rol":"COCINERO","nombre":"Cocina","expiraEn":3600,"extra":{"a":1}}"""),
        )

        val resultado = repositorio.iniciarSesion("cocina@appresso.test", "x")

        assertEquals(ResultadoLogin.Exito(Sesion("t", Rol.COCINERO, "Cocina")), resultado)
    }

    @Test
    fun `se entienden los cinco roles`() = runTest {
        Rol.entries.forEach { rol ->
            servidor.enqueue(json(200, """{"token":"t","rol":"${rol.name}","nombre":"N"}"""))

            val resultado = repositorio.iniciarSesion("a@appresso.test", "x")

            assertEquals(rol, (resultado as ResultadoLogin.Exito).sesion.rol)
        }
    }

    @Test
    fun `200 con un rol desconocido es un error del servidor y no un cierre inesperado`() = runTest {
        servidor.enqueue(json(200, """{"token":"t","rol":"SUPERUSUARIO","nombre":"N"}"""))

        assertEquals(ResultadoLogin.ErrorDelServidor, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `200 con un cuerpo que no es JSON es un error del servidor`() = runTest {
        servidor.enqueue(json(200, "<html>Bad gateway</html>"))

        assertEquals(ResultadoLogin.ErrorDelServidor, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `200 sin cuerpo es un error del servidor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200))

        assertEquals(ResultadoLogin.ErrorDelServidor, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    // ---------------------------------------------------------------- 400, 401, 423

    @Test
    fun `400 devuelve el mensaje y los problemas por campo`() = runTest {
        servidor.enqueue(
            json(
                400,
                """{"mensaje":"Revisa los datos ingresados.","campos":{"correo":"El correo no tiene un formato válido"}}""",
            ),
        )

        val resultado = repositorio.iniciarSesion("no-es-correo", "x")

        assertEquals(
            ResultadoLogin.DatosInvalidos(
                "Revisa los datos ingresados.",
                mapOf("correo" to "El correo no tiene un formato válido"),
            ),
            resultado,
        )
    }

    @Test
    fun `400 sin campos o con un cuerpo ilegible no falla`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"La solicitud no tiene un formato válido."}"""))
        servidor.enqueue(json(400, "esto no es json"))
        servidor.enqueue(MockResponse().setResponseCode(400))

        assertEquals(
            ResultadoLogin.DatosInvalidos("La solicitud no tiene un formato válido.", emptyMap()),
            repositorio.iniciarSesion("a@appresso.test", "x"),
        )
        assertEquals(ResultadoLogin.DatosInvalidos(null, emptyMap()), repositorio.iniciarSesion("a@appresso.test", "x"))
        assertEquals(ResultadoLogin.DatosInvalidos(null, emptyMap()), repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `401 son credenciales invalidas sin importar el cuerpo`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Credenciales inválidas"}"""))
        servidor.enqueue(MockResponse().setResponseCode(401))

        assertEquals(ResultadoLogin.CredencialesInvalidas, repositorio.iniciarSesion("a@appresso.test", "mala"))
        assertEquals(ResultadoLogin.CredencialesInvalidas, repositorio.iniciarSesion("a@appresso.test", "mala"))
    }

    @Test
    fun `423 devuelve el mensaje de bloqueo del servidor`() = runTest {
        val mensaje = "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos."
        servidor.enqueue(json(423, """{"mensaje":"$mensaje"}"""))

        assertEquals(ResultadoLogin.CuentaBloqueada(mensaje), repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `423 sin cuerpo sigue siendo una cuenta bloqueada`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(423))

        assertEquals(ResultadoLogin.CuentaBloqueada(null), repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    // ---------------------------------------------------------------- otros errores del servidor

    @Test
    fun `los errores 5xx y los codigos inesperados son un error del servidor`() = runTest {
        listOf(500, 502, 503, 404, 403, 418).forEach { codigo ->
            servidor.enqueue(json(codigo, """{"mensaje":"algo"}"""))

            assertEquals("codigo $codigo", ResultadoLogin.ErrorDelServidor, repositorio.iniciarSesion("a@appresso.test", "x"))
        }
    }

    // ---------------------------------------------------------------- sin conexion

    @Test
    fun `con el servidor apagado es sin conexion`() = runTest {
        servidor.shutdown()

        assertEquals(ResultadoLogin.SinConexion, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `si el servidor no responde a tiempo es sin conexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val rapido = repositorioApuntandoA(servidor, tiempoLimiteSegundos = 1)

        assertEquals(ResultadoLogin.SinConexion, rapido.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `si la conexion se corta a mitad de la respuesta es sin conexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(ResultadoLogin.SinConexion, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    @Test
    fun `si el servidor cierra la conexion apenas se abre es sin conexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(ResultadoLogin.SinConexion, repositorio.iniciarSesion("a@appresso.test", "x"))
    }

    /**
     * No se prueba el comportamiento (OkHttp solo reintenta en casos puntuales, como una conexion
     * reutilizada que el servidor cerro, dificiles de reproducir con un servidor simulado) sino la
     * configuracion: que nadie vuelva a activar el reintento automatico sin darse cuenta.
     */
    @Test
    fun `el cliente HTTP no reintenta por su cuenta y respeta el tiempo limite`() {
        val porDefecto = ClienteHttp.crearCliente()
        val corto = ClienteHttp.crearCliente(tiempoLimiteSegundos = 3)

        assertFalse(porDefecto.retryOnConnectionFailure)
        assertEquals(ClienteHttp.TIEMPO_LIMITE_SEGUNDOS * 1000, porDefecto.connectTimeoutMillis.toLong())
        assertEquals(ClienteHttp.TIEMPO_LIMITE_SEGUNDOS * 1000, porDefecto.readTimeoutMillis.toLong())
        assertEquals(3000, corto.connectTimeoutMillis)
        assertEquals(3000, corto.readTimeoutMillis)
        assertEquals(3000, corto.writeTimeoutMillis)
    }

    // ---------------------------------------------------------------- datos sensibles

    @Test
    fun `ni la sesion ni las solicitudes muestran secretos al convertirlas a texto`() {
        val sesion = Sesion("token-secreto-123", Rol.MESERO, "Ana")
        val solicitud = com.appresso.app.data.remote.LoginRequest("ana@appresso.test", "clave-secreta-456")
        val respuesta = com.appresso.app.data.remote.LoginResponse("token-secreto-123", Rol.MESERO, "Ana")

        listOf(sesion.toString(), solicitud.toString(), respuesta.toString(), ResultadoLogin.Exito(sesion).toString())
            .forEach { texto ->
                assertFalse(texto, texto.contains("token-secreto-123"))
                assertFalse(texto, texto.contains("clave-secreta-456"))
            }
        assertNotNull(sesion.nombre)
    }

    // ---------------------------------------------------------------- apoyo

    private fun repositorioApuntandoA(servidor: MockWebServer, tiempoLimiteSegundos: Long = 5): AuthRepository =
        AuthRepositoryRemoto(ClienteHttp.crearAuthApi(servidor.url("/").toString(), tiempoLimiteSegundos))

    private fun json(codigo: Int, cuerpo: String) = MockResponse()
        .setResponseCode(codigo)
        .setHeader("Content-Type", "application/json")
        .setBody(cuerpo)
}
