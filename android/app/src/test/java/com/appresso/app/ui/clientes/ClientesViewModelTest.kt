package com.appresso.app.ui.clientes

import com.appresso.app.data.clientes.Cliente
import com.appresso.app.data.clientes.ClienteCrearRequest
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ResultadoClientes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientesViewModelTest {

    private val cliente1 = Cliente(
        id = 1,
        nombre = "Carlos",
        apellido = "Mora",
        documento = "100200300",
        celular = "3001112233",
        puntos = 5,
        aceptoTratamientoDatos = true,
        fechaAceptacionDatos = "2026-11-17T20:00:00Z",
    )

    private val repo = RepositorioClientesFalso()
    private lateinit var viewModel: ClientesViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.respuestaListar = ResultadoClientes.Exito(listOf(cliente1))
        viewModel = ClientesViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    @Test
    fun `al iniciar carga la lista de clientes`() {
        val state = viewModel.uiState.value
        assertFalse(state.cargando)
        assertEquals(1, state.clientes.size)
        assertEquals("Carlos", state.clientes[0].nombre)
    }

    @Test
    fun `registro exige aceptacion explicita de ley 1581 - rechaza si es falso sin llamar al repositorio`() {
        viewModel.registrarCliente(
            nombre = "Laura",
            apellido = "Perez",
            documento = "987654",
            celular = "3112223344",
            aceptoTratamientoDatos = false,
        )

        val state = viewModel.uiState.value
        assertNotNull(state.errorFormulario)
        assertTrue(state.errorFormulario!!.contains("Ley 1581"))
        assertNull(repo.ultimoRegistrado)
    }

    @Test
    fun `registro con aceptacion explicita llama al repositorio y registra exitosamente`() {
        val nuevoCliente = Cliente(
            id = 2,
            nombre = "Laura",
            apellido = "Perez",
            documento = "987654",
            celular = "3112223344",
            aceptoTratamientoDatos = true,
            fechaAceptacionDatos = "2026-11-17T21:00:00Z",
        )
        repo.respuestaRegistrar = ResultadoClientes.Exito(nuevoCliente)
        repo.respuestaListar = ResultadoClientes.Exito(listOf(cliente1, nuevoCliente))

        viewModel.registrarCliente(
            nombre = "Laura",
            apellido = "Perez",
            documento = "987654",
            celular = "3112223344",
            aceptoTratamientoDatos = true,
        )

        val state = viewModel.uiState.value
        assertNull(state.errorFormulario)
        assertFalse(state.mostrarDialogoRegistro)
        assertEquals("Cliente registrado con consentimiento Ley 1581.", state.mensajeExito)
        assertNotNull(repo.ultimoRegistrado)
        assertTrue(repo.ultimoRegistrado!!.aceptoTratamientoDatos)
    }

    @Test
    fun `confirmar eliminacion de datos personales llama al repositorio`() {
        repo.respuestaEliminar = ResultadoClientes.Exito(Unit)

        viewModel.confirmarEliminacionDatos(cliente1, "Revocatoria de consentimiento")

        val state = viewModel.uiState.value
        assertEquals("Datos personales suprimidos conforme a la Ley 1581.", state.mensajeExito)
        assertEquals(1L, repo.ultimoEliminadoId)
        assertEquals("Revocatoria de consentimiento", repo.ultimoMotivoEliminacion)
    }

    @Test
    fun `busqueda actualiza estado correctamente`() {
        viewModel.onBusquedaCambio("100200")
        assertEquals("100200", viewModel.uiState.value.busqueda)
    }

    private class RepositorioClientesFalso : ClientesRepository {
        var respuestaListar: ResultadoClientes<List<Cliente>> = ResultadoClientes.Exito(emptyList())
        var respuestaRegistrar: ResultadoClientes<Cliente>? = null
        var respuestaEliminar: ResultadoClientes<Unit> = ResultadoClientes.Exito(Unit)

        var ultimoRegistrado: ClienteCrearRequest? = null
        var ultimoEliminadoId: Long? = null
        var ultimoMotivoEliminacion: String? = null

        override suspend fun listar(): ResultadoClientes<List<Cliente>> = respuestaListar

        override suspend fun registrar(solicitud: ClienteCrearRequest): ResultadoClientes<Cliente> {
            ultimoRegistrado = solicitud
            return respuestaRegistrar ?: ResultadoClientes.ErrorDelServidor
        }

        override suspend fun buscarPorDocumento(documento: String): ResultadoClientes<Cliente> =
            ResultadoClientes.ErrorDelServidor

        override suspend fun eliminar(id: Long): ResultadoClientes<Unit> {
            ultimoEliminadoId = id
            return respuestaEliminar
        }

        override suspend fun solicitarEliminacion(id: Long, motivo: String?): ResultadoClientes<Unit> {
            ultimoEliminadoId = id
            ultimoMotivoEliminacion = motivo
            return respuestaEliminar
        }
    }
}
