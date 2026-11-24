package com.appresso.app.data.cuenta

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.CuentaApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * El repositorio de la cuenta del cliente contra un servidor simulado que responde como el backend
 * (docs/api-clientes.md y docs/api-auth.md).
 */
class CuentaClienteRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var repositorio: CuentaClienteRepository

    private val datos = DatosRegistro(
        nombre = "Ana",
        apellido = "Pérez",
        documento = "1234567890",
        celular = "3001234567",
        correo = "ana@correo.test",
        contrasena = "Clave-Cliente-1",
        aceptoTratamientoDatos = true,
    )

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        repositorio = CuentaClienteRepositoryRemoto(
            ClienteHttp.crearRetrofit(servidor.url("/").toString(), tiempoLimiteSegundos = 2).create(CuentaApi::class.java),
        )
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    @Test
    fun `el registro es un POST a api auth registro con todos los campos`() = runTest {
        servidor.enqueue(json(201, """{"mensaje":"Registro exitoso.","nombre":"Ana","puntos":0}"""))

        val resultado = repositorio.registrar(datos)

        assertEquals(ResultadoCuenta.Exito("Registro exitoso."), resultado)
        val solicitud = servidor.takeRequest()
        assertEquals("POST", solicitud.method)
        assertEquals("/api/auth/registro", solicitud.path)
        assertNull("las rutas públicas no llevan token", solicitud.getHeader("Authorization"))
        val cuerpo = Json.parseToJsonElement(solicitud.body.readUtf8()).jsonObject
        assertEquals(
            setOf("nombre", "apellido", "documento", "celular", "correo", "contrasena", "aceptoTratamientoDatos"),
            cuerpo.keys,
        )
        assertEquals("1234567890", cuerpo["documento"]?.jsonPrimitive?.content)
        assertEquals(true, cuerpo["aceptoTratamientoDatos"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `un 409 trae el mensaje y el campo duplicado`() = runTest {
        servidor.enqueue(
            json(409, """{"mensaje":"Este documento ya está registrado.","campos":{"documento":"Este documento ya está registrado."}}"""),
        )

        val resultado = repositorio.registrar(datos)

        assertEquals(
            ResultadoCuenta.Duplicado(
                "Este documento ya está registrado.",
                mapOf("documento" to "Este documento ya está registrado."),
            ),
            resultado,
        )
    }

    @Test
    fun `un 400 trae el mensaje y los campos`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Ingresa un correo electrónico válido.","campos":{"correo":"Ingresa un correo electrónico válido."}}"""))

        val resultado = repositorio.registrar(datos)

        assertEquals(
            ResultadoCuenta.DatosInvalidos(
                "Ingresa un correo electrónico válido.",
                mapOf("correo" to "Ingresa un correo electrónico válido."),
            ),
            resultado,
        )
    }

    @Test
    fun `pedir el codigo envia el documento a api auth recuperar`() = runTest {
        servidor.enqueue(json(200, """{"mensaje":"Si el documento está registrado, enviamos un código al correo asociado."}"""))

        val resultado = repositorio.solicitarCodigo("1234567890")

        assertEquals(ResultadoCuenta.Exito("Si el documento está registrado, enviamos un código al correo asociado."), resultado)
        val solicitud = servidor.takeRequest()
        assertEquals("/api/auth/recuperar", solicitud.path)
        assertEquals("""{"documento":"1234567890"}""", solicitud.body.readUtf8())
    }

    @Test
    fun `restablecer envia documento codigo y contrasena nueva`() = runTest {
        servidor.enqueue(json(200, """{"mensaje":"Tu contraseña se actualizó. Ya puedes iniciar sesión."}"""))

        repositorio.restablecer("1234567890", "123456", "Clave-Nueva-1")

        val solicitud = servidor.takeRequest()
        assertEquals("/api/auth/restablecer", solicitud.path)
        val cuerpo = Json.parseToJsonElement(solicitud.body.readUtf8()).jsonObject
        assertEquals("123456", cuerpo["codigo"]?.jsonPrimitive?.content)
        assertEquals("Clave-Nueva-1", cuerpo["contrasenaNueva"]?.jsonPrimitive?.content)
    }

    @Test
    fun `un codigo invalido es un 400 con el mensaje del servidor`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"El código no es válido o venció. Solicita uno nuevo.","campos":{"codigo":"x"}}"""))

        val resultado = repositorio.restablecer("1234567890", "000000", "Clave-Nueva-1")

        assertEquals("El código no es válido o venció. Solicita uno nuevo.", (resultado as ResultadoCuenta.DatosInvalidos).mensaje)
    }

    @Test
    fun `un 500 o un cuerpo ilegible es error del servidor`() = runTest {
        servidor.enqueue(json(500, """{"mensaje":"x"}"""))
        assertEquals(ResultadoCuenta.ErrorDelServidor, repositorio.solicitarCodigo("1234567890"))

        servidor.enqueue(json(200, "no es json"))
        assertEquals(ResultadoCuenta.ErrorDelServidor, repositorio.solicitarCodigo("1234567890"))
    }

    @Test
    fun `sin servidor es sin conexion`() = runTest {
        servidor.shutdown()

        assertEquals(ResultadoCuenta.SinConexion, repositorio.registrar(datos))
    }

    private fun json(codigo: Int, cuerpo: String) = MockResponse()
        .setResponseCode(codigo)
        .setHeader("Content-Type", "application/json")
        .setBody(cuerpo)
}
