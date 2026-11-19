package com.appresso.app.ui.pagos

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.data.pagos.MedioPago

/** Conecta [PagoScreen] y la pregunta de confirmación con [PagoViewModel]. */
@Composable
fun PagoRoute(
    viewModel: PagoViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    // Mientras se envía el pago no se puede salir: así no se pierde de vista el resultado.
    BackHandler(enabled = estado.procesando) { }

    PagoScreen(
        estado = estado,
        onSeleccionarMedio = viewModel::seleccionarMedio,
        onConfirmar = viewModel::pedirConfirmacion,
        onVolver = onVolver,
        modifier = modifier,
    )

    val medio = estado.medio
    if (estado.confirmando && medio != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelarConfirmacion,
            title = { Text(stringResource(R.string.pago_dialogo_titulo, etiquetaDe(medio))) },
            text = {
                Text(
                    stringResource(
                        when (medio) {
                            MedioPago.EFECTIVO -> R.string.pago_dialogo_efectivo
                            MedioPago.TRANSFERENCIA -> R.string.pago_dialogo_transferencia
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmar) { Text(stringResource(R.string.pago_dialogo_confirmar)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarConfirmacion) {
                    Text(stringResource(R.string.pago_dialogo_cancelar))
                }
            },
        )
    }
}
