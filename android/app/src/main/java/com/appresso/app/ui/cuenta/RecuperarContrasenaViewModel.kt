package com.appresso.app.ui.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.cuenta.CuentaClienteRepository
import com.appresso.app.data.cuenta.ReglasCuenta
import com.appresso.app.data.cuenta.ResultadoCuenta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Los pasos de la recuperación: pedir el código, usarlo y terminar. */
enum class PasoRecuperacion { DOCUMENTO, CODIGO, LISTO }

/** Por qué no se pudo avanzar. La pantalla traduce cada caso; los del servidor se muestran tal cual. */
sealed interface ErrorRecuperacion {
    data object DocumentoInvalido : ErrorRecuperacion
    data object CodigoIncompleto : ErrorRecuperacion
    data object ContrasenaInvalida : ErrorRecuperacion
    data object NoCoinciden : ErrorRecuperacion

    /** 400 del servidor, por ejemplo "El código no es válido o venció. Solicita uno nuevo.". */
    data class DelServidor(val mensaje: String?) : ErrorRecuperacion

    data object SinConexion : ErrorRecuperacion
    data object ErrorDelServidor : ErrorRecuperacion
}

/** Todo lo que la pantalla de recuperación necesita dibujar. */
data class RecuperacionUiState(
    val paso: PasoRecuperacion = PasoRecuperacion.DOCUMENTO,
    val documento: String = "",
    val codigo: String = "",
    val contrasena: String = "",
    val confirmacion: String = "",
    val cargando: Boolean = false,
    /** El mensaje del servidor al pedir el código (es el mismo exista o no el documento). */
    val aviso: String? = null,
    val error: ErrorRecuperacion? = null,
    val mensajeExito: String? = null,
)

/**
 * Recuperación de la contraseña del cliente con un código de 6 dígitos enviado a su correo (RF-65).
 * Las contraseñas se descartan al terminar.
 */
class RecuperarContrasenaViewModel(private val repositorio: CuentaClienteRepository) : ViewModel() {

    private val _estado = MutableStateFlow(RecuperacionUiState())
    val estado: StateFlow<RecuperacionUiState> = _estado.asStateFlow()

    fun onDocumentoChange(valor: String) = editar { it.copy(documento = valor) }

    /** Solo dígitos y como mucho 6. */
    fun onCodigoChange(valor: String) = editar { it.copy(codigo = valor.filter(Char::isDigit).take(LARGO_CODIGO)) }

    fun onContrasenaChange(valor: String) = editar { it.copy(contrasena = valor) }
    fun onConfirmacionChange(valor: String) = editar { it.copy(confirmacion = valor) }

    /** Paso 1: pide el código. También sirve para reenviarlo desde el paso 2. */
    fun enviarCodigo() {
        val actual = _estado.value
        if (actual.cargando) return
        if (!ReglasCuenta.esDocumentoValido(actual.documento.trim())) {
            _estado.update { it.copy(error = ErrorRecuperacion.DocumentoInvalido) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.solicitarCodigo(actual.documento.trim())) {
                is ResultadoCuenta.Exito -> _estado.update {
                    it.copy(cargando = false, paso = PasoRecuperacion.CODIGO, aviso = resultado.mensaje, codigo = "")
                }
                else -> _estado.update { it.copy(cargando = false, error = resultado.aError()) }
            }
        }
    }

    /** Paso 2: cambia la contraseña con el código. */
    fun cambiarContrasena() {
        val actual = _estado.value
        if (actual.cargando) return
        val problema = when {
            actual.codigo.length != LARGO_CODIGO -> ErrorRecuperacion.CodigoIncompleto
            !ReglasCuenta.esContrasenaValida(actual.contrasena) -> ErrorRecuperacion.ContrasenaInvalida
            actual.contrasena != actual.confirmacion -> ErrorRecuperacion.NoCoinciden
            else -> null
        }
        if (problema != null) {
            _estado.update { it.copy(error = problema) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.restablecer(actual.documento.trim(), actual.codigo, actual.contrasena)) {
                is ResultadoCuenta.Exito -> _estado.update {
                    it.copy(
                        cargando = false,
                        paso = PasoRecuperacion.LISTO,
                        mensajeExito = resultado.mensaje,
                        codigo = "",
                        contrasena = "",
                        confirmacion = "",
                    )
                }
                else -> _estado.update { it.copy(cargando = false, error = resultado.aError()) }
            }
        }
    }

    /** Desde el paso 2, para corregir el documento. */
    fun volverAlDocumento() = _estado.update {
        if (it.cargando) it else it.copy(paso = PasoRecuperacion.DOCUMENTO, codigo = "", error = null, aviso = null)
    }

    private fun editar(cambio: (RecuperacionUiState) -> RecuperacionUiState) = _estado.update {
        if (it.cargando) it else cambio(it).copy(error = null)
    }

    companion object {
        const val LARGO_CODIGO = 6

        fun factory(repositorio: CuentaClienteRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { RecuperarContrasenaViewModel(repositorio) } }
    }
}

private fun ResultadoCuenta.aError(): ErrorRecuperacion? = when (this) {
    is ResultadoCuenta.Exito -> null
    is ResultadoCuenta.DatosInvalidos -> ErrorRecuperacion.DelServidor(mensaje)
    is ResultadoCuenta.Duplicado -> ErrorRecuperacion.DelServidor(mensaje)
    ResultadoCuenta.SinConexion -> ErrorRecuperacion.SinConexion
    ResultadoCuenta.ErrorDelServidor -> ErrorRecuperacion.ErrorDelServidor
}
