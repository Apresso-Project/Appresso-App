package com.appresso.app.ui.clientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appresso.app.data.clientes.Cliente
import com.appresso.app.data.clientes.ClienteCrearRequest
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ResultadoClientes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientesUiState(
    val cargando: Boolean = false,
    val clientes: List<Cliente> = emptyList(),
    val busqueda: String = "",
    val mostrarDialogoRegistro: Boolean = false,
    val clienteAEliminar: Cliente? = null,
    val guardando: Boolean = false,
    val errorFormulario: String? = null,
    val mensajeExito: String? = null,
    val errorGeneral: String? = null,
)

class ClientesViewModel(private val repository: ClientesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientesUiState())
    val uiState: StateFlow<ClientesUiState> = _uiState.asStateFlow()

    init {
        cargarClientes()
    }

    fun cargarClientes() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, errorGeneral = null) }
            when (val r = repository.listar()) {
                is ResultadoClientes.Exito -> _uiState.update { it.copy(cargando = false, clientes = r.datos) }
                is ResultadoClientes.SinConexion -> _uiState.update {
                    it.copy(cargando = false, errorGeneral = "Sin conexión con el servidor.")
                }
                is ResultadoClientes.SinPermiso -> _uiState.update {
                    it.copy(cargando = false, errorGeneral = r.mensaje ?: "No tienes permiso para ver clientes.")
                }
                else -> _uiState.update {
                    it.copy(cargando = false, errorGeneral = "No se pudieron cargar los clientes.")
                }
            }
        }
    }

    fun onBusquedaCambio(nueva: String) {
        _uiState.update { it.copy(busqueda = nueva) }
    }

    fun abrirRegistro() {
        _uiState.update { it.copy(mostrarDialogoRegistro = true, errorFormulario = null) }
    }

    fun cerrarRegistro() {
        _uiState.update { it.copy(mostrarDialogoRegistro = false, errorFormulario = null) }
    }

    fun abrirDialogoEliminacion(cliente: Cliente) {
        _uiState.update { it.copy(clienteAEliminar = cliente) }
    }

    fun cerrarDialogoEliminacion() {
        _uiState.update { it.copy(clienteAEliminar = null) }
    }

    fun registrarCliente(
        nombre: String,
        apellido: String,
        documento: String,
        celular: String,
        aceptoTratamientoDatos: Boolean,
    ) {
        if (!aceptoTratamientoDatos) {
            _uiState.update {
                it.copy(errorFormulario = "Debes aceptar explícitamente la autorización de tratamiento de datos personales conforme a la Ley 1581 de 2012.")
            }
            return
        }

        if (nombre.isBlank() || apellido.isBlank() || documento.isBlank() || celular.isBlank()) {
            _uiState.update { it.copy(errorFormulario = "Todos los campos son obligatorios.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, errorFormulario = null) }
            val req = ClienteCrearRequest(
                nombre = nombre.trim(),
                apellido = apellido.trim(),
                documento = documento.trim(),
                celular = celular.trim(),
                aceptoTratamientoDatos = true,
            )
            when (val res = repository.registrar(req)) {
                is ResultadoClientes.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            mostrarDialogoRegistro = false,
                            mensajeExito = "Cliente registrado con consentimiento Ley 1581.",
                        )
                    }
                    cargarClientes()
                }
                is ResultadoClientes.Conflicto -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Ya existe un cliente con ese documento.")
                    }
                }
                is ResultadoClientes.DatosInvalidos -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = res.mensaje ?: "Datos inválidos.")
                    }
                }
                is ResultadoClientes.SinConexion -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = "Sin conexión para registrar el cliente.")
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(guardando = false, errorFormulario = "Error al registrar cliente.")
                    }
                }
            }
        }
    }

    fun confirmarEliminacionDatos(cliente: Cliente, motivo: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(guardando = true, clienteAEliminar = null) }
            when (repository.solicitarEliminacion(cliente.id, motivo)) {
                is ResultadoClientes.Exito -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            mensajeExito = "Datos personales suprimidos conforme a la Ley 1581.",
                        )
                    }
                    cargarClientes()
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            guardando = false,
                            errorGeneral = "No se pudo procesar la solicitud de supresión de datos.",
                        )
                    }
                }
            }
        }
    }

    fun limpiarMensajes() {
        _uiState.update { it.copy(mensajeExito = null, errorGeneral = null) }
    }

    companion object {
        fun factory(repository: ClientesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClientesViewModel(repository) as T
            }
    }
}
