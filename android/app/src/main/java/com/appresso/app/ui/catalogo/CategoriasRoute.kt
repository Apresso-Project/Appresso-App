package com.appresso.app.ui.catalogo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Ink

/** Conecta la lista, el formulario y los diálogos de confirmación con [CategoriasViewModel]. */
@Composable
fun CategoriasRoute(
    viewModel: CategoriasViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    CategoriasScreen(
        estado = estado,
        onNueva = viewModel::nuevaCategoria,
        onRenombrar = viewModel::renombrarCategoria,
        onDesactivar = viewModel::pedirDesactivar,
        onActivar = viewModel::activar,
        onReintentar = viewModel::cargar,
        onVolver = onVolver,
        modifier = modifier,
    )

    estado.formulario?.let { formulario ->
        val editando = formulario.editando != null
        AlertDialog(
            onDismissRequest = viewModel::cerrarFormulario,
            title = { Text(stringResource(if (editando) R.string.cat_form_titulo_renombrar else R.string.cat_form_titulo_nueva)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = formulario.nombre,
                        onValueChange = viewModel::onNombreChange,
                        label = { Text(stringResource(R.string.cat_form_nombre)) },
                        singleLine = true,
                        enabled = !formulario.guardando,
                        isError = formulario.errorNombre != null,
                        supportingText = formulario.errorNombre?.let { { Text(textoDe(it)) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    formulario.error?.let {
                        Text(textoDe(it), color = Error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::guardar, enabled = !formulario.guardando) {
                    Text(
                        stringResource(
                            when {
                                formulario.guardando -> R.string.cat_form_guardando
                                editando -> R.string.cat_form_guardar_nombre
                                else -> R.string.cat_form_crear
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cerrarFormulario, enabled = !formulario.guardando) {
                    Text(stringResource(R.string.cat_form_cancelar))
                }
            },
        )
    }

    estado.confirmarDesactivar?.let { categoria ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarDesactivar,
            title = { Text(stringResource(R.string.cat_confirmar_titulo, categoria.nombre)) },
            text = {
                Text(
                    text = if (categoria.totalProductos > 0) {
                        stringResource(R.string.cat_confirmar_con_productos, textoProductos(categoria.totalProductos))
                    } else {
                        stringResource(R.string.cat_confirmar_sin_productos)
                    },
                    color = Ink,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmarDesactivar) {
                    Text(stringResource(R.string.cat_confirmar_accion))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarDesactivar) {
                    Text(stringResource(R.string.cat_form_cancelar))
                }
            },
        )
    }
}
