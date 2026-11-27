package com.appresso.app.ui.comandas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.comandas.CalculadoraComanda
import com.appresso.app.data.comandas.Comanda
import com.appresso.app.data.comandas.EstadoComanda
import com.appresso.app.data.menu.MenuProducto
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.menu.formatoPesos
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.Warning
import com.appresso.app.ui.theme.White
import java.math.BigDecimal

/** Armado, resumen previo y detalle guardado con los importes del servidor. */
@Composable
fun ComandaScreen(
    estado: ComandaUiState,
    onFiltrar: (Long?) -> Unit,
    onAgregar: (MenuProducto) -> Unit,
    onQuitar: (MenuProducto) -> Unit,
    onObservaciones: (Long, String) -> Unit,
    onVerResumen: () -> Unit,
    onSeguirEditando: () -> Unit,
    onGuardar: () -> Unit,
    onReintentarMenu: () -> Unit,
    onReintentarDetalle: () -> Unit,
    onVolverAMesas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titulo = when (estado.paso) {
        PasoComanda.ARMAR -> stringResource(R.string.comanda_titulo, estado.mesaNombre)
        PasoComanda.RESUMEN -> stringResource(R.string.comanda_resumen_titulo, estado.mesaNombre)
        PasoComanda.DETALLE -> stringResource(R.string.comanda_titulo, estado.mesaNombre)
    }
    PantallaProvisional(titulo = titulo, modifier = modifier) {
        when (estado.paso) {
            PasoComanda.ARMAR -> Armar(
                estado, onFiltrar, onAgregar, onQuitar, onObservaciones, onVerResumen, onReintentarMenu, onVolverAMesas,
            )
            PasoComanda.RESUMEN -> Resumen(estado, onSeguirEditando, onGuardar, onReintentarMenu, onVolverAMesas)
            PasoComanda.DETALLE -> Detalle(estado, onReintentarDetalle, onVolverAMesas)
        }
    }
}

// ------------------------------------------------------------------------------------ armar

@Composable
private fun ColumnScope.Armar(
    estado: ComandaUiState,
    onFiltrar: (Long?) -> Unit,
    onAgregar: (MenuProducto) -> Unit,
    onQuitar: (MenuProducto) -> Unit,
    onObservaciones: (Long, String) -> Unit,
    onVerResumen: () -> Unit,
    onReintentarMenu: () -> Unit,
    onVolverAMesas: () -> Unit,
) {
    BotonSecundario(stringResource(R.string.comanda_volver_mesas), onVolverAMesas)

    if (estado.categorias.size > 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ChipCategoria(stringResource(R.string.comanda_filtro_todas), estado.filtroCategoriaId == null) { onFiltrar(null) }
            estado.categorias.forEach { c ->
                ChipCategoria(c.nombre, estado.filtroCategoriaId == c.id) { onFiltrar(c.id) }
            }
        }
    }

    when {
        estado.cargandoMenu && estado.categorias.isEmpty() ->
            Centrado { Cargando(stringResource(R.string.comanda_cargando_menu)) }

        estado.errorMenu != null && estado.categorias.isEmpty() -> Centrado {
            MensajeConBorde(textoDe(estado.errorMenu))
            BotonSecundario(stringResource(R.string.cat_reintentar), onReintentarMenu)
        }

        estado.categorias.isEmpty() -> Centrado {
            Text(
                stringResource(R.string.comanda_menu_vacio),
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
                        categoria.nombre,
                        style = MaterialTheme.typography.titleLarge,
                        color = Espresso,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(categoria.productos, key = { "producto-${it.id}" }) { producto ->
                    TarjetaProducto(
                        producto = producto,
                        cantidad = estado.cantidadDe(producto.id),
                        editable = estado.editable,
                        observaciones = estado.observaciones[producto.id].orEmpty(),
                        onObservaciones = { onObservaciones(producto.id, it) },
                        onAgregar = { onAgregar(producto) },
                        onQuitar = { onQuitar(producto) },
                    )
                }
            }
        }
    }

    BarraTotal(estado, onVerResumen)
}

@Composable
private fun ChipCategoria(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = { Text(texto, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = White,
            labelColor = Ink,
            selectedContainerColor = Espresso,
            selectedLabelColor = Cream,
        ),
    )
}

@Composable
private fun TarjetaProducto(
    producto: MenuProducto,
    cantidad: Int,
    editable: Boolean,
    observaciones: String,
    onObservaciones: (String) -> Unit,
    onAgregar: () -> Unit,
    onQuitar: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    val seleccionado = cantidad > 0
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White)
            .border(1.5.dp, if (seleccionado) Caramel else White, forma)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(producto.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(formatoPesos(producto.precio), style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (seleccionado) {
                    Text(
                        formatoPesos(CalculadoraComanda.subtotalLinea(producto.precio, cantidad)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Espresso,
                    )
                }
                if (cantidad >= producto.existencias) {
                    Text(stringResource(R.string.comanda_sin_mas_stock), style = MaterialTheme.typography.labelSmall, color = Warning)
                }
            }
            Selector(
                nombre = producto.nombre,
                cantidad = cantidad,
                puedeQuitar = editable && cantidad > 0,
                puedeAgregar = editable && cantidad < producto.existencias,
                onQuitar = onQuitar,
                onAgregar = onAgregar,
            )
        }
        if (seleccionado) {
            CampoTexto(
                etiqueta = stringResource(R.string.comanda_observaciones),
                placeholder = stringResource(R.string.comanda_observaciones_ayuda, MAX_OBSERVACIONES_COMANDA),
                valor = observaciones,
                onValorChange = onObservaciones,
                teclado = KeyboardOptions.Default,
                soloLectura = !editable,
            )
        }
    }
}

/** − cantidad +, con botones de 48 dp (área táctil mínima de la guía). */
@Composable
private fun Selector(
    nombre: String,
    cantidad: Int,
    puedeQuitar: Boolean,
    puedeAgregar: Boolean,
    onQuitar: () -> Unit,
    onAgregar: () -> Unit,
) {
    val descripcionCantidad = stringResource(R.string.comanda_cantidad, cantidad, nombre)
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo("−", stringResource(R.string.comanda_quitar, nombre), puedeQuitar, relleno = false, onQuitar)
        Text(
            cantidad.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = if (cantidad > 0) Espresso else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .semantics { contentDescription = descripcionCantidad },
        )
        BotonRedondo("+", stringResource(R.string.comanda_agregar, nombre), puedeAgregar, relleno = true, onAgregar)
    }
}

@Composable
private fun BotonRedondo(simbolo: String, descripcion: String, habilitado: Boolean, relleno: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = descripcion },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (relleno) Espresso else Cream,
            contentColor = if (relleno) Cream else Espresso,
            disabledContainerColor = if (relleno) Espresso.copy(alpha = 0.25f) else Cream,
            disabledContentColor = if (relleno) Cream else Muted.copy(alpha = 0.5f),
        ),
    ) {
        Text(simbolo, style = MaterialTheme.typography.titleLarge)
    }
}

/** Abajo, siempre a la vista: cuántos productos lleva, el total y el botón al resumen. */
@Composable
private fun BarraTotal(estado: ComandaUiState, onVerResumen: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, forma, ambientColor = Ink.copy(alpha = 0.10f), spotColor = Ink.copy(alpha = 0.10f))
            .clip(forma)
            .background(White)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.comanda_unidades, estado.unidades, estado.unidades),
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
            )
            Text(formatoPesos(estado.importes.total), style = MaterialTheme.typography.headlineMedium, color = Espresso)
        }
        when {
            estado.error is ErrorComanda.Vacia -> MensajeConBorde(textoDe(estado.error))
            estado.vacia -> Text(
                stringResource(R.string.comanda_vacia_ayuda),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
        BotonPrimario(
            stringResource(R.string.comanda_ver_resumen),
            onClick = onVerResumen,
            habilitado = !estado.vacia,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ------------------------------------------------------------------------------------ resumen

@Composable
private fun ColumnScope.Resumen(
    estado: ComandaUiState,
    onSeguirEditando: () -> Unit,
    onGuardar: () -> Unit,
    onReintentarMenu: () -> Unit,
    onVolverAMesas: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
    ) {
        item {
            Tarjeta {
                estado.lineas.forEachIndexed { i, linea ->
                    if (i > 0) HorizontalDivider(color = Espresso.copy(alpha = 0.1f))
                    FilaLinea(
                        nombre = linea.producto.nombre,
                        detalle = stringResource(R.string.comanda_linea_cantidad, linea.cantidad, formatoPesos(linea.producto.precio)),
                        subtotal = formatoPesos(linea.subtotal),
                        observaciones = linea.observaciones,
                    )
                }
            }
        }
        item { Importes(estado.importes.subtotal, estado.importes.descuento, estado.importes.total) }
    }

    estado.error?.let { MensajeConBorde(textoDe(it)) }
    if (estado.cargandoMenu) {
        Cargando(stringResource(R.string.comanda_actualizando_menu))
    } else if (estado.errorMenu != null) {
        MensajeConBorde(textoDe(estado.errorMenu))
        BotonSecundario(stringResource(R.string.cat_reintentar), onReintentarMenu)
    }
    BotonPrimario(
        stringResource(if (estado.guardando) R.string.comanda_guardando else R.string.comanda_guardar),
        onClick = onGuardar,
        habilitado = estado.puedeGuardar,
        modifier = Modifier.fillMaxWidth(),
    )
    BotonSecundario(
        stringResource(R.string.comanda_seguir_editando),
        onSeguirEditando,
        Modifier.fillMaxWidth(),
        habilitado = !estado.guardando,
    )
    if (estado.error != null) {
        BotonSecundario(
            stringResource(R.string.comanda_volver_mesas),
            onVolverAMesas,
            habilitado = !estado.guardando,
        )
    }
}

// ------------------------------------------------------------------------------------ detalle

@Composable
private fun ColumnScope.Detalle(estado: ComandaUiState, onReintentar: () -> Unit, onVolverAMesas: () -> Unit) {
    val guardada = estado.guardada
    if (guardada != null) {
        DetalleGuardado(guardada)
    } else {
        Centrado {
            if (estado.cargandoDetalle) Cargando(stringResource(R.string.comanda_cargando_detalle))
            estado.error?.let {
                MensajeConBorde(textoDe(it))
                BotonSecundario(stringResource(R.string.cat_reintentar), onReintentar)
            }
        }
    }
    BotonPrimario(stringResource(R.string.comanda_volver_mesas), onVolverAMesas, Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.DetalleGuardado(comanda: Comanda) {
    val estado = stringResource(
        when (comanda.estado) {
            EstadoComanda.PENDIENTE -> R.string.comanda_estado_pendiente
            EstadoComanda.ENVIADA -> R.string.comanda_estado_enviada
            EstadoComanda.EN_PREPARACION -> R.string.cocina_estado_en_preparacion
            EstadoComanda.LISTA -> R.string.cocina_estado_lista
            EstadoComanda.CERRADA -> R.string.comanda_estado_cerrada
        },
    )
    MensajeConBorde(stringResource(R.string.comanda_detalle_estado, comanda.id, estado), color = Success)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
    ) {
        item {
            Tarjeta {
                comanda.lineas.forEachIndexed { i, linea ->
                    if (i > 0) HorizontalDivider(color = Espresso.copy(alpha = 0.1f))
                    FilaLinea(
                        nombre = linea.productoNombre,
                        detalle = stringResource(R.string.comanda_linea_cantidad, linea.cantidad, formatoPesos(linea.precioUnitario)),
                        subtotal = formatoPesos(linea.subtotal),
                        observaciones = linea.observaciones,
                    )
                }
            }
        }
        item { Importes(comanda.subtotal, comanda.descuento, comanda.total) }
    }
}

// ------------------------------------------------------------------------------------ piezas

@Composable
private fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White)
            .padding(16.dp),
        content = contenido,
    )
}

@Composable
private fun FilaLinea(nombre: String, detalle: String, subtotal: String, observaciones: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(detalle, style = MaterialTheme.typography.bodyMedium, color = Muted)
            observaciones?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink) }
        }
        Text(subtotal, style = MaterialTheme.typography.titleMedium, color = Espresso)
    }
}

/** Subtotal, descuento (solo si hay) y total: total = subtotal − descuento. */
@Composable
private fun Importes(subtotal: BigDecimal, descuento: BigDecimal, total: BigDecimal) {
    Tarjeta {
        FilaImporte(stringResource(R.string.comanda_subtotal), formatoPesos(subtotal))
        if (descuento.signum() > 0) {
            FilaImporte(stringResource(R.string.comanda_descuento), "− " + formatoPesos(descuento))
        }
        HorizontalDivider(color = Espresso.copy(alpha = 0.1f))
        FilaImporte(stringResource(R.string.comanda_total), formatoPesos(total), destacado = true)
    }
}
