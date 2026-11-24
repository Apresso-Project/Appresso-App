package com.appresso.app.ui.cuenta

import com.appresso.app.data.cuenta.MisDatosRepository
import com.appresso.app.data.cuenta.ResultadoEliminacion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MisDatosViewModelTest {

    private val repositorio = RepositorioFalso()
    private lateinit var viewModel: MisDatosViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = MisDatosViewModel(repositorio)
    }

    @After
    fun detener() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin contrasena no se pide confirmar`() {
        viewModel.pedirEliminacion()

        assertFalse(viewModel.estado.value.confirmando)
        assertEquals(ErrorMisDatos.ContrasenaVacia, viewModel.estado.value.error)
    }

    @Test
    fun `nada se elimina sin la ultima confirmacion`() {
        viewModel.onContrasenaChange("Clave-1234")

        viewModel.confirmar()
        viewModel.pedirEliminacion()
        assertTrue(viewModel.estado.value.confirmando)
        viewModel.cancelar()
        viewModel.confirmar()

        assertTrue(repositorio.llamadas.isEmpty())
    }

    @Test
    fun `al confirmar se elimina y se descarta la contrasena`() {
        eliminar("Clave-1234")

        assertEquals(listOf("Clave-1234"), repositorio.llamadas)
        assertEquals(MisDatosUiState(eliminado = true), viewModel.estado.value)
    }

    @Test
    fun `una contrasena incorrecta muestra el mensaje del servidor y la borra`() {
        repositorio.respuesta = CompletableDeferred(ResultadoEliminacion.Rechazado("La contraseña no es correcta."))

        eliminar("otra-clave")

        val estado = viewModel.estado.value
        assertEquals(ErrorMisDatos.DelServidor("La contraseña no es correcta."), estado.error)
        assertEquals("", estado.contrasena)
        assertFalse(estado.eliminado)
    }

    @Test
    fun `sin conexion o con error del servidor se conserva lo escrito`() {
        repositorio.respuesta = CompletableDeferred(ResultadoEliminacion.SinConexion)
        eliminar("Clave-1234")
        assertEquals(ErrorMisDatos.SinConexion, viewModel.estado.value.error)
        assertEquals("Clave-1234", viewModel.estado.value.contrasena)

        repositorio.respuesta = CompletableDeferred(ResultadoEliminacion.ErrorDelServidor)
        eliminar("Clave-1234")
        assertEquals(ErrorMisDatos.ErrorDelServidor, viewModel.estado.value.error)
    }

    @Test
    fun `mientras espera no se envia dos veces ni se puede editar`() {
        repositorio.respuesta = CompletableDeferred()
        eliminar("Clave-1234")

        viewModel.pedirEliminacion()
        viewModel.confirmar()
        viewModel.onContrasenaChange("x")
        viewModel.limpiar()

        assertEquals(1, repositorio.llamadas.size)
        assertTrue(viewModel.estado.value.cargando)
        assertEquals("Clave-1234", viewModel.estado.value.contrasena)
    }

    @Test
    fun `al salir no queda la contrasena`() {
        viewModel.onContrasenaChange("Clave-1234")

        viewModel.limpiar()

        assertEquals(MisDatosUiState(), viewModel.estado.value)
    }

    private fun eliminar(contrasena: String) {
        viewModel.onContrasenaChange(contrasena)
        viewModel.pedirEliminacion()
        viewModel.confirmar()
    }

    private class RepositorioFalso : MisDatosRepository {
        val llamadas = mutableListOf<String>()
        var respuesta = CompletableDeferred<ResultadoEliminacion>(ResultadoEliminacion.Eliminado)

        override suspend fun eliminar(contrasena: String): ResultadoEliminacion {
            llamadas += contrasena
            return respuesta.await()
        }
    }
}
