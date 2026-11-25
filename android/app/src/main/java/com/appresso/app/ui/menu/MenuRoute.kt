package com.appresso.app.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Conecta [MenuScreen] con [MenuViewModel]. */
@Composable
fun MenuRoute(
    viewModel: MenuViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    MenuScreen(
        estado = estado,
        onFiltrar = viewModel::filtrarPorCategoria,
        onReintentar = viewModel::cargar,
        onVolver = onVolver,
        modifier = modifier,
    )
}
