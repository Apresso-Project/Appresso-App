package com.appresso.app.ui.menu

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.menu.Disponibilidad
import com.appresso.app.data.menu.MenuProducto
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.catalogo.ImagenProducto
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Menú de solo lectura (SCRUM-91): una lista vertical de productos agrupados por categoría, cada
 * uno con imagen, descripción, precio y existencias, más un filtro por categoría. No tiene botones
 * de edición: solo dibuja lo que entrega [MenuViewModel].
 */
@Composable
fun MenuScreen(
    estado: MenuUiState,
    onFiltrar: (Long?) -> Unit,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.menu_titulo), modifier = modifier) {
        OutlinedButton(
            onClick = onVolver,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, Espresso),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.menu_volver), style = MaterialTheme.typography.titleMedium) }

        // Con una sola categoría el filtro no aporta nada.
        if (estado.categorias.size > 1) FiltroCategorias(estado, onFiltrar)

        if (estado.desactualizado) MensajeConBorde(stringResource(R.string.menu_desactualizado))

        when {
            estado.cargando && estado.categorias.isEmpty() -> Centrado {
                val descripcion = stringResource(R.string.menu_cargando)
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = descripcion },
                )
            }

            estado.error != null && estado.categorias.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.error))
                OutlinedButton(
                    onClick = onReintentar,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Espresso),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.cat_reintentar), style = MaterialTheme.typography.titleMedium) }
            }

            estado.categorias.isEmpty() -> Centrado {
                Text(
                    stringResource(R.string.menu_vacio),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                estado.visibles.forEach { categoria ->
                    item(key = "categoria-${categoria.id}") {
                        Text(
                            text = categoria.nombre,
                            style = MaterialTheme.typography.titleLarge,
                            color = Espresso,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .semantics { heading() },
                        )
                    }
                    items(categoria.productos, key = { "producto-${it.id}" }) { TarjetaMenuProducto(it) }
                }
            }
        }
    }
}

@Composable
private fun FiltroCategorias(estado: MenuUiState, onFiltrar: (Long?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = estado.filtroCategoriaId == null,
            onClick = { onFiltrar(null) },
            label = { Text(stringResource(R.string.menu_filtro_todas)) },
        )
        estado.categorias.forEach { categoria ->
            FilterChip(
                selected = estado.filtroCategoriaId == categoria.id,
                onClick = { onFiltrar(categoria.id) },
                label = { Text(categoria.nombre) },
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
private fun TarjetaMenuProducto(producto: MenuProducto) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White)
            .padding(16.dp),
    ) {
        ImagenProducto(url = producto.imagenUrl, nombre = producto.nombre, lado = 88.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(producto.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
            producto.descripcion?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
            Text(formatoPesos(producto.precio), style = MaterialTheme.typography.titleMedium, color = Espresso)
            IndicadorExistencias(producto)
        }
    }
}

/** La cantidad y el estado van en texto (RNF-17); el color solo acompaña. */
@Composable
private fun IndicadorExistencias(producto: MenuProducto) {
    val pocas = producto.disponibilidad == Disponibilidad.POCAS_UNIDADES
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (pocas) Caramel else Success),
        )
        Text(
            text = stringResource(
                if (pocas) R.string.menu_stock_pocas else R.string.menu_stock_disponible,
                producto.existencias,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (pocas) Ink else Success,
        )
    }
}

@Composable
private fun textoDe(error: ErrorMenu): String = when (error) {
    ErrorMenu.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorMenu.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    is ErrorMenu.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
}

/** «$ 6.500» (pesos colombianos, sin decimales si no los tiene). */
internal fun formatoPesos(valor: BigDecimal): String {
    val formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "$ " + formato.format(valor)
}
