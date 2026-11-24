package com.appresso.app.ui.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.cuenta.MisDatosRepository
import com.appresso.app.data.cuenta.ResultadoEliminacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué no se eliminaron los datos. La pantalla traduce cada caso; los del servidor se muestran tal cual. */
sealed interface ErrorMisDatos {
    data object ContrasenaVacia : ErrorMisDatos
    data class DelServidor(val mensaje: String?) : ErrorMisDatos
    data object SinConexion : ErrorMisDatos
    data object ErrorDelServidor : ErrorMisDatos
}

data class MisDatosUiState(
    val contrasena: String = "",
    /** Se muestra la última confirmación antes de eliminar. */
    val confirmando: Boolean = false,
    val cargando: Boolean = false,
    val error: ErrorMisDatos? = null,
    /** Los datos ya se eliminaron: solo queda cerrar la sesión. */
    val eliminado: Boolean = false,
)

/**
 * El cliente elimina sus datos personales (Ley 1581, 8.4.1): escribe su contraseña, confirma y, si el
 * servidor lo acepta, la sesión se cierra. Es irreversible.
 */
class MisDatosViewModel(private val repositorio: MisDatosRepository) : ViewModel() {

    private val _estado = MutableStateFlow(MisDatosUiState())
    val estado: StateFlow<MisDatosUiState> = _estado.asStateFlow()

    fun onContrasenaChange(valor: String) = _estado.update {
        if (it.cargando) it else it.copy(contrasena = valor, error = null)
    }

    /** Pide la última confirmación. Todavía no llama al servidor. */
    fun pedirEliminacion() = _estado.update {
        when {
            it.cargando || it.eliminado -> it
            it.contrasena.isBlank() -> it.copy(error = ErrorMisDatos.ContrasenaVacia)
            else -> it.copy(confirmando = true, error = null)
        }
    }

    fun cancelar() = _estado.update { it.copy(confirmando = false) }

    fun confirmar() {
        val actual = _estado.value
        if (!actual.confirmando || actual.cargando) return
        _estado.update { it.copy(confirmando = false, cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = repositorio.eliminar(actual.contrasena)
            _estado.update {
                when (resultado) {
                    ResultadoEliminacion.Eliminado -> MisDatosUiState(eliminado = true)
                    is ResultadoEliminacion.Rechazado ->
                        it.copy(cargando = false, contrasena = "", error = ErrorMisDatos.DelServidor(resultado.mensaje))
                    ResultadoEliminacion.SinConexion -> it.copy(cargando = false, error = ErrorMisDatos.SinConexion)
                    ResultadoEliminacion.ErrorDelServidor -> it.copy(cargando = false, error = ErrorMisDatos.ErrorDelServidor)
                }
            }
        }
    }

    /** Al salir de la pantalla, para no dejar la contraseña escrita. */
    fun limpiar() = _estado.update { if (it.cargando || it.eliminado) it else MisDatosUiState() }

    companion object {
        fun factory(repositorio: MisDatosRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { MisDatosViewModel(repositorio) } }
    }
}
