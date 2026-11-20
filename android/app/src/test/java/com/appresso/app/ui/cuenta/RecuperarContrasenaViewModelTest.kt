package com.appresso.app.ui.cuenta

import com.appresso.app.data.cuenta.CuentaClienteRepository
import com.appresso.app.data.cuenta.DatosRegistro
import com.appresso.app.data.cuenta.ResultadoCuenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecuperarContrasenaViewModelTest {

    private val repositorio = RepositorioFalso()
    private lateinit var viewModel: RecuperarContrasenaViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = RecuperarContrasenaViewModel(repositorio)
    }

    @After
    fun detener() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pedir el codigo pasa al paso del codigo con el aviso del servidor`() {
        repositorio.resultado = ResultadoCuenta.Exito("Si el documento está registrado, enviamos un código.")
        viewModel.onDocumentoChange(" 1234567890 ")

        viewModel.enviarCodigo()

        assertEquals(listOf("solicitar 1234567890"), repositorio.llamadas)
        assertEquals(PasoRecuperacion.CODIGO, viewModel.estado.value.paso)
        assertEquals("Si el documento está registrado, enviamos un código.", viewModel.estado.value.aviso)
    }

    @Test
    fun `un documento invalido no llega al servidor`() {
        viewModel.onDocumentoChange("12")

        viewModel.enviarCodigo()

        assertTrue(repositorio.llamadas.isEmpty())
        assertEquals(ErrorRecuperacion.DocumentoInvalido, viewModel.estado.value.error)
    }

    @Test
    fun `el codigo solo admite seis digitos`() {
        viewModel.onCodigoChange("12a3-45678")

        assertEquals("123456", viewModel.estado.value.codigo)
    }

    @Test
    fun `se valida el codigo la longitud y la confirmacion antes de enviar`() {
        irAlPasoDelCodigo()

        viewModel.onCodigoChange("123")
        viewModel.cambiarContrasena()
        assertEquals(ErrorRecuperacion.CodigoIncompleto, viewModel.estado.value.error)

        viewModel.onCodigoChange("123456")
        viewModel.onContrasenaChange("corta")
        viewModel.cambiarContrasena()
        assertEquals(ErrorRecuperacion.ContrasenaInvalida, viewModel.estado.value.error)

        viewModel.onContrasenaChange("Clave-Nueva-1")
        viewModel.onConfirmacionChange("Clave-Nueva-2")
        viewModel.cambiarContrasena()
        assertEquals(ErrorRecuperacion.NoCoinciden, viewModel.estado.value.error)

        assertEquals(listOf("solicitar 1234567890"), repositorio.llamadas)
    }

    @Test
    fun `con el codigo correcto termina y descarta las contrasenas`() {
        irAlPasoDelCodigo()
        repositorio.resultado = ResultadoCuenta.Exito("Tu contraseña se actualizó.")
        viewModel.onCodigoChange("123456")
        viewModel.onContrasenaChange("Clave-Nueva-1")
        viewModel.onConfirmacionChange("Clave-Nueva-1")

        viewModel.cambiarContrasena()

        assertEquals("restablecer 1234567890 123456", repositorio.llamadas.last())
        val estado = viewModel.estado.value
        assertEquals(PasoRecuperacion.LISTO, estado.paso)
        assertEquals("Tu contraseña se actualizó.", estado.mensajeExito)
        assertEquals("", estado.contrasena)
        assertEquals("", estado.confirmacion)
    }

    @Test
    fun `un codigo rechazado muestra el mensaje del servidor y sigue en el paso del codigo`() {
        irAlPasoDelCodigo()
        repositorio.resultado = ResultadoCuenta.DatosInvalidos("El código no es válido o venció. Solicita uno nuevo.", emptyMap())
        viewModel.onCodigoChange("000000")
        viewModel.onContrasenaChange("Clave-Nueva-1")
        viewModel.onConfirmacionChange("Clave-Nueva-1")

        viewModel.cambiarContrasena()

        assertEquals(
            ErrorRecuperacion.DelServidor("El código no es válido o venció. Solicita uno nuevo."),
            viewModel.estado.value.error,
        )
        assertEquals(PasoRecuperacion.CODIGO, viewModel.estado.value.paso)
    }

    @Test
    fun `volver al documento permite corregirlo`() {
        irAlPasoDelCodigo()

        viewModel.volverAlDocumento()

        assertEquals(PasoRecuperacion.DOCUMENTO, viewModel.estado.value.paso)
        assertEquals("1234567890", viewModel.estado.value.documento)
    }

    private fun irAlPasoDelCodigo() {
        repositorio.resultado = ResultadoCuenta.Exito("aviso")
        viewModel.onDocumentoChange("1234567890")
        viewModel.enviarCodigo()
    }

    private class RepositorioFalso : CuentaClienteRepository {
        val llamadas = mutableListOf<String>()
        var resultado: ResultadoCuenta = ResultadoCuenta.ErrorDelServidor

        override suspend fun registrar(datos: DatosRegistro) = resultado

        override suspend fun solicitarCodigo(documento: String): ResultadoCuenta {
            llamadas += "solicitar $documento"
            return resultado
        }

        override suspend fun restablecer(documento: String, codigo: String, contrasenaNueva: String): ResultadoCuenta {
            llamadas += "restablecer $documento $codigo"
            return resultado
        }
    }
}
