package com.appresso.app.ui.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Ink

/** Conecta la lista, el formulario y la confirmación de desactivar con [ProductosViewModel]. */
@Composable
fun ProductosRoute(
    viewModel: ProductosViewModel,
    inventario: InventarioViewModel,
    onVerHistorial: () -> Unit,
    onVerCategorias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ajuste by inventario.ajuste.collectAsStateWithLifecycle()

    ProductosScreen(
        estado = estado,
        onNuevo = viewModel::nuevoProducto,
        onEditar = viewModel::editarProducto,
        onDesactivar = viewModel::pedirDesactivar,
        onActivar = viewModel::activar,
        onAjustar = inventario::abrirAjuste,
        onVerHistorial = onVerHistorial,
        onFiltrar = viewModel::filtrarPorCategoria,
        onReintentar = viewModel::cargar,
        onVolver = onVerCategorias,
        modifier = modifier,
    )

    ajuste?.let { formulario ->
        AjusteInventarioDialog(formulario, inventario, viewModel::actualizarTrasAjuste)
    }

    estado.formulario?.let { formulario ->
        FormularioProductoDialog(estado, formulario, viewModel)
    }

    estado.confirmarDesactivar?.let { producto ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarDesactivar,
            title = { Text(stringResource(R.string.prod_confirmar_titulo, producto.nombre)) },
            text = { Text(stringResource(R.string.prod_confirmar_texto), color = Ink) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmarDesactivar) {
                    Text(stringResource(R.string.prod_confirmar_accion))
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

@Composable
private fun FormularioProductoDialog(
    estado: ProductosUiState,
    f: FormularioProductoUiState,
    vm: ProductosViewModel,
) {
    val editando = f.editando != null
    AlertDialog(
        onDismissRequest = vm::cerrarFormulario,
        title = { Text(stringResource(if (editando) R.string.prod_form_titulo_editar else R.string.prod_form_titulo_nuevo)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Campo(R.string.prod_form_nombre, f.nombre, vm::onNombreChange, f, CampoProducto.NOMBRE, capitalizar = true)
                Campo(R.string.prod_form_descripcion, f.descripcion, vm::onDescripcionChange, f, CampoProducto.DESCRIPCION, capitalizar = true, unaLinea = false)
                Campo(R.string.prod_form_precio, f.precio, vm::onPrecioChange, f, CampoProducto.PRECIO, teclado = KeyboardType.Decimal)
                SelectorCategoria(estado, f, vm)
                Campo(R.string.prod_form_imagen, f.imagenUrl, vm::onImagenChange, f, CampoProducto.IMAGEN, teclado = KeyboardType.Uri)
                if (!editando) {
                    Campo(R.string.prod_form_existencias, f.existencias, vm::onExistenciasChange, f, CampoProducto.EXISTENCIAS, teclado = KeyboardType.Number)
                } else {
                    // Las existencias no se editan aquí (RF-07): se muestran para que se sepa por qué.
                    Text(
                        text = stringResource(R.string.prod_form_existencias_fijas, f.editando!!.stockActual),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                    )
                }
                Campo(R.string.prod_form_umbral, f.umbral, vm::onUmbralChange, f, CampoProducto.UMBRAL, teclado = KeyboardType.Number)
                f.error?.let { Text(textoDe(it), color = Error) }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::guardar, enabled = !f.guardando) {
                Text(
                    stringResource(
                        when {
                            f.guardando -> R.string.cat_form_guardando
                            editando -> R.string.prod_form_guardar
                            else -> R.string.prod_form_crear
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cerrarFormulario, enabled = !f.guardando) {
                Text(stringResource(R.string.cat_form_cancelar))
            }
        },
    )
}

@Composable
private fun Campo(
    etiqueta: Int,
    valor: String,
    onCambio: (String) -> Unit,
    f: FormularioProductoUiState,
    campo: CampoProducto,
    teclado: KeyboardType = KeyboardType.Text,
    capitalizar: Boolean = false,
    unaLinea: Boolean = true,
) {
    val error = f.errores[campo]
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(stringResource(etiqueta)) },
        singleLine = unaLinea,
        enabled = !f.guardando,
        isError = error != null,
        supportingText = error?.let { { Text(textoDe(it)) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = teclado,
            capitalization = if (capitalizar) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lista desplegable de categorías activas; al editar se conserva la actual aunque esté desactivada. */
@Composable
private fun SelectorCategoria(estado: ProductosUiState, f: FormularioProductoUiState, vm: ProductosViewModel) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = estado.categorias.map { it.id to it.nombre } +
        listOfNotNull(f.editando?.takeIf { e -> estado.categorias.none { it.id == e.categoriaId } }?.let { it.categoriaId to it.categoriaNombre })
    val elegida = opciones.firstOrNull { it.first == f.categoriaId }?.second
    val error = f.errores[CampoProducto.CATEGORIA]

    Column {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = !f.guardando,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(elegida ?: stringResource(R.string.prod_form_categoria_elegir))
                Text("▾")
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, nombre) ->
                DropdownMenuItem(
                    text = { Text(nombre) },
                    onClick = {
                        vm.onCategoriaChange(id)
                        abierto = false
                    },
                )
            }
        }
        if (error != null) {
            Text(textoDe(error), color = Error, style = MaterialTheme.typography.bodySmall)
        } else if (opciones.isEmpty()) {
            Text(stringResource(R.string.prod_form_sin_categorias), style = MaterialTheme.typography.bodySmall, color = Ink)
        }
    }
}
