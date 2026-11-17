package com.appresso.app.data.usuarios

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.UsuariosApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cada caso del contrato (docs/api-usuarios.md) contra un servidor HTTP simulado. */
class UsuariosRepositoryRemotoTest {

    private lateinit var servidor: MockWebServer
    private lateinit var almacen: AlmacenSesionEnMemoria
    private lateinit var repositorio: UsuariosRepository

    private val ana = Usuario(5, "Ana", "ana@appresso.test", Rol.MESERO, true)
    private val anaJson =
        """{"id":5,"nombre":"Ana","correo":"ana@appresso.test","rol":"MESERO","activo":true,"fechaCreacion":"2026-11-01T10:00:00Z"}"""

    @Before
    fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        almacen = AlmacenSesionEnMemoria().also { it.guardar(Sesion("tok.en.x", Rol.ADMINISTRADOR, "Admin")) }
        // El mismo cliente que usa la app: agrega el token y cierra la sesión ante un 401.
        val retrofit = ClienteHttp.crearRetrofit(servidor.url("/").toString(), almacen, tiempoLimiteSegundos = 2)
        repositorio = UsuariosRepositoryRemoto(retrofit.create(UsuariosApi::class.java))
    }

    @After
    fun detener() {
        runCatching { servidor.shutdown() }
    }

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo)

    @Test
    fun `listar manda el token Bearer y devuelve los usuarios`() = runTest {
        servidor.enqueue(json(200, "[$anaJson]"))

        val r = repositorio.listar()

        assertEquals(ResultadoUsuarios.Exito(listOf(ana)), r)
        val pedido = servidor.takeRequest()
        assertEquals("GET", pedido.method)
        assertEquals("/api/usuarios", pedido.path)
        assertEquals("Bearer tok.en.x", pedido.getHeader("Authorization"))
    }

    @Test
    fun `sin sesion la peticion sale sin token y el 401 se traduce a SesionInvalida`() = runTest {
        almacen.borrar()
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró. Inicia sesión de nuevo."}"""))

        assertEquals(ResultadoUsuarios.SesionInvalida, repositorio.listar())
        assertEquals(null, servidor.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `un 401 con sesion cierra la sesion`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró. Inicia sesión de nuevo."}"""))

        assertEquals(ResultadoUsuarios.SesionInvalida, repositorio.listar())
        assertEquals(null, almacen.sesion.value)
    }

    @Test
    fun `crear envia los datos y devuelve el usuario creado`() = runTest {
        servidor.enqueue(json(201, anaJson))

        val r = repositorio.crear(DatosUsuario("Ana", "ana@appresso.test", "clave-segura", Rol.MESERO))

        assertEquals(ResultadoUsuarios.Exito(ana), r)
        val pedido = servidor.takeRequest()
        assertEquals("POST", pedido.method)
        val cuerpo = Json.parseToJsonElement(pedido.body.readUtf8()).jsonObject
        assertEquals("Ana", cuerpo["nombre"]!!.jsonPrimitive.content)
        assertEquals("clave-segura", cuerpo["contrasena"]!!.jsonPrimitive.content)
        assertEquals("MESERO", cuerpo["rol"]!!.jsonPrimitive.content)
    }

    @Test
    fun `editar sin contrasena no la envia`() = runTest {
        servidor.enqueue(json(200, anaJson))

        repositorio.editar(5, DatosUsuario("Ana", "ana@appresso.test", null, Rol.CAJERO))

        val pedido = servidor.takeRequest()
        assertEquals("PUT", pedido.method)
        assertEquals("/api/usuarios/5", pedido.path)
        val cuerpo = Json.parseToJsonElement(pedido.body.readUtf8()).jsonObject
        assertFalse(cuerpo.containsKey("contrasena"))
        assertEquals("CAJERO", cuerpo["rol"]!!.jsonPrimitive.content)
    }

    @Test
    fun `desactivar y activar usan sus rutas`() = runTest {
        servidor.enqueue(json(200, anaJson.replace("true", "false")))
        servidor.enqueue(json(200, anaJson))

        val desactivado = repositorio.desactivar(5) as ResultadoUsuarios.Exito
        val activado = repositorio.activar(5) as ResultadoUsuarios.Exito

        assertFalse(desactivado.valor.activo)
        assertTrue(activado.valor.activo)
        assertEquals("/api/usuarios/5/desactivar", servidor.takeRequest().path)
        assertEquals("/api/usuarios/5/activar", servidor.takeRequest().path)
    }

    @Test
    fun `400 devuelve los errores por campo`() = runTest {
        servidor.enqueue(json(400, """{"mensaje":"Revisa los datos ingresados.","campos":{"correo":"no es válido"}}"""))

        val r = repositorio.crear(DatosUsuario("Ana", "x", "clave-segura", Rol.MESERO))

        assertEquals(ResultadoUsuarios.DatosInvalidos("Revisa los datos ingresados.", mapOf("correo" to "no es válido")), r)
    }

    @Test
    fun `401 devuelve SesionInvalida`() = runTest {
        servidor.enqueue(json(401, """{"mensaje":"Tu sesión no es válida o expiró. Inicia sesión de nuevo."}"""))

        assertEquals(ResultadoUsuarios.SesionInvalida, repositorio.listar())
    }

    @Test
    fun `403 devuelve el mensaje del servidor`() = runTest {
        servidor.enqueue(json(403, """{"mensaje":"No tienes permiso para realizar esta acción."}"""))

        assertEquals(ResultadoUsuarios.SinPermiso("No tienes permiso para realizar esta acción."), repositorio.listar())
    }

    @Test
    fun `404 devuelve NoEncontrado`() = runTest {
        servidor.enqueue(json(404, """{"mensaje":"No existe"}"""))

        assertEquals(ResultadoUsuarios.NoEncontrado, repositorio.desactivar(99))
    }

    @Test
    fun `409 por correo duplicado trae el campo y 409 de regla solo el mensaje`() = runTest {
        servidor.enqueue(json(409, """{"mensaje":"Ya existe","campos":{"correo":"Ya está registrado"}}"""))
        servidor.enqueue(json(409, """{"mensaje":"No puedes desactivar tu propia cuenta."}"""))

        val duplicado = repositorio.crear(DatosUsuario("Ana", "ana@appresso.test", "clave-segura", Rol.MESERO))
        val regla = repositorio.desactivar(1)

        assertEquals(ResultadoUsuarios.Conflicto("Ya existe", mapOf("correo" to "Ya está registrado")), duplicado)
        assertEquals(ResultadoUsuarios.Conflicto("No puedes desactivar tu propia cuenta.", emptyMap()), regla)
    }

    @Test
    fun `500 y cuerpo ilegible son ErrorDelServidor`() = runTest {
        servidor.enqueue(json(500, "{}"))
        servidor.enqueue(json(200, "esto no es json"))
        servidor.enqueue(json(200, "[" + anaJson.replace("MESERO", "ROL_NUEVO") + "]"))

        assertEquals(ResultadoUsuarios.ErrorDelServidor, repositorio.listar())
        assertEquals(ResultadoUsuarios.ErrorDelServidor, repositorio.listar())
        assertEquals(ResultadoUsuarios.ErrorDelServidor, repositorio.listar())
    }

    @Test
    fun `sin red devuelve SinConexion`() = runTest {
        servidor.shutdown()

        assertEquals(ResultadoUsuarios.SinConexion, repositorio.listar())
    }

    @Test
    fun `conexion cortada devuelve SinConexion`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertEquals(ResultadoUsuarios.SinConexion, repositorio.listar())
    }

    @Test
    fun `los textos de depuracion no incluyen contrasenas ni token`() {
        val datos = DatosUsuario("Ana", "a@a.co", "clave-secreta", Rol.MESERO)
        assertFalse(datos.toString().contains("clave-secreta"))
    }
}
