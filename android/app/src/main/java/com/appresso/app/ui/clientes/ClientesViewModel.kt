package com.appresso.app.ui.clientes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.clientes.Cliente
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ResultadoBusqueda
import com.appresso.app.data.cuenta.ReglasCuenta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué mostrar bajo el buscador. */
sealed interface ResultadoVista {
    data class Encontrado(val cliente: Cliente) : ResultadoVista

    /** "Cliente no encontrado", con la indicación de registrarse desde la app (RF-51). */
    data object NoEncontrado : ResultadoVista
}

/** Por qué no se pudo buscar. La pantalla traduce cada caso; el 403 muestra el mensaje del servidor. */
sealed interface ErrorBusqueda {
    data object DocumentoInvalido : ErrorBusqueda
    data class SinPermiso(val mensaje: String?) : ErrorBusqueda
    data object SinConexion : ErrorBusqueda
    data object ErrorDelServidor : ErrorBusqueda
}

data class ClientesUiState(
    val documento: String = "",
    val buscando: Boolean = false,
    val resultado: ResultadoVista? = null,
    val error: ErrorBusqueda? = null,
)

/**
 * Búsqueda de un cliente por su documento (RF-51, RF-57), para el mesero y el administrador.
 * Pregunta al servidor cada vez: no descarga la lista de clientes.
 */
class ClientesViewModel(private val repositorio: ClientesRepository) : ViewModel() {

    private val _estado = MutableStateFlow(ClientesUiState())
    val estado: StateFlow<ClientesUiState> = _estado.asStateFlow()

    /** Al cambiar el documento, el resultado anterior ya no corresponde. */
    fun onDocumentoChange(valor: String) = _estado.update {
        if (it.buscando) it else it.copy(documento = valor, resultado = null, error = null)
    }

    fun buscar() {
        val actual = _estado.value
        if (actual.buscando) return
        val documento = ReglasCuenta.normalizarDocumento(actual.documento.trim())
        if (!ReglasCuenta.esDocumentoValido(documento)) {
            _estado.update { it.copy(resultado = null, error = ErrorBusqueda.DocumentoInvalido) }
            return
        }
        _estado.update { it.copy(buscando = true, resultado = null, error = null) }
        viewModelScope.launch {
            val nuevo = when (val r = repositorio.buscarPorDocumento(documento)) {
                is ResultadoBusqueda.Encontrado -> _estado.value.copy(resultado = ResultadoVista.Encontrado(r.cliente))
                ResultadoBusqueda.NoEncontrado -> _estado.value.copy(resultado = ResultadoVista.NoEncontrado)
                is ResultadoBusqueda.SinPermiso -> _estado.value.copy(error = ErrorBusqueda.SinPermiso(r.mensaje))
                ResultadoBusqueda.SinConexion -> _estado.value.copy(error = ErrorBusqueda.SinConexion)
                ResultadoBusqueda.ErrorDelServidor -> _estado.value.copy(error = ErrorBusqueda.ErrorDelServidor)
            }
            _estado.value = nuevo.copy(buscando = false)
        }
    }

    companion object {
        fun factory(repositorio: ClientesRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ClientesViewModel(repositorio) } }
    }
}
