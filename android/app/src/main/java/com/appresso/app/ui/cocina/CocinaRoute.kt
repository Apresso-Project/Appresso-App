package com.appresso.app.ui.cocina

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.tiemporeal.InfoConexion
import kotlinx.coroutines.flow.StateFlow

/** Conecta [CocinaScreen] con [CocinaViewModel] y con el estado de la conexión en tiempo real. */
@Composable
fun CocinaRoute(
    viewModel: CocinaViewModel,
    conexion: StateFlow<InfoConexion>,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val infoConexion by conexion.collectAsStateWithLifecycle()

    CocinaScreen(
        estado = estado,
        conexion = infoConexion,
        onReintentar = viewModel::cargar,
        onVolver = onVolver,
        modifier = modifier,
    )
}
