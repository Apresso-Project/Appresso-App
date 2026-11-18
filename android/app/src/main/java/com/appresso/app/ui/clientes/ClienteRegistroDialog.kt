package com.appresso.app.ui.clientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appresso.app.R
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted

@Composable
fun ClienteRegistroDialog(
    guardando: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRegistrar: (nombre: String, apellido: String, documento: String, celular: String, acepto: Boolean) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var documento by remember { mutableStateOf("") }
    var celular by remember { mutableStateOf("") }
    var aceptoTratamientoDatos by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!guardando) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.clientes_nuevo),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Espresso,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.cliente_nombre_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = apellido,
                    onValueChange = { apellido = it },
                    label = { Text(stringResource(R.string.cliente_apellido_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = documento,
                    onValueChange = { documento = it },
                    label = { Text(stringResource(R.string.cliente_documento_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = celular,
                    onValueChange = { celular = it },
                    label = { Text(stringResource(R.string.cliente_celular_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )

                // Contenedor con el texto formal de autorización Ley 1581 de 2012
                Text(
                    text = stringResource(R.string.cliente_autorizacion_titulo),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Espresso,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Cream)
                        .border(1.dp, Muted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cliente_autorizacion_texto),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = Ink,
                    )
                }

                // Checkbox de aceptación explícita obligatoria
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !guardando) { aceptoTratamientoDatos = !aceptoTratamientoDatos }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = aceptoTratamientoDatos,
                        onCheckedChange = { if (!guardando) aceptoTratamientoDatos = it },
                        colors = CheckboxDefaults.colors(checkedColor = Espresso),
                    )
                    Text(
                        text = stringResource(R.string.cliente_checkbox_aceptacion),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aceptoTratamientoDatos) Ink else Espresso,
                        fontWeight = if (aceptoTratamientoDatos) FontWeight.Normal else FontWeight.Medium,
                    )
                }

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRegistrar(nombre, apellido, documento, celular, aceptoTratamientoDatos)
                },
                enabled = !guardando && aceptoTratamientoDatos && nombre.isNotBlank() && documento.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso),
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 6.dp),
                        color = Cream,
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.cliente_guardar))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !guardando,
            ) {
                Text(stringResource(R.string.cliente_cancelar), color = Espresso)
            }
        },
    )
}
