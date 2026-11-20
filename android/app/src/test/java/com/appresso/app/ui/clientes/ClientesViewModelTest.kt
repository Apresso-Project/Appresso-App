package com.appresso.app.ui.clientes

import com.appresso.app.data.clientes.Cliente
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ResultadoBusqueda
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
class ClientesViewModelTest {

    private val repositorio = RepositorioFalso()
    private lateinit var viewModel: ClientesViewModel

    private val ana = Cliente(1, "Ana", "Pérez", "1234567890", 3)

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ClientesViewModel(repositorio)
    }

    @After
    fun detener() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buscar pregunta al servidor por el documento normalizado y muestra al cliente`() {
        repositorio.resultado = ResultadoBusqueda.Encontrado(ana)
        viewModel.onDocumentoChange(" 1.234.567.890 ")

        viewModel.buscar()

        assertEquals(listOf("1234567890"), repositorio.buscados)
        assertEquals(ResultadoVista.Encontrado(ana), viewModel.estado.value.resultado)
        assertFalse(viewModel.estado.value.buscando)
    }

    @Test
    fun `un documento no registrado muestra cliente no encontrado`() {
        repositorio.resultado = ResultadoBusqueda.NoEncontrado
        viewModel.onDocumentoChange("5555555555")

        viewModel.buscar()

        assertEquals(ResultadoVista.NoEncontrado, viewModel.estado.value.resultado)
        assertNull(viewModel.estado.value.error)
    }

    @Test
    fun `un documento con formato invalido no llega al servidor`() {
        viewModel.onDocumentoChange("12")

        viewModel.buscar()

        assertTrue(repositorio.buscados.isEmpty())
        assertEquals(ErrorBusqueda.DocumentoInvalido, viewModel.estado.value.error)
    }

    @Test
    fun `los errores del servidor se muestran como error y no como resultado`() {
        viewModel.onDocumentoChange("1234567890")

        repositorio.resultado = ResultadoBusqueda.SinPermiso("No tienes permiso para realizar esta acción.")
        viewModel.buscar()
        assertEquals(ErrorBusqueda.SinPermiso("No tienes permiso para realizar esta acción."), viewModel.estado.value.error)
        assertNull(viewModel.estado.value.resultado)

        repositorio.resultado = ResultadoBusqueda.SinConexion
        viewModel.buscar()
        assertEquals(ErrorBusqueda.SinConexion, viewModel.estado.value.error)
    }

    @Test
    fun `cambiar el documento borra el resultado anterior`() {
        repositorio.resultado = ResultadoBusqueda.Encontrado(ana)
        viewModel.onDocumentoChange("1234567890")
        viewModel.buscar()

        viewModel.onDocumentoChange("9876543210")

        assertNull(viewModel.estado.value.resultado)
    }

    private class RepositorioFalso : ClientesRepository {
        val buscados = mutableListOf<String>()
        var resultado: ResultadoBusqueda = ResultadoBusqueda.ErrorDelServidor

        override suspend fun buscarPorDocumento(documento: String): ResultadoBusqueda {
            buscados += documento
            return resultado
        }
    }
}
