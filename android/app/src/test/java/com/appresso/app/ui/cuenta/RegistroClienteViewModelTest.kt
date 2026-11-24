package com.appresso.app.ui.cuenta

import com.appresso.app.data.cuenta.CuentaClienteRepository
import com.appresso.app.data.cuenta.DatosRegistro
import com.appresso.app.data.cuenta.ResultadoCuenta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistroClienteViewModelTest {

    private val repositorio = RepositorioFalso()
    private lateinit var viewModel: RegistroClienteViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = RegistroClienteViewModel(repositorio)
    }

    @After
    fun detener() {
        Dispatchers.resetMain()
    }

    @Test
    fun `un registro completo se envia con los datos sin espacios sobrantes`() {
        repositorio.resultado = ResultadoCuenta.Exito("Registro exitoso.")
        llenarTodo()

        viewModel.enviar()

        val enviado = repositorio.registros.single()
        assertEquals("Ana", enviado.nombre)
        assertEquals("1234567890", enviado.documento)
        assertEquals("ana@correo.test", enviado.correo)
        assertTrue(enviado.aceptoTratamientoDatos)
        val estado = viewModel.estado.value
        assertTrue(estado.registrado)
        assertEquals("Registro exitoso.", estado.mensajeExito)
        assertEquals("", estado.contrasena)
    }

    @Test
    fun `sin aceptar el tratamiento de datos no se envia y el error es incompleto`() {
        llenarTodo()
        viewModel.onAceptoChange(false)

        viewModel.enviar()

        assertTrue(repositorio.registros.isEmpty())
        assertEquals(ErrorRegistro.Incompleto, viewModel.estado.value.error)
        assertEquals(ProblemaCampo.FALTA, viewModel.estado.value.problemas[CampoRegistro.ACEPTO])
    }

    @Test
    fun `los campos vacios se marcan como faltantes`() {
        viewModel.enviar()

        val problemas = viewModel.estado.value.problemas
        assertEquals(CampoRegistro.entries.toSet(), problemas.keys)
        assertTrue(problemas.values.all { it == ProblemaCampo.FALTA })
        assertEquals(ErrorRegistro.Incompleto, viewModel.estado.value.error)
    }

    @Test
    fun `si todo esta lleno el error es el primer formato invalido del formulario`() {
        llenarTodo()
        viewModel.onCorreoChange("ana-sin-arroba")
        viewModel.onContrasenaChange("corta")

        viewModel.enviar()

        assertTrue(repositorio.registros.isEmpty())
        assertEquals(ErrorRegistro.Formato(CampoRegistro.CORREO), viewModel.estado.value.error)
        assertEquals(ProblemaCampo.FORMATO, viewModel.estado.value.problemas[CampoRegistro.CONTRASENA])
    }

    @Test
    fun `una contrasena de mas de 72 bytes se rechaza aunque tenga menos de 72 caracteres`() {
        llenarTodo()
        viewModel.onContrasenaChange("ñ".repeat(40))

        viewModel.enviar()

        assertEquals(ErrorRegistro.Formato(CampoRegistro.CONTRASENA), viewModel.estado.value.error)
    }

    @Test
    fun `un documento duplicado muestra el mensaje del servidor y marca el campo`() {
        repositorio.resultado = ResultadoCuenta.Duplicado(
            "Este documento ya está registrado.",
            mapOf("documento" to "Este documento ya está registrado."),
        )
        llenarTodo()

        viewModel.enviar()

        val estado = viewModel.estado.value
        assertEquals(ErrorRegistro.DelServidor("Este documento ya está registrado."), estado.error)
        assertEquals("Este documento ya está registrado.", estado.erroresServidor[CampoRegistro.DOCUMENTO])
        assertFalse(estado.registrado)
        assertEquals("Clave-Cliente-1", estado.contrasena) // conserva lo escrito para corregir
    }

    @Test
    fun `editar un campo borra su error`() {
        repositorio.resultado = ResultadoCuenta.Duplicado("x", mapOf("correo" to "Este correo ya está registrado."))
        llenarTodo()
        viewModel.enviar()

        viewModel.onCorreoChange("otra@correo.test")

        assertNull(viewModel.estado.value.erroresServidor[CampoRegistro.CORREO])
        assertNull(viewModel.estado.value.error)
    }

    @Test
    fun `mientras espera no envia dos veces ni deja editar`() {
        val espera = CompletableDeferred<ResultadoCuenta>()
        repositorio.espera = espera
        llenarTodo()

        viewModel.enviar()
        viewModel.enviar()
        viewModel.onNombreChange("Otra")

        assertEquals(1, repositorio.registros.size)
        assertEquals(" Ana ", viewModel.estado.value.nombre) // no cambió mientras esperaba
        espera.complete(ResultadoCuenta.SinConexion)
        assertEquals(ErrorRegistro.SinConexion, viewModel.estado.value.error)
        assertFalse(viewModel.estado.value.cargando)
    }

    private fun llenarTodo() {
        viewModel.onNombreChange(" Ana ")
        viewModel.onApellidoChange("Pérez")
        viewModel.onDocumentoChange("1234567890")
        viewModel.onCelularChange("300 123 4567")
        viewModel.onCorreoChange("ana@correo.test ")
        viewModel.onContrasenaChange("Clave-Cliente-1")
        viewModel.onAceptoChange(true)
    }

    private class RepositorioFalso : CuentaClienteRepository {
        val registros = mutableListOf<DatosRegistro>()
        var resultado: ResultadoCuenta = ResultadoCuenta.ErrorDelServidor
        var espera: CompletableDeferred<ResultadoCuenta>? = null

        override suspend fun registrar(datos: DatosRegistro): ResultadoCuenta {
            registros += datos
            return espera?.await() ?: resultado
        }

        override suspend fun solicitarCodigo(documento: String) = resultado
        override suspend fun restablecer(documento: String, codigo: String, contrasenaNueva: String) = resultado
    }
}
