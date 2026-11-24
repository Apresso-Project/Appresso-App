package com.appresso.app.ui.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.data.catalogo.MovimientoInventario
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.TipoAjuste
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.White
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
internal fun ResumenStockBajo(productos: List<Producto>, onVerInventario: () -> Unit) {
    if (productos.isEmpty()) return
    Surface(color = White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.inv_alertas_titulo), color = Error, style = MaterialTheme.typography.titleMedium)
            productos.take(3).forEach {
                Text(stringResource(R.string.inv_alerta_producto, it.nombre, it.stockActual, it.umbralMinimo))
            }
            if (productos.size > 3) {
                val restantes = productos.size - 3
                Text(pluralStringResource(R.plurals.inv_alertas_restantes, restantes, restantes))
            }
            TextButton(onClick = onVerInventario) { Text(stringResource(R.string.inv_ver_inventario)) }
        }
    }
}

@Composable
internal fun AjusteInventarioDialog(
    formulario: AjusteUi,
    vm: InventarioViewModel,
    alGuardar: (Producto) -> Unit,
) {
    var tiposAbiertos by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::cerrarAjuste,
        title = { Text(stringResource(R.string.inv_ajuste_titulo, formulario.producto.nombre)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.inv_stock_actual, formulario.producto.stockActual))
                OutlinedButton(onClick = { tiposAbiertos = true }, enabled = !formulario.guardando) {
                    Text(stringResource(textoTipo(formulario.tipo.name)))
                }
                DropdownMenu(expanded = tiposAbiertos, onDismissRequest = { tiposAbiertos = false }) {
                    TipoAjuste.entries.forEach { tipo ->
                        DropdownMenuItem(
                            text = { Text(stringResource(textoTipo(tipo.name))) },
                            onClick = {
                                vm.editarAjuste { it.copy(tipo = tipo) }
                                tiposAbiertos = false
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = formulario.cantidad,
                    onValueChange = { valor -> vm.editarAjuste { it.copy(cantidad = valor) } },
                    label = { Text(stringResource(R.string.inv_cantidad)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !formulario.guardando,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = formulario.concepto,
                    onValueChange = { valor -> vm.editarAjuste { it.copy(concepto = valor) } },
                    label = { Text(stringResource(R.string.inv_concepto)) },
                    enabled = !formulario.guardando,
                    modifier = Modifier.fillMaxWidth(),
                )
                formulario.error?.let {
                    Text(formulario.mensajeServidor ?: stringResource(it), color = Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.guardarAjuste(alGuardar) }, enabled = !formulario.guardando) {
                Text(stringResource(if (formulario.guardando) R.string.cat_form_guardando else R.string.prod_form_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cerrarAjuste, enabled = !formulario.guardando) {
                Text(stringResource(R.string.cat_form_cancelar))
            }
        },
    )
}

@Composable
internal fun HistorialInventarioRoute(vm: InventarioViewModel, onVolver: () -> Unit) {
    val estado by vm.historial.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargarHistorial() }

    PantallaProvisional(titulo = stringResource(R.string.inv_historial)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVolver) { Text(stringResource(R.string.inv_volver)) }
            OutlinedButton(onClick = vm::cargarHistorial, enabled = !estado.cargando) {
                Text(stringResource(R.string.inv_filtrar))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            item {
                OutlinedTextField(
                    value = estado.producto,
                    onValueChange = { valor -> vm.editarHistorial { it.copy(producto = valor) } },
                    label = { Text(stringResource(R.string.inv_filtro_producto)) },
                    enabled = !estado.cargando,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = estado.desde,
                        onValueChange = { valor -> vm.editarHistorial { it.copy(desde = valor) } },
                        label = { Text(stringResource(R.string.inv_desde)) },
                        enabled = !estado.cargando,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = estado.hasta,
                        onValueChange = { valor -> vm.editarHistorial { it.copy(hasta = valor) } },
                        label = { Text(stringResource(R.string.inv_hasta)) },
                        enabled = !estado.cargando,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            estado.error?.let { error -> item { Text(stringResource(error), color = Error) } }
            if (estado.cargando) {
                item { CircularProgressIndicator() }
            } else if (estado.movimientos.isEmpty() && estado.error == null) {
                item { Text(stringResource(R.string.inv_sin_movimientos)) }
            }
            items(estado.movimientos, key = { it.id }) { Movimiento(it) }
        }
    }
}

@Composable
private fun Movimiento(movimiento: MovimientoInventario) {
    val fecha = remember(movimiento.fecha) {
        try {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
                .format(Instant.parse(movimiento.fecha))
        } catch (e: DateTimeParseException) {
            movimiento.fecha
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(movimiento.producto, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.inv_movimiento, stringResource(textoTipo(movimiento.tipo)), movimiento.cantidad))
        Text(stringResource(R.string.inv_cambio_stock, movimiento.stockAnterior, movimiento.stockResultante))
        movimiento.concepto?.let { Text(it) }
        Text(stringResource(R.string.inv_fecha_usuario, fecha, movimiento.usuario), style = MaterialTheme.typography.bodySmall)
    }
}

private fun textoTipo(tipo: String): Int = when (tipo) {
    "ENTRADA_MANUAL" -> R.string.inv_entrada
    "SALIDA_MANUAL" -> R.string.inv_salida
    "AJUSTE_MERMA" -> R.string.inv_merma
    "VENTA" -> R.string.inv_venta
    else -> R.string.inv_movimiento_generico
}
