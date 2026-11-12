package com.appresso.app.ui.login

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.ResultadoLogin
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val repositorio = RepositorioFalso()
    private val almacen = AlmacenSesionEnMemoria()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun iniciar() {
        // viewModelScope usa Dispatchers.Main; con este despachador las coroutines corren de inmediato.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LoginViewModel(repositorio, almacen)
    }

    @After
    fun detener() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- estado y edición

    @Test
    fun `al empezar los campos estan vacios sin error ni carga`() {
        assertEquals(LoginUiState(), viewModel.estado.value)
    }

    @Test
    fun `escribir actualiza los campos`() {
        viewModel.onCorreoChange("ana@appresso.test")
        viewModel.onContrasenaChange("clave")

        assertEquals("ana@appresso.test", viewModel.estado.value.correo)
        assertEquals("clave", viewModel.estado.value.contrasena)
    }

    @Test
    fun `editar un campo despues de un error lo borra`() = runTest {
        escribir("ana@appresso.test", "mala")
        repositorio.resultado = ResultadoLogin.CredencialesInvalidas
        viewModel.enviar()
        assertEquals(ErrorLogin.CredencialesInvalidas, viewModel.estado.value.error)

        viewModel.onContrasenaChange("otra")

        assertNull(viewModel.estado.value.error)
    }

    // ---------------------------------------------------------------- inicio de sesion correcto

    @Test
    fun `un inicio de sesion correcto guarda la sesion y borra la contrasena`() = runTest {
        val sesion = Sesion("token", Rol.MESERO, "Ana")
        repositorio.resultado = ResultadoLogin.Exito(sesion)
        escribir("ana@appresso.test", "clave-1")

        viewModel.enviar()

        assertEquals(sesion, almacen.sesion.value)
        assertEquals("", viewModel.estado.value.contrasena)
        assertFalse(viewModel.estado.value.cargando)
        assertNull(viewModel.estado.value.error)
    }

    @Test
    fun `el correo se envia sin espacios al inicio ni al final`() = runTest {
        repositorio.resultado = ResultadoLogin.CredencialesInvalidas
        escribir("  ana@appresso.test  ", "clave-1")

        viewModel.enviar()

        assertEquals(listOf("ana@appresso.test" to "clave-1"), repositorio.llamadas)
    }

    @Test
    fun `la contrasena se envia tal cual sin recortarla`() = runTest {
        repositorio.resultado = ResultadoLogin.CredencialesInvalidas
        escribir("ana@appresso.test", " con espacios ")

        viewModel.enviar()

        assertEquals(" con espacios ", repositorio.llamadas.single().second)
    }

    // ---------------------------------------------------------------- carga

    @Test
    fun `mientras espera la respuesta esta cargando y despues deja de estarlo`() = runTest {
        val espera = CompletableDeferred<ResultadoLogin>()
        repositorio.espera = espera
        escribir("ana@appresso.test", "clave-1")

        viewModel.enviar()
        assertTrue(viewModel.estado.value.cargando)

        espera.complete(ResultadoLogin.CredencialesInvalidas)
        assertFalse(viewModel.estado.value.cargando)
    }

    @Test
    fun `un segundo envio mientras carga no manda otra solicitud`() = runTest {
        repositorio.espera = CompletableDeferred()
        escribir("ana@appresso.test", "clave-1")

        viewModel.enviar()
        viewModel.enviar()
        viewModel.enviar()

        assertEquals(1, repositorio.llamadas.size)
    }

    @Test
    fun `mientras carga los campos no se pueden editar`() = runTest {
        repositorio.espera = CompletableDeferred()
        escribir("ana@appresso.test", "clave-1")
        viewModel.enviar()

        viewModel.onCorreoChange("otro@appresso.test")
        viewModel.onContrasenaChange("otra")

        assertEquals("ana@appresso.test", viewModel.estado.value.correo)
        assertEquals("clave-1", viewModel.estado.value.contrasena)
    }

    @Test
    fun `al enviar de nuevo el error anterior desaparece mientras se espera`() = runTest {
        escribir("ana@appresso.test", "mala")
        repositorio.resultado = ResultadoLogin.SinConexion
        viewModel.enviar()
        assertEquals(ErrorLogin.SinConexion, viewModel.estado.value.error)

        repositorio.espera = CompletableDeferred()
        viewModel.enviar()

        assertNull(viewModel.estado.value.error)
    }

    // ---------------------------------------------------------------- errores del servidor

    @Test
    fun `credenciales invalidas conservan lo escrito y no guardan sesion`() = runTest {
        repositorio.resultado = ResultadoLogin.CredencialesInvalidas
        escribir("ana@appresso.test", "mala")

        viewModel.enviar()

        val estado = viewModel.estado.value
        assertEquals(ErrorLogin.CredencialesInvalidas, estado.error)
        assertEquals("ana@appresso.test", estado.correo)
        assertEquals("mala", estado.contrasena)
        assertFalse(estado.cargando)
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `una cuenta bloqueada muestra el mensaje del servidor`() = runTest {
        val mensaje = "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos."
        repositorio.resultado = ResultadoLogin.CuentaBloqueada(mensaje)
        escribir("ana@appresso.test", "x")

        viewModel.enviar()

        assertEquals(ErrorLogin.CuentaBloqueada(mensaje), viewModel.estado.value.error)
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `los datos invalidos del servidor traen los problemas de cada campo en orden`() = runTest {
        repositorio.resultado = ResultadoLogin.DatosInvalidos(
            "Revisa los datos ingresados.",
            linkedMapOf("correo" to "El correo no tiene un formato válido", "contrasena" to "La contraseña es obligatoria"),
        )
        escribir("ana@appresso.test", "x")

        viewModel.enviar()

        assertEquals(
            ErrorLogin.DatosInvalidos(
                "Revisa los datos ingresados.",
                listOf("El correo no tiene un formato válido", "La contraseña es obligatoria"),
            ),
            viewModel.estado.value.error,
        )
    }

    @Test
    fun `sin conexion conserva lo escrito y permite reintentar con exito`() = runTest {
        escribir("ana@appresso.test", "clave-1")
        repositorio.resultado = ResultadoLogin.SinConexion
        viewModel.enviar()

        assertEquals(ErrorLogin.SinConexion, viewModel.estado.value.error)
        assertEquals("clave-1", viewModel.estado.value.contrasena)
        assertNull(almacen.sesion.value)

        // Vuelve la red: se reenvia sin volver a escribir nada.
        val sesion = Sesion("token", Rol.CAJERO, "Ana")
        repositorio.resultado = ResultadoLogin.Exito(sesion)
        viewModel.enviar()

        assertEquals(sesion, almacen.sesion.value)
        assertEquals(2, repositorio.llamadas.size)
    }

    @Test
    fun `un error del servidor se muestra y no guarda sesion`() = runTest {
        repositorio.resultado = ResultadoLogin.ErrorDelServidor
        escribir("ana@appresso.test", "clave-1")

        viewModel.enviar()

        assertEquals(ErrorLogin.ErrorDelServidor, viewModel.estado.value.error)
        assertNull(almacen.sesion.value)
    }

    // ---------------------------------------------------------------- validacion en el cliente

    @Test
    fun `con algun campo vacio no se envia nada ni se muestra error`() = runTest {
        escribir("", "clave-1")
        viewModel.enviar()
        escribir("ana@appresso.test", "")
        viewModel.enviar()
        escribir("   ", "clave-1")
        viewModel.enviar()

        assertTrue(repositorio.llamadas.isEmpty())
        assertNull(viewModel.estado.value.error)
    }

    @Test
    fun `un correo sin formato valido no llega al servidor`() = runTest {
        escribir("no-es-un-correo", "clave-1")

        viewModel.enviar()

        assertEquals(ErrorLogin.CorreoInvalido, viewModel.estado.value.error)
        assertTrue(repositorio.llamadas.isEmpty())
        assertFalse(viewModel.estado.value.cargando)
    }

    @Test
    fun `una contrasena de mas de 72 caracteres no llega al servidor`() = runTest {
        escribir("ana@appresso.test", "a".repeat(73))

        viewModel.enviar()

        assertEquals(ErrorLogin.ContrasenaDemasiadoLarga, viewModel.estado.value.error)
        assertTrue(repositorio.llamadas.isEmpty())
    }

    @Test
    fun `una contrasena de exactamente 72 caracteres si se envia`() = runTest {
        repositorio.resultado = ResultadoLogin.CredencialesInvalidas
        escribir("ana@appresso.test", "a".repeat(72))

        viewModel.enviar()

        assertEquals(1, repositorio.llamadas.size)
    }

    @Test
    fun `reglas del formato de correo`() {
        listOf("a@b", "ana@appresso.test", "ana.perez+cafe@appresso.co", "a".repeat(143) + "@x.com")
            .forEach { assertTrue(it, LoginViewModel.esCorreoValido(it)) }
        listOf("sin-arroba", "@x.com", "a@", "a b@c.com", "a@b@c", "", "a".repeat(145) + "@x.com")
            .forEach { assertFalse(it, LoginViewModel.esCorreoValido(it)) }
    }

    // ---------------------------------------------------------------- apoyo

    private fun escribir(correo: String, contrasena: String) {
        viewModel.onCorreoChange(correo)
        viewModel.onContrasenaChange(contrasena)
    }

    /** Repositorio de prueba: registra las llamadas y responde lo que se le indique, o espera si se le pide. */
    private class RepositorioFalso : AuthRepository {
        val llamadas = mutableListOf<Pair<String, String>>()
        var resultado: ResultadoLogin = ResultadoLogin.ErrorDelServidor
        var espera: CompletableDeferred<ResultadoLogin>? = null

        override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin {
            llamadas += correo to contrasena
            return espera?.await() ?: resultado
        }
    }
}
