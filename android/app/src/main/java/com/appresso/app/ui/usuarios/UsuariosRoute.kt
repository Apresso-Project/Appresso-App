package com.appresso.app.ui.usuarios

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.usuarios.Usuario
import com.appresso.app.ui.PantallaProvisional

/** Conecta lista, formulario y diálogos de confirmación con [UsuariosViewModel]. */
@Composable
fun UsuariosRoute(
    viewModel: UsuariosViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    /** Id de quien inició sesión, para no ofrecerle acciones sobre su propia cuenta. */
    miId: Long? = null,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val formulario = estado.formulario

    // Atrás cierra el formulario si está abierto; si no, vuelve al panel.
    BackHandler(enabled = formulario != null) { viewModel.cerrarFormulario() }

    if (formulario == null) {
        UsuariosScreen(
            estado = estado,
            onNuevo = viewModel::nuevoUsuario,
            onEditar = viewModel::editarUsuario,
            onDesactivar = viewModel::pedirDesactivar,
            onActivar = viewModel::activar,
            onReintentar = viewModel::cargar,
            onVolver = onVolver,
            modifier = modifier,
            miId = miId,
        )
    } else {
        PantallaProvisional(
            titulo = stringResource(
                if (formulario.editando == null) R.string.user_form_title_new else R.string.user_form_title_edit,
            ),
            modifier = modifier,
        ) {
            UsuarioFormulario(
                estado = formulario,
                onNombreChange = viewModel::onNombreChange,
                onCorreoChange = viewModel::onCorreoChange,
                onContrasenaChange = viewModel::onContrasenaChange,
                onRolChange = viewModel::onRolChange,
                onGuardar = viewModel::guardar,
                onCancelar = viewModel::cerrarFormulario,
                rolBloqueado = miId != null && formulario.editando?.id == miId,
            )
        }
        if (formulario.confirmando && formulario.editando != null) {
            DialogoConfirmarCambios(
                formulario = formulario,
                original = formulario.editando,
                onConfirmar = viewModel::confirmarCambios,
                onCancelar = viewModel::cancelarConfirmacion,
            )
        }
    }

    estado.confirmarDesactivar?.let { usuario ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarDesactivar,
            title = { Text(stringResource(R.string.user_confirm_deactivate_title, usuario.nombre)) },
            text = { Text(stringResource(R.string.user_confirm_deactivate_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmarDesactivar) {
                    Text(stringResource(R.string.user_confirm_deactivate_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarDesactivar) { Text(stringResource(R.string.user_form_cancel)) }
            },
        )
    }
}

/** Resume qué cambiará (sin mostrar nunca la contraseña) y pide confirmar antes de guardar. */
@Composable
private fun DialogoConfirmarCambios(
    formulario: FormularioUiState,
    original: Usuario,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val cambios = UsuariosViewModel.cambios(formulario, original)
    val rolNuevo: Rol? = formulario.rol
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.user_confirm_changes_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.user_confirm_changes_intro))
                if (Campo.NOMBRE in cambios) Text("• " + stringResource(R.string.user_confirm_change_name))
                if (Campo.CORREO in cambios) Text("• " + stringResource(R.string.user_confirm_change_email))
                if (Campo.CONTRASENA in cambios) Text("• " + stringResource(R.string.user_confirm_change_password))
                if (Campo.ROL in cambios && rolNuevo != null) {
                    Text("• " + stringResource(R.string.user_confirm_change_role, etiquetaDe(original.rol), etiquetaDe(rolNuevo)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) { Text(stringResource(R.string.user_confirm_changes_action)) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text(stringResource(R.string.user_form_cancel)) } },
    )
}
