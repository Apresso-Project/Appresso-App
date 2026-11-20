package com.appresso.app.ui.catalogo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso

@Composable
fun ProductoFormularioDialog(
    producto: Producto?,
    categorias: List<Categoria>,
    guardando: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGuardarCrear: (nombre: String, descripcion: String?, precio: Double, categoriaId: Long, imagenUrl: String?, existencias: Int?, umbral: Int?) -> Unit,
    onGuardarEditar: (nombre: String, descripcion: String?, precio: Double, categoriaId: Long, imagenUrl: String?, umbral: Int?) -> Unit,
) {
    var nombre by remember(producto) { mutableStateOf(producto?.nombre.orEmpty()) }
    var descripcion by remember(producto) { mutableStateOf(producto?.descripcion.orEmpty()) }
    var precioTexto by remember(producto) { mutableStateOf(producto?.precio?.let { String.format("%.0f", it) }.orEmpty()) }
    var categoriaIdSeleccionada by remember(producto, categorias) {
        mutableStateOf(
            producto?.categoriaId ?: categorias.firstOrNull()?.id ?: 0L,
        )
    }
    var imagenUrl by remember(producto) { mutableStateOf(producto?.imagenUrl.orEmpty()) }
    var existenciasTexto by remember(producto) { mutableStateOf("") }
    var umbralTexto by remember(producto) { mutableStateOf(producto?.umbralMinimo?.takeIf { it > 0 }?.toString().orEmpty()) }

    var dropdownExpandid by remember { mutableStateOf(false) }

    val categoriaSeleccionadaNombre = categorias.find { it.id == categoriaIdSeleccionada }?.nombre.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!guardando) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (producto == null) R.string.producto_nuevo_titulo else R.string.producto_editar_titulo,
                ),
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
                    onValueChange = { if (it.length <= 120) nombre = it },
                    label = { Text(stringResource(R.string.producto_nombre_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { if (it.length <= 255) descripcion = it },
                    label = { Text(stringResource(R.string.producto_descripcion_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = false,
                    maxLines = 3,
                )

                OutlinedTextField(
                    value = precioTexto,
                    onValueChange = { precioTexto = it },
                    label = { Text(stringResource(R.string.producto_precio_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                // Selector de categoría
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categoriaSeleccionadaNombre,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.producto_categoria_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !guardando,
                        trailingIcon = {
                            Text("▼", modifier = Modifier.clickable { if (!guardando) dropdownExpandid = true })
                        },
                    )

                    DropdownMenu(
                        expanded = dropdownExpandid,
                        onDismissRequest = { dropdownExpandid = false },
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) {
                        categorias.filter { it.activa || it.id == categoriaIdSeleccionada }.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.nombre + if (!cat.activa) " (Desactivada)" else "") },
                                onClick = {
                                    categoriaIdSeleccionada = cat.id
                                    dropdownExpandid = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = imagenUrl,
                    onValueChange = { if (it.length <= 500) imagenUrl = it },
                    label = { Text(stringResource(R.string.producto_imagen_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    singleLine = true,
                )

                if (producto == null) {
                    OutlinedTextField(
                        value = existenciasTexto,
                        onValueChange = { existenciasTexto = it },
                        label = { Text(stringResource(R.string.producto_existencias_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !guardando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                OutlinedTextField(
                    value = umbralTexto,
                    onValueChange = { umbralTexto = it },
                    label = { Text(stringResource(R.string.producto_umbral_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

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
            val precioParsed = precioTexto.toDoubleOrNull() ?: 0.0
            val existenciasParsed = existenciasTexto.toIntOrNull()
            val umbralParsed = umbralTexto.toIntOrNull()
            val formularioValido = nombre.isNotBlank() && precioParsed > 0 && categoriaIdSeleccionada > 0

            Button(
                onClick = {
                    if (producto == null) {
                        onGuardarCrear(
                            nombre,
                            descripcion,
                            precioParsed,
                            categoriaIdSeleccionada,
                            imagenUrl,
                            existenciasParsed,
                            umbralParsed,
                        )
                    } else {
                        onGuardarEditar(
                            nombre,
                            descripcion,
                            precioParsed,
                            categoriaIdSeleccionada,
                            imagenUrl,
                            umbralParsed,
                        )
                    }
                },
                enabled = !guardando && formularioValido,
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
