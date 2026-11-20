package com.appresso.app.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R

/**
 * Conecta [LoginScreen] con su [LoginViewModel]: lee el estado y traduce los errores a texto.
 * [onCrearCuenta] y [onOlvideContrasena] abren las pantallas públicas del cliente.
 */
@Composable
fun LoginRoute(
    viewModel: LoginViewModel,
    onCrearCuenta: () -> Unit,
    onOlvideContrasena: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LoginScreen(
        identificador = estado.identificador,
        contrasena = estado.contrasena,
        onIdentificadorChange = viewModel::onIdentificadorChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onEnviar = viewModel::enviar,
        onCrearCuenta = onCrearCuenta,
        onOlvideContrasena = onOlvideContrasena,
        modifier = modifier,
        errorMessage = estado.error?.let { textoDe(it) },
        isLoading = estado.cargando,
    )
}

/**
 * Textos de cada error. Los mensajes del servidor en 400 y 423 se muestran tal cual (el contrato
 * en docs/api-auth.md lo pide); si faltan, se usa uno propio. El 401 nunca dice qué dato falló.
 */
@Composable
private fun textoDe(error: ErrorLogin): String = when (error) {
    ErrorLogin.IdentificadorInvalido -> stringResource(R.string.login_error_identifier_invalid)
    ErrorLogin.ContrasenaDemasiadoLarga -> stringResource(R.string.login_error_password_too_long)
    ErrorLogin.CredencialesInvalidas -> stringResource(R.string.login_error_credentials)
    is ErrorLogin.CuentaBloqueada -> error.mensajeServidor ?: stringResource(R.string.login_error_locked)
    is ErrorLogin.DatosInvalidos -> error.detalles.takeIf { it.isNotEmpty() }?.joinToString("\n")
        ?: error.mensajeServidor
        ?: stringResource(R.string.login_error_invalid_data)
    ErrorLogin.SinConexion -> stringResource(R.string.login_error_no_connection)
    ErrorLogin.ErrorDelServidor -> stringResource(R.string.login_error_server)
}
