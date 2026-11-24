package com.appresso.app.ui.cuenta

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Success

/** Conecta [MisDatosScreen] con su [MisDatosViewModel]. [onCerrarSesion] se llama cuando ya se eliminaron los datos. */
@Composable
fun MisDatosRoute(
    viewModel: MisDatosViewModel,
    onVolver: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val salir: () -> Unit = {
        when {
            estado.cargando -> Unit // no se puede salir a mitad de la eliminación
            estado.eliminado -> onCerrarSesion()
            else -> {
                viewModel.limpiar()
                onVolver()
            }
        }
    }
    BackHandler(onBack = salir)
    MisDatosScreen(
        estado = estado,
        onContrasenaChange = viewModel::onContrasenaChange,
        onEliminar = viewModel::pedirEliminacion,
        onConfirmar = viewModel::confirmar,
        onCancelar = viewModel::cancelar,
        onSalir = salir,
        modifier = modifier,
    )
}

/** «Mis datos» del cliente: la autorización que dio y la eliminación de sus datos (Ley 1581). Solo dibuja. */
@Composable
fun MisDatosScreen(
    estado: MisDatosUiState,
    onContrasenaChange: (String) -> Unit,
    onEliminar: () -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MarcoCuenta(
        titulo = stringResource(R.string.misdatos_titulo),
        subtitulo = stringResource(R.string.misdatos_subtitulo),
        onVolver = onSalir,
        volverHabilitado = !estado.cargando,
        modifier = modifier,
    ) {
        if (estado.eliminado) {
            Text(
                text = stringResource(R.string.misdatos_exito),
                style = MaterialTheme.typography.titleMedium,
                color = Success,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            BotonCuenta(
                texto = stringResource(R.string.misdatos_entendido),
                textoCargando = stringResource(R.string.misdatos_entendido),
                onClick = onSalir,
            )
        } else {
            Titulo(stringResource(R.string.cliente_autorizacion_titulo))
            Parrafo(stringResource(R.string.cliente_autorizacion_texto))
            Titulo(stringResource(R.string.misdatos_eliminar_titulo))
            Parrafo(stringResource(R.string.misdatos_eliminar_texto))
            CampoTexto(
                etiqueta = stringResource(R.string.misdatos_contrasena),
                placeholder = stringResource(R.string.misdatos_contrasena_placeholder),
                valor = estado.contrasena,
                onValorChange = onContrasenaChange,
                icono = R.drawable.ic_lock,
                soloLectura = estado.cargando,
                error = estado.error?.let { textoDe(it) },
                transformacion = PasswordVisualTransformation(),
                teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                acciones = KeyboardActions(onDone = { onEliminar() }),
            )
            BotonCuenta(
                texto = stringResource(R.string.misdatos_eliminar),
                textoCargando = stringResource(R.string.misdatos_eliminando),
                onClick = onEliminar,
                cargando = estado.cargando,
            )
        }
    }

    if (estado.confirmando) {
        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text(stringResource(R.string.misdatos_confirmar_titulo)) },
            text = { Text(stringResource(R.string.misdatos_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = onConfirmar) {
                    Text(stringResource(R.string.misdatos_confirmar_si), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelar) {
                    Text(stringResource(R.string.misdatos_confirmar_no), color = Espresso)
                }
            },
        )
    }
}

@Composable
private fun Titulo(texto: String) = Text(
    text = texto,
    style = MaterialTheme.typography.titleMedium,
    color = Ink,
    modifier = Modifier.semantics { heading() },
)

@Composable
private fun Parrafo(texto: String) = Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = Ink)

@Composable
private fun textoDe(error: ErrorMisDatos): String = when (error) {
    ErrorMisDatos.ContrasenaVacia -> stringResource(R.string.misdatos_error_contrasena)
    is ErrorMisDatos.DelServidor -> error.mensaje ?: stringResource(R.string.misdatos_error_rechazado)
    ErrorMisDatos.SinConexion -> stringResource(R.string.login_error_no_connection)
    ErrorMisDatos.ErrorDelServidor -> stringResource(R.string.login_error_server)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun MisDatosPreview() {
    AppressoTheme {
        MisDatosScreen(
            estado = MisDatosUiState(),
            onContrasenaChange = {}, onEliminar = {}, onConfirmar = {}, onCancelar = {}, onSalir = {},
        )
    }
}
