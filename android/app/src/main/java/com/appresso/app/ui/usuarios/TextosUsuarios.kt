package com.appresso.app.ui.usuarios

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.appresso.app.R
import com.appresso.app.data.auth.Rol

@Composable
fun etiquetaDe(rol: Rol): String = stringResource(
    when (rol) {
        Rol.ADMINISTRADOR -> R.string.role_administrador
        Rol.CAJERO -> R.string.role_cajero
        Rol.MESERO -> R.string.role_mesero
        Rol.COCINERO -> R.string.role_cocinero
        Rol.CLIENTE -> R.string.role_cliente
    },
)

@Composable
fun textoDe(error: ErrorCampo): String = when (error) {
    ErrorCampo.Obligatorio -> stringResource(R.string.user_error_required)
    ErrorCampo.NombreDemasiadoLargo -> stringResource(R.string.user_error_name_long)
    ErrorCampo.CorreoInvalido -> stringResource(R.string.user_error_email_invalid)
    ErrorCampo.ContrasenaCorta -> stringResource(R.string.user_error_password_short)
    ErrorCampo.ContrasenaLarga -> stringResource(R.string.user_error_password_long)
    is ErrorCampo.Servidor -> error.mensaje
}

/** Los mensajes del servidor (403, 409, 400) se muestran tal cual; si faltan se usa uno propio. */
@Composable
fun textoDe(error: ErrorUsuarios): String = when (error) {
    ErrorUsuarios.SinConexion -> stringResource(R.string.user_error_no_connection)
    ErrorUsuarios.ErrorDelServidor -> stringResource(R.string.user_error_server)
    ErrorUsuarios.NoEncontrado -> stringResource(R.string.user_error_not_found)
    is ErrorUsuarios.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.user_error_forbidden)
    is ErrorUsuarios.Regla -> error.mensajeServidor ?: stringResource(R.string.user_error_rule)
    is ErrorUsuarios.DatosInvalidos -> error.mensajeServidor ?: stringResource(R.string.user_error_invalid_data)
}
