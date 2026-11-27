package com.appresso.app.ui.comandas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta [ComandaScreen] con [ComandaViewModel]. */
@Composable
fun ComandaRoute(
    viewModel: ComandaViewModel,
    onVolverAMesas: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    ComandaScreen(
        estado = estado,
        onFiltrar = viewModel::filtrarPorCategoria,
        onAgregar = viewModel::agregar,
        onQuitar = viewModel::quitar,
        onObservaciones = viewModel::cambiarObservaciones,
        onVerResumen = viewModel::verResumen,
        onSeguirEditando = viewModel::seguirEditando,
        onGuardar = viewModel::guardar,
        onReintentarMenu = viewModel::cargarMenu,
        onReintentarDetalle = viewModel::cargarDetalle,
        onVolverAMesas = onVolverAMesas,
        modifier = modifier,
    )
}
