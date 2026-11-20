package com.appresso.app.ui.catalogo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CatalogoRoute(
    viewModel: CatalogoViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CatalogoScreen(
        state = state,
        onVolver = onVolver,
        onCambiarPestaña = viewModel::cambiarPestaña,
        onBusquedaCambio = viewModel::onBusquedaCambio,
        onFiltrarCategoria = viewModel::filtrarPorCategoria,
        onAbrirCrearCategoria = viewModel::abrirCrearCategoria,
        onCerrarCrearCategoria = viewModel::cerrarCrearCategoria,
        onGuardarCategoria = viewModel::guardarCategoria,
        onAbrirEditarCategoria = viewModel::abrirEditarCategoria,
        onCerrarEditarCategoria = viewModel::cerrarEditarCategoria,
        onRenombrarCategoria = viewModel::renombrarCategoria,
        onToggleEstadoCategoria = viewModel::toggleEstadoCategoria,
        onAbrirCrearProducto = viewModel::abrirCrearProducto,
        onCerrarCrearProducto = viewModel::cerrarCrearProducto,
        onGuardarCrearProducto = viewModel::guardarProducto,
        onAbrirEditarProducto = viewModel::abrirEditarProducto,
        onCerrarEditarProducto = viewModel::cerrarEditarProducto,
        onGuardarEditarProducto = viewModel::actualizarProducto,
        onToggleEstadoProducto = viewModel::toggleEstadoProducto,
        onLimpiarMensajes = viewModel::limpiarMensajes,
        modifier = modifier,
    )
}
