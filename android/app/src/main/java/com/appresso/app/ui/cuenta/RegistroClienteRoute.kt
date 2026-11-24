package com.appresso.app.ui.cuenta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R

/**
 * Conecta [RegistroClienteScreen] con su [RegistroClienteViewModel] y traduce los errores a texto.
 * Los mensajes del servidor se muestran tal cual (son los de RF-50).
 *
 * @param onIrAlLogin recibe el documento registrado, para dejarlo escrito en el inicio de sesión.
 */
@Composable
fun RegistroClienteRoute(
    viewModel: RegistroClienteViewModel,
    onVolver: () -> Unit,
    onIrAlLogin: (documento: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    val textos = CampoRegistro.entries.associateWith { campo ->
        estado.erroresServidor[campo] ?: estado.problemas[campo]?.let { textoDe(campo, it) }
    }

    RegistroClienteScreen(
        estado = estado,
        errorDe = { textos[it] },
        errorGeneral = estado.error?.let { textoDe(it) },
        onNombreChange = viewModel::onNombreChange,
        onApellidoChange = viewModel::onApellidoChange,
        onDocumentoChange = viewModel::onDocumentoChange,
        onCelularChange = viewModel::onCelularChange,
        onCorreoChange = viewModel::onCorreoChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onAceptoChange = viewModel::onAceptoChange,
        onEnviar = viewModel::enviar,
        onVolver = onVolver,
        onIrAlLogin = { onIrAlLogin(estado.documento.trim()) },
        modifier = modifier,
    )
}

@Composable
private fun textoDe(campo: CampoRegistro, problema: ProblemaCampo): String = when {
    campo == CampoRegistro.ACEPTO -> stringResource(R.string.registro_error_acepto)
    problema == ProblemaCampo.FALTA -> stringResource(R.string.registro_error_requerido)
    else -> stringResource(textoDeFormato(campo))
}

private fun textoDeFormato(campo: CampoRegistro): Int = when (campo) {
    CampoRegistro.NOMBRE, CampoRegistro.APELLIDO -> R.string.registro_error_nombre_largo
    CampoRegistro.DOCUMENTO -> R.string.registro_error_documento
    CampoRegistro.CELULAR -> R.string.registro_error_celular
    CampoRegistro.CORREO -> R.string.registro_error_correo
    CampoRegistro.CONTRASENA -> R.string.registro_error_contrasena
    CampoRegistro.ACEPTO -> R.string.registro_error_acepto
}

@Composable
private fun textoDe(error: ErrorRegistro): String = when (error) {
    ErrorRegistro.Incompleto -> stringResource(R.string.registro_error_incompleto)
    is ErrorRegistro.Formato -> stringResource(textoDeFormato(error.campo))
    is ErrorRegistro.DelServidor -> error.mensaje ?: stringResource(R.string.login_error_invalid_data)
    ErrorRegistro.SinConexion -> stringResource(R.string.login_error_no_connection)
    ErrorRegistro.ErrorDelServidor -> stringResource(R.string.login_error_server)
}
