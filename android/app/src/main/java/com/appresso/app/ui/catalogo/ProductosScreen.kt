package com.appresso.app.ui.catalogo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White
import java.text.NumberFormat
import java.util.Locale

/**
 * Lista de productos del catálogo (SCRUM-72, solo administrador): imagen, precio, existencias con
 * su estado (RNF-17) y si están en el menú. Filtro por categoría. Solo dibuja: el estado y las
 * acciones vienen de [ProductosViewModel].
 */
@Composable
fun ProductosScreen(
    estado: ProductosUiState,
    onNuevo: () -> Unit,
    onEditar: (Producto) -> Unit,
    onDesactivar: (Producto) -> Unit,
    onActivar: (Producto) -> Unit,
    onAjustar: (Producto) -> Unit,
    onVerHistorial: () -> Unit,
    onFiltrar: (Long?) -> Unit,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.prod_titulo), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNuevo,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso, contentColor = Cream),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.prod_nuevo), style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = onVolver,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Espresso),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.prod_ver_categorias), style = MaterialTheme.typography.titleMedium) }
        }

        OutlinedButton(onClick = onVerHistorial) { Text(stringResource(R.string.inv_historial)) }

        estado.errorAccion?.let { MensajeConBorde(textoDe(it)) }

        if (estado.productos.isNotEmpty()) {
            FiltroCategorias(estado, onFiltrar)
        }

        when {
            estado.cargando && estado.productos.isEmpty() -> Centrado {
                val descripcion = stringResource(R.string.prod_cargando)
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = descripcion },
                )
            }

            estado.errorLista != null && estado.productos.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.errorLista))
                OutlinedButton(
                    onClick = onReintentar,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Espresso),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.cat_reintentar), style = MaterialTheme.typography.titleMedium) }
            }

            estado.productos.isEmpty() -> Centrado {
                Text(
                    stringResource(R.string.prod_vacio),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            estado.visibles.isEmpty() -> Centrado {
                Text(
                    stringResource(R.string.prod_vacio_categoria),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(estado.visibles, key = { it.id }) { producto ->
                    TarjetaProducto(
                        producto = producto,
                        ocupado = estado.procesandoId == producto.id,
                        onEditar = { onEditar(producto) },
                        onDesactivar = { onDesactivar(producto) },
                        onActivar = { onActivar(producto) },
                        onAjustar = { onAjustar(producto) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltroCategorias(estado: ProductosUiState, onFiltrar: (Long?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = estado.filtroCategoriaId == null,
            onClick = { onFiltrar(null) },
            label = { Text(stringResource(R.string.prod_filtro_todas)) },
        )
        // Solo las categorías que tienen productos en la lista; así el filtro nunca queda vacío.
        estado.productos
            .distinctBy { it.categoriaId }
            .sortedBy { it.categoriaNombre.lowercase() }
            .forEach { p ->
                FilterChip(
                    selected = estado.filtroCategoriaId == p.categoriaId,
                    onClick = { onFiltrar(p.categoriaId) },
                    label = { Text(p.categoriaNombre) },
                )
            }
    }
}

@Composable
private fun ColumnScope.Centrado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = contenido,
    )
}

@Composable
private fun MensajeConBorde(texto: String) {
    Text(
        text = texto,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.5.dp, Error, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun TarjetaProducto(
    producto: Producto,
    ocupado: Boolean,
    onEditar: () -> Unit,
    onDesactivar: () -> Unit,
    onActivar: () -> Unit,
    onAjustar: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        ) {
            ImagenProducto(url = producto.imagenUrl, nombre = producto.nombre, lado = 88.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    text = stringResource(R.string.prod_precio_categoria, formatoPrecio(producto), producto.categoriaNombre),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                IndicadorExistencias(producto)
                IndicadorMenu(producto)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cream),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            AccionTexto(R.string.prod_editar, Espresso, !ocupado, onEditar)
            if (producto.activo) {
                AccionTexto(R.string.prod_desactivar, Error, !ocupado, onDesactivar)
            } else {
                AccionTexto(R.string.prod_activar, Success, !ocupado, onActivar)
            }
            if (ocupado) {
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                )
            }
        }
        AccionTexto(R.string.inv_ajustar, Espresso, !ocupado, onAjustar)
    }
}

/**
 * Existencias con la cantidad y un estado en texto (RNF-17). Los colores oscuros (contraste de al
 * menos 4,5:1 sobre blanco) acompañan al texto, no lo reemplazan.
 */
@Composable
private fun IndicadorExistencias(producto: Producto) {
    val estado = producto.estadoExistencias()
    val color = when (estado) {
        EstadoExistencias.AGOTADO -> Error
        EstadoExistencias.POCAS_UNIDADES -> Ink
        EstadoExistencias.DISPONIBLE -> Success
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (estado == EstadoExistencias.POCAS_UNIDADES) Caramel else color),
        )
        Text(
            text = textoExistencias(estado, producto.stockActual),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

/** Si el producto no aparece en el menú, dice por qué: desactivado o categoría desactivada. */
@Composable
private fun IndicadorMenu(producto: Producto) {
    val texto = when {
        !producto.activo -> R.string.prod_fuera_desactivado
        !producto.categoriaActiva -> R.string.prod_fuera_categoria
        else -> R.string.prod_en_menu
    }
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.labelSmall,
        color = if (producto.enElMenu) Muted else Error,
    )
}

@Composable
private fun AccionTexto(texto: Int, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = habilitado,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.4f)),
        modifier = Modifier.heightIn(min = 44.dp),
    ) { Text(stringResource(texto), style = MaterialTheme.typography.titleMedium) }
}

/** «$ 6.500» (pesos colombianos, sin decimales si no los tiene). */
private fun formatoPrecio(producto: Producto): String {
    val formato = NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "$ " + formato.format(producto.precio)
}
