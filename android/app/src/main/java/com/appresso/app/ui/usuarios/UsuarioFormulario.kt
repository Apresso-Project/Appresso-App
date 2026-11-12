package com.appresso.app.ui.usuarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink

/** Formulario de crear / editar usuario (SCRUM-57). Los errores de cada campo salen debajo de él. */
@Composable
fun UsuarioFormulario(
    estado: FormularioUiState,
    onNombreChange: (String) -> Unit,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onRolChange: (Rol) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editando = estado.editando != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampoTexto(
            etiqueta = stringResource(R.string.user_form_name),
            valor = estado.nombre,
            error = estado.errores[Campo.NOMBRE],
            habilitado = !estado.guardando,
            onChange = onNombreChange,
        )
        CampoTexto(
            etiqueta = stringResource(R.string.user_form_email),
            valor = estado.correo,
            error = estado.errores[Campo.CORREO],
            habilitado = !estado.guardando,
            teclado = KeyboardType.Email,
            onChange = onCorreoChange,
        )
        CampoTexto(
            etiqueta = stringResource(R.string.user_form_password),
            valor = estado.contrasena,
            error = estado.errores[Campo.CONTRASENA],
            habilitado = !estado.guardando,
            teclado = KeyboardType.Password,
            ayuda = if (editando) stringResource(R.string.user_form_password_edit_hint) else null,
            oculto = true,
            onChange = onContrasenaChange,
        )

        Text(
            text = stringResource(R.string.user_form_role),
            style = MaterialTheme.typography.titleMedium,
            color = Espresso,
        )
        Rol.entries.forEach { rol ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = estado.rol == rol,
                        enabled = !estado.guardando,
                        role = Role.RadioButton,
                        onClick = { onRolChange(rol) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = estado.rol == rol, onClick = null, enabled = !estado.guardando)
                Text(
                    text = etiquetaDe(rol),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        estado.errores[Campo.ROL]?.let {
            Text(textoDe(it), color = Error, style = MaterialTheme.typography.bodySmall)
        }

        estado.error?.let {
            Text(textoDe(it), color = Error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onGuardar,
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Espresso),
        ) {
            Text(stringResource(if (estado.guardando) R.string.user_form_saving else R.string.user_form_save))
        }
        OutlinedButton(
            onClick = onCancelar,
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.user_form_cancel))
        }
    }
}

@Composable
private fun CampoTexto(
    etiqueta: String,
    valor: String,
    error: ErrorCampo?,
    habilitado: Boolean,
    onChange: (String) -> Unit,
    teclado: KeyboardType = KeyboardType.Text,
    ayuda: String? = null,
    oculto: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(etiqueta) },
        singleLine = true,
        enabled = habilitado,
        isError = error != null,
        supportingText = (error?.let { { Text(textoDe(it)) } } ?: ayuda?.let { { Text(it) } }),
        visualTransformation = if (oculto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth(),
    )
}
