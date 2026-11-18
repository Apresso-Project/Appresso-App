package com.appresso.app.ui.clientes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ClientesRoute(
    viewModel: ClientesViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ClientesScreen(
        state = state,
        onVolver = onVolver,
        onBusquedaCambio = viewModel::onBusquedaCambio,
        onAbrirRegistro = viewModel::abrirRegistro,
        onCerrarRegistro = viewModel::cerrarRegistro,
        onRegistrar = viewModel::registrarCliente,
        onAbrirEliminacion = viewModel::abrirDialogoEliminacion,
        onCerrarEliminacion = viewModel::cerrarDialogoEliminacion,
        onConfirmarEliminacion = viewModel::confirmarEliminacionDatos,
        onLimpiarMensajes = viewModel::limpiarMensajes,
        modifier = modifier,
    )
}
