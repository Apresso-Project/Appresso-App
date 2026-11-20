package com.appresso.app.ui.catalogo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

/**
 * Lista de categorías del catálogo (SCRUM-71, solo administrador). Misma cabecera espresso y cuerpo
 * crema que las demás pantallas. El estado y las acciones vienen de [CategoriasViewModel]; aquí solo
 * se dibuja.
 */
@Composable
fun CategoriasScreen(
    estado: CategoriasUiState,
    onNueva: () -> Unit,
    onRenombrar: (Categoria) -> Unit,
    onDesactivar: (Categoria) -> Unit,
    onActivar: (Categoria) -> Unit,
    onReintentar: () -> Unit,
    onVerProductos: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.cat_titulo), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNueva,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso, contentColor = Cream),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.cat_nueva), style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = onVolver,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Espresso),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.cat_volver), style = MaterialTheme.typography.titleMedium) }
        }

        OutlinedButton(
            onClick = onVerProductos,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, Espresso),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.cat_ver_productos), style = MaterialTheme.typography.titleMedium) }

        estado.errorAccion?.let { MensajeConBorde(textoDe(it)) }

        when {
            estado.cargando && estado.categorias.isEmpty() -> Centrado {
                val descripcion = stringResource(R.string.cat_cargando)
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = descripcion },
                )
            }

            estado.errorLista != null && estado.categorias.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.errorLista))
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
                    stringResource(R.string.cat_vacio),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(estado.categorias, key = { it.id }) { categoria ->
                    TarjetaCategoria(
                        categoria = categoria,
                        ocupada = estado.procesandoId == categoria.id,
                        onRenombrar = { onRenombrar(categoria) },
                        onDesactivar = { onDesactivar(categoria) },
                        onActivar = { onActivar(categoria) },
                    )
                }
            }
        }
    }
}

/** Contenido centrado en el espacio que queda bajo los botones (cargando, vacío y error). */
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

/** Mensaje de error en tarjeta blanca con borde rojo; el texto va en color de tinta para leerse bien. */
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
private fun TarjetaCategoria(
    categoria: Categoria,
    ocupada: Boolean,
    onRenombrar: () -> Unit,
    onDesactivar: () -> Unit,
    onActivar: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(categoria.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = textoProductos(categoria.totalProductos),
                    style = MaterialTheme.typography.labelSmall,
                    color = Espresso,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Cream)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                // El estado va también en texto, no solo en color.
                val color = if (categoria.activa) Success else Error
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Text(
                        text = stringResource(if (categoria.activa) R.string.cat_estado_activa else R.string.cat_estado_inactiva),
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                }
            }
            if (!categoria.activa && categoria.totalProductos > 0) {
                Text(
                    text = stringResource(R.string.cat_inactiva_aviso),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
            AccionTexto(R.string.cat_renombrar, Espresso, !ocupada, onRenombrar)
            if (categoria.activa) {
                AccionTexto(R.string.cat_desactivar, Error, !ocupada, onDesactivar)
            } else {
                AccionTexto(R.string.cat_activar, Success, !ocupada, onActivar)
            }
            if (ocupada) {
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                )
            }
        }
    }
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
