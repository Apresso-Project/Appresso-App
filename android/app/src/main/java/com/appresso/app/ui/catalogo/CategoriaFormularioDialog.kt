package com.appresso.app.ui.catalogo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso

@Composable
fun CategoriaFormularioDialog(
    categoria: Categoria?,
    guardando: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGuardar: (nombre: String) -> Unit,
) {
    var nombre by remember(categoria) { mutableStateOf(categoria?.nombre.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!guardando) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (categoria == null) R.string.categoria_nueva_titulo else R.string.categoria_editar_titulo,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Espresso,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { if (it.length <= 80) nombre = it },
                    label = { Text(stringResource(R.string.categoria_nombre_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = true,
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(nombre) },
                enabled = !guardando && nombre.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso),
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 6.dp),
                        color = Cream,
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.catalogo_guardar))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !guardando,
            ) {
                Text(stringResource(R.string.catalogo_cancelar), color = Espresso)
            }
        },
    )
}
