package com.appresso.app.ui.catalogo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appresso.app.R
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.Warning
import com.appresso.app.ui.theme.White

@Composable
fun CatalogoScreen(
    state: CatalogoUiState,
    onVolver: () -> Unit,
    onCambiarPestaña: (PestañaCatalogo) -> Unit,
    onBusquedaCambio: (String) -> Unit,
    onFiltrarCategoria: (Long?) -> Unit,
    onAbrirCrearCategoria: () -> Unit,
    onCerrarCrearCategoria: () -> Unit,
    onGuardarCategoria: (String) -> Unit,
    onAbrirEditarCategoria: (Categoria) -> Unit,
    onCerrarEditarCategoria: () -> Unit,
    onRenombrarCategoria: (Long, String) -> Unit,
    onToggleEstadoCategoria: (Categoria) -> Unit,
    onAbrirCrearProducto: () -> Unit,
    onCerrarCrearProducto: () -> Unit,
    onGuardarCrearProducto: (String, String?, Double, Long, String?, Int?, Int?) -> Unit,
    onAbrirEditarProducto: (Producto) -> Unit,
    onCerrarEditarProducto: () -> Unit,
    onGuardarEditarProducto: (String, String?, Double, Long, String?, Int?) -> Unit,
    onToggleEstadoProducto: (Producto) -> Unit,
    onLimpiarMensajes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.mensajeExito, state.errorGeneral) {
        val mensaje = state.mensajeExito ?: state.errorGeneral
        if (mensaje != null) {
            snackbarHostState.showSnackbar(mensaje)
            onLimpiarMensajes()
        }
    }

    if (state.mostrarDialogoCrearCategoria) {
        CategoriaFormularioDialog(
            categoria = null,
            guardando = state.guardando,
            error = state.errorFormulario,
            onDismiss = onCerrarCrearCategoria,
            onGuardar = onGuardarCategoria,
        )
    }

    state.categoriaAEditar?.let { cat ->
        CategoriaFormularioDialog(
            categoria = cat,
            guardando = state.guardando,
            error = state.errorFormulario,
            onDismiss = onCerrarEditarCategoria,
            onGuardar = { nuevoNombre -> onRenombrarCategoria(cat.id, nuevoNombre) },
        )
    }

    if (state.mostrarDialogoCrearProducto) {
        ProductoFormularioDialog(
            producto = null,
            categorias = state.categorias,
            guardando = state.guardando,
            error = state.errorFormulario,
            onDismiss = onCerrarCrearProducto,
            onGuardarCrear = onGuardarCrearProducto,
            onGuardarEditar = { _, _, _, _, _, _ -> },
        )
    }

    state.productoAEditar?.let { prod ->
        ProductoFormularioDialog(
            producto = prod,
            categorias = state.categorias,
            guardando = state.guardando,
            error = state.errorFormulario,
            onDismiss = onCerrarEditarProducto,
            onGuardarCrear = { _, _, _, _, _, _, _ -> },
            onGuardarEditar = { nombre, desc, precio, catId, imgUrl, umbral ->
                onGuardarEditarProducto(nombre, desc, precio, catId, imgUrl, umbral)
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CabeceraCatalogo(
                pestañaActual = state.pestañaSeleccionada,
                esAdministrador = state.esAdministrador,
                onVolver = onVolver,
                onNuevoElemento = {
                    if (state.pestañaSeleccionada == PestañaCatalogo.PRODUCTOS) {
                        onAbrirCrearProducto()
                    } else {
                        onAbrirCrearCategoria()
                    }
                },
            )
        },
        containerColor = Cream,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab row de navegación
            TabRow(
                selectedTabIndex = state.pestañaSeleccionada.ordinal,
                containerColor = White,
                contentColor = Espresso,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[state.pestañaSeleccionada.ordinal]),
                        color = Caramel,
                    )
                },
            ) {
                Tab(
                    selected = state.pestañaSeleccionada == PestañaCatalogo.PRODUCTOS,
                    onClick = { onCambiarPestaña(PestañaCatalogo.PRODUCTOS) },
                    text = {
                        Text(
                            stringResource(R.string.catalogo_tab_productos),
                            fontWeight = FontWeight.Bold,
                            color = if (state.pestañaSeleccionada == PestañaCatalogo.PRODUCTOS) Espresso else Muted,
                        )
                    },
                )
                Tab(
                    selected = state.pestañaSeleccionada == PestañaCatalogo.CATEGORIAS,
                    onClick = { onCambiarPestaña(PestañaCatalogo.CATEGORIAS) },
                    text = {
                        Text(
                            stringResource(R.string.catalogo_tab_categorias),
                            fontWeight = FontWeight.Bold,
                            color = if (state.pestañaSeleccionada == PestañaCatalogo.CATEGORIAS) Espresso else Muted,
                        )
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.busqueda,
                    onValueChange = onBusquedaCambio,
                    label = { Text(stringResource(R.string.catalogo_buscar_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (state.pestañaSeleccionada == PestañaCatalogo.PRODUCTOS) {
                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item {
                            FilterChip(
                                selected = state.categoriaFiltroId == null,
                                onClick = { onFiltrarCategoria(null) },
                                label = { Text(stringResource(R.string.catalogo_filtro_todas_categorias)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Caramel,
                                    selectedLabelColor = White,
                                ),
                            )
                        }
                        items(state.categorias, key = { it.id }) { cat ->
                            FilterChip(
                                selected = state.categoriaFiltroId == cat.id,
                                onClick = { onFiltrarCategoria(if (state.categoriaFiltroId == cat.id) null else cat.id) },
                                label = { Text(cat.nombre + if (!cat.activa) " (Inactiva)" else "") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Caramel,
                                    selectedLabelColor = White,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (state.cargando && state.productos.isEmpty() && state.categorias.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Espresso)
                    }
                } else {
                    when (state.pestañaSeleccionada) {
                        PestañaCatalogo.PRODUCTOS -> {
                            val productosFiltrados = state.productos.filter { p ->
                                val coincideBusqueda = state.busqueda.isBlank() ||
                                    p.nombre.contains(state.busqueda.trim(), ignoreCase = true) ||
                                    p.categoriaNombre.contains(state.busqueda.trim(), ignoreCase = true)
                                val coincideCat = state.categoriaFiltroId == null || p.categoriaId == state.categoriaFiltroId
                                coincideBusqueda && coincideCat
                            }

                            if (productosFiltrados.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.producto_sin_resultados),
                                        color = Muted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(productosFiltrados, key = { it.id }) { prod ->
                                        TarjetaProducto(
                                            producto = prod,
                                            esAdministrador = state.esAdministrador,
                                            onEditar = { onAbrirEditarProducto(prod) },
                                            onToggleEstado = { onToggleEstadoProducto(prod) },
                                        )
                                    }
                                    item { Spacer(Modifier.height(16.dp)) }
                                }
                            }
                        }

                        PestañaCatalogo.CATEGORIAS -> {
                            val categoriasFiltradas = state.categorias.filter { c ->
                                state.busqueda.isBlank() || c.nombre.contains(state.busqueda.trim(), ignoreCase = true)
                            }

                            if (categoriasFiltradas.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.categoria_sin_resultados),
                                        color = Muted,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(categoriasFiltradas, key = { it.id }) { cat ->
                                        TarjetaCategoria(
                                            categoria = cat,
                                            esAdministrador = state.esAdministrador,
                                            onEditar = { onAbrirEditarCategoria(cat) },
                                            onToggleEstado = { onToggleEstadoCategoria(cat) },
                                        )
                                    }
                                    item { Spacer(Modifier.height(16.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabeceraCatalogo(
    pestañaActual: PestañaCatalogo,
    esAdministrador: Boolean,
    onVolver: () -> Unit,
    onNuevoElemento: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Espresso)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) {
                    Text("←", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(R.string.catalogo_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Cream,
                )
            }

            if (esAdministrador) {
                Button(
                    onClick = onNuevoElemento,
                    colors = ButtonDefaults.buttonColors(containerColor = Caramel),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (pestañaActual == PestañaCatalogo.PRODUCTOS) R.string.catalogo_nuevo_producto else R.string.catalogo_nueva_categoria,
                        ),
                        color = White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaProducto(
    producto: Producto,
    esAdministrador: Boolean,
    onEditar: () -> Unit,
    onToggleEstado: () -> Unit,
) {
    val enMenu = producto.activo && producto.categoriaActiva

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = String.format("$ %,.0f", producto.precio),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Caramel,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Cream)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = producto.categoriaNombre,
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (enMenu) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (enMenu) stringResource(R.string.producto_en_menu) else stringResource(R.string.producto_fuera_de_menu),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enMenu) Success else Error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (!producto.descripcion.isNullOrBlank()) {
                Text(
                    text = producto.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }

            Text(
                text = stringResource(R.string.producto_stock_formato, producto.stockActual, producto.umbralMinimo),
                style = MaterialTheme.typography.bodySmall,
                color = if (producto.stockActual <= producto.umbralMinimo) Warning else Ink,
                fontWeight = if (producto.stockActual <= producto.umbralMinimo) FontWeight.Bold else FontWeight.Normal,
            )

            if (esAdministrador) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onEditar,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Editar", style = MaterialTheme.typography.labelSmall, color = Espresso)
                    }

                    Button(
                        onClick = onToggleEstado,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (producto.activo) Error else Leaf,
                        ),
                    ) {
                        Text(
                            text = if (producto.activo) stringResource(R.string.producto_desactivar) else stringResource(R.string.producto_activar),
                            style = MaterialTheme.typography.labelSmall,
                            color = White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCategoria(
    categoria: Categoria,
    esAdministrador: Boolean,
    onEditar: () -> Unit,
    onToggleEstado: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = categoria.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (categoria.activa) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (categoria.activa) stringResource(R.string.categoria_activa) else stringResource(R.string.categoria_desactivada),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (categoria.activa) Success else Error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = stringResource(R.string.categoria_productos_conteo, categoria.totalProductos),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )

            if (esAdministrador) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onEditar,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Renombrar", style = MaterialTheme.typography.labelSmall, color = Espresso)
                    }

                    Button(
                        onClick = onToggleEstado,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (categoria.activa) Error else Leaf,
                        ),
                    ) {
                        Text(
                            text = if (categoria.activa) stringResource(R.string.categoria_desactivar) else stringResource(R.string.categoria_activar),
                            style = MaterialTheme.typography.labelSmall,
                            color = White,
                        )
                    }
                }
            }
        }
    }
}
