package com.appresso.app.ui.cuenta

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Success

/** Conecta [RecuperarContrasenaScreen] con su [RecuperarContrasenaViewModel]. */
@Composable
fun RecuperarContrasenaRoute(
    viewModel: RecuperarContrasenaViewModel,
    onVolver: () -> Unit,
    onIrAlLogin: (documento: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    RecuperarContrasenaScreen(
        estado = estado,
        errorGeneral = estado.error?.let { textoDe(it) },
        onDocumentoChange = viewModel::onDocumentoChange,
        onCodigoChange = viewModel::onCodigoChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onConfirmacionChange = viewModel::onConfirmacionChange,
        onEnviarCodigo = viewModel::enviarCodigo,
        onCambiarContrasena = viewModel::cambiarContrasena,
        onOtroDocumento = viewModel::volverAlDocumento,
        onVolver = onVolver,
        onIrAlLogin = { onIrAlLogin(estado.documento.trim()) },
        modifier = modifier,
    )
}

/**
 * Recuperación de la contraseña del cliente (RF-65), en tres pasos: el documento para pedir el
 * código, el código con la contraseña nueva y la confirmación. Solo dibuja.
 */
@Composable
fun RecuperarContrasenaScreen(
    estado: RecuperacionUiState,
    errorGeneral: String?,
    onDocumentoChange: (String) -> Unit,
    onCodigoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onConfirmacionChange: (String) -> Unit,
    onEnviarCodigo: () -> Unit,
    onCambiarContrasena: () -> Unit,
    onOtroDocumento: () -> Unit,
    onVolver: () -> Unit,
    onIrAlLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitulo = when (estado.paso) {
        PasoRecuperacion.DOCUMENTO -> R.string.recuperar_subtitulo_documento
        PasoRecuperacion.CODIGO -> R.string.recuperar_subtitulo_codigo
        PasoRecuperacion.LISTO -> R.string.recuperar_subtitulo_listo
    }
    MarcoCuenta(
        titulo = stringResource(R.string.recuperar_titulo),
        subtitulo = stringResource(subtitulo),
        onVolver = onVolver,
        volverHabilitado = !estado.cargando,
        modifier = modifier,
    ) {
        when (estado.paso) {
            PasoRecuperacion.DOCUMENTO -> {
                CampoTexto(
                    etiqueta = stringResource(R.string.registro_documento),
                    placeholder = stringResource(R.string.registro_documento_placeholder),
                    valor = estado.documento,
                    onValorChange = onDocumentoChange,
                    soloLectura = estado.cargando,
                    teclado = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Send,
                    ),
                    acciones = KeyboardActions(onSend = { onEnviarCodigo() }),
                )
                if (errorGeneral != null) MensajeError(errorGeneral)
                BotonCuenta(
                    texto = stringResource(R.string.recuperar_enviar_codigo),
                    textoCargando = stringResource(R.string.cuenta_enviando),
                    onClick = onEnviarCodigo,
                    cargando = estado.cargando,
                    habilitado = estado.documento.isNotBlank(),
                )
            }

            PasoRecuperacion.CODIGO -> {
                Text(
                    text = estado.aviso ?: stringResource(R.string.recuperar_aviso),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                CampoTexto(
                    etiqueta = stringResource(R.string.recuperar_codigo),
                    placeholder = stringResource(R.string.recuperar_codigo_placeholder),
                    valor = estado.codigo,
                    onValorChange = onCodigoChange,
                    soloLectura = estado.cargando,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                )
                CampoTexto(
                    etiqueta = stringResource(R.string.recuperar_nueva),
                    placeholder = stringResource(R.string.registro_contrasena_placeholder),
                    valor = estado.contrasena,
                    onValorChange = onContrasenaChange,
                    icono = R.drawable.ic_lock,
                    soloLectura = estado.cargando,
                    transformacion = PasswordVisualTransformation(),
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                CampoTexto(
                    etiqueta = stringResource(R.string.recuperar_confirmar),
                    placeholder = stringResource(R.string.recuperar_confirmar_placeholder),
                    valor = estado.confirmacion,
                    onValorChange = onConfirmacionChange,
                    icono = R.drawable.ic_lock,
                    soloLectura = estado.cargando,
                    transformacion = PasswordVisualTransformation(),
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    acciones = KeyboardActions(onDone = { onCambiarContrasena() }),
                )
                if (errorGeneral != null) MensajeError(errorGeneral)
                BotonCuenta(
                    texto = stringResource(R.string.recuperar_cambiar),
                    textoCargando = stringResource(R.string.cuenta_enviando),
                    onClick = onCambiarContrasena,
                    cargando = estado.cargando,
                )
                TextButton(
                    onClick = onEnviarCodigo,
                    enabled = !estado.cargando,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.recuperar_reenviar), color = Caramel)
                }
                TextButton(
                    onClick = onOtroDocumento,
                    enabled = !estado.cargando,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.recuperar_otro_documento), color = Caramel)
                }
            }

            PasoRecuperacion.LISTO -> {
                Text(
                    text = estado.mensajeExito ?: stringResource(R.string.recuperar_exito),
                    style = MaterialTheme.typography.titleMedium,
                    color = Success,
                )
                BotonCuenta(
                    texto = stringResource(R.string.cuenta_ir_login),
                    textoCargando = stringResource(R.string.cuenta_ir_login),
                    onClick = onIrAlLogin,
                )
            }
        }
    }
}

@Composable
private fun textoDe(error: ErrorRecuperacion): String = when (error) {
    ErrorRecuperacion.DocumentoInvalido -> stringResource(R.string.registro_error_documento)
    ErrorRecuperacion.CodigoIncompleto -> stringResource(R.string.recuperar_error_codigo)
    ErrorRecuperacion.ContrasenaInvalida -> stringResource(R.string.registro_error_contrasena)
    ErrorRecuperacion.NoCoinciden -> stringResource(R.string.recuperar_error_no_coinciden)
    is ErrorRecuperacion.DelServidor -> error.mensaje ?: stringResource(R.string.recuperar_error_codigo_servidor)
    ErrorRecuperacion.SinConexion -> stringResource(R.string.login_error_no_connection)
    ErrorRecuperacion.ErrorDelServidor -> stringResource(R.string.login_error_server)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun RecuperarContrasenaCodigoPreview() {
    AppressoTheme {
        RecuperarContrasenaScreen(
            estado = RecuperacionUiState(paso = PasoRecuperacion.CODIGO, documento = "1234567890"),
            errorGeneral = null,
            onDocumentoChange = {}, onCodigoChange = {}, onContrasenaChange = {}, onConfirmacionChange = {},
            onEnviarCodigo = {}, onCambiarContrasena = {}, onOtroDocumento = {}, onVolver = {}, onIrAlLogin = {},
        )
    }
}
