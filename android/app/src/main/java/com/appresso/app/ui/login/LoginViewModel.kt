package com.appresso.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.ResultadoLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo que la pantalla de inicio de sesión necesita dibujar. */
data class LoginUiState(
    val correo: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: ErrorLogin? = null,
)

/**
 * Por qué falló el inicio de sesión. El ViewModel no conoce textos (no tiene acceso a los
 * recursos): la pantalla traduce cada caso a un mensaje.
 */
sealed interface ErrorLogin {

    /** Validación del lado del cliente: no se llegó a llamar al servidor. */
    data object CorreoInvalido : ErrorLogin
    data object ContrasenaDemasiadoLarga : ErrorLogin

    data object CredencialesInvalidas : ErrorLogin

    /** [mensajeServidor] trae los minutos que faltan; puede faltar si el servidor no envió cuerpo. */
    data class CuentaBloqueada(val mensajeServidor: String?) : ErrorLogin

    /** El servidor rechazó algún dato (400). [detalles] son los problemas por campo, ya sin los nombres. */
    data class DatosInvalidos(val mensajeServidor: String?, val detalles: List<String>) : ErrorLogin

    data object SinConexion : ErrorLogin
    data object ErrorDelServidor : ErrorLogin
}

class LoginViewModel(
    private val repositorio: AuthRepository,
    private val almacenSesion: AlmacenSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    // Mientras se espera la respuesta los campos no cambian; al editar, el error anterior ya no aplica.
    fun onCorreoChange(valor: String) = _estado.update {
        if (it.cargando) it else it.copy(correo = valor, error = null)
    }

    fun onContrasenaChange(valor: String) = _estado.update {
        if (it.cargando) it else it.copy(contrasena = valor, error = null)
    }

    fun enviar() {
        val actual = _estado.value
        if (actual.cargando) return // un segundo toque no debe enviar otra solicitud (cuenta como otro intento)

        val correo = actual.correo.trim()
        val contrasena = actual.contrasena
        if (correo.isEmpty() || contrasena.isEmpty()) return

        val problema = validar(correo, contrasena)
        if (problema != null) {
            _estado.update { it.copy(error = problema) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = repositorio.iniciarSesion(correo, contrasena)) {
                is ResultadoLogin.Exito -> {
                    almacenSesion.guardar(resultado.sesion)
                    // La contraseña no se queda en memoria una vez usada.
                    _estado.update { it.copy(cargando = false, contrasena = "", error = null) }
                }

                else -> _estado.update { it.copy(cargando = false, error = resultado.aError()) }
            }
        }
    }

    /** Mismas reglas del servidor (docs/api-auth.md), igual de permisivas o más, para no rechazar de más. */
    private fun validar(correo: String, contrasena: String): ErrorLogin? = when {
        !esCorreoValido(correo) -> ErrorLogin.CorreoInvalido
        contrasena.length > MAX_CARACTERES_CONTRASENA -> ErrorLogin.ContrasenaDemasiadoLarga
        else -> null
    }

    companion object {
        const val MAX_CARACTERES_CORREO = 150
        const val MAX_CARACTERES_CONTRASENA = 72

        // Algo antes y después de una @, sin espacios. El formato fino lo valida el servidor.
        private val FORMATO_CORREO = Regex("^[^@\\s]+@[^@\\s]+$")

        internal fun esCorreoValido(correo: String) =
            correo.length <= MAX_CARACTERES_CORREO && FORMATO_CORREO.matches(correo)

        fun factory(repositorio: AuthRepository, almacenSesion: AlmacenSesion): ViewModelProvider.Factory =
            viewModelFactory { initializer { LoginViewModel(repositorio, almacenSesion) } }
    }
}

private fun ResultadoLogin.aError(): ErrorLogin? = when (this) {
    is ResultadoLogin.Exito -> null
    is ResultadoLogin.DatosInvalidos -> ErrorLogin.DatosInvalidos(mensaje, campos.values.toList())
    ResultadoLogin.CredencialesInvalidas -> ErrorLogin.CredencialesInvalidas
    is ResultadoLogin.CuentaBloqueada -> ErrorLogin.CuentaBloqueada(mensaje)
    ResultadoLogin.SinConexion -> ErrorLogin.SinConexion
    ResultadoLogin.ErrorDelServidor -> ErrorLogin.ErrorDelServidor
}
