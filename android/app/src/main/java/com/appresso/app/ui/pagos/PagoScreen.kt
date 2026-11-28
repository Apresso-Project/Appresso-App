package com.appresso.app.ui.pagos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.pagos.MedioPago
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success

/**
 * Confirmación de pago (SCRUM-110): resumen de la comanda, elección del medio (efectivo o
 * transferencia, según el rol) y botón para confirmar. Al registrarse el pago muestra el resultado.
 * Sin lógica: todo viene de [PagoViewModel].
 */
@Composable
fun PagoScreen(
    estado: PagoUiState,
    onSeleccionarMedio: (MedioPago) -> Unit,
    onConfirmar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.pago_titulo), modifier = modifier) {
        val pagado = estado.pagado
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pagado != null) {
                Text(
                    text = stringResource(R.string.pago_registrado),
                    style = MaterialTheme.typography.titleLarge,
                    color = Success,
                )
                if (estado.yaEstabaRegistrado) {
                    Text(
                        text = stringResource(R.string.pago_ya_estaba_registrado),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                Text(
                    text = stringResource(R.string.pago_resumen_comanda, pagado.comandaId, pagado.mesa),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.pago_resumen_valor, formatoPesos(pagado.valor)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Espresso,
                )
                Text(
                    text = stringResource(R.string.pago_resumen_medio, etiquetaDe(pagado.medio)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                )
                Text(
                    text = stringResource(R.string.pago_resumen_usuario, pagado.confirmadoPorNombre),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Text(
                    text = stringResource(R.string.pago_comanda_cerrada),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            } else {
                Text(
                    text = if (estado.mesa != null) {
                        stringResource(R.string.pago_resumen_comanda, estado.comandaId, estado.mesa)
                    } else {
                        stringResource(R.string.pago_resumen_comanda_sin_mesa, estado.comandaId)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                if (estado.total != null) {
                    Text(
                        text = stringResource(R.string.pago_total, formatoPesos(estado.total)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Espresso,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pago_total_lo_calcula_el_servidor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }

                if (estado.medios.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pago_sin_medios),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.pago_elige_medio),
                        style = MaterialTheme.typography.titleMedium,
                        color = Espresso,
                    )
                    estado.medios.forEach { medio ->
                        val habilitado = !estado.procesando
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = estado.medio == medio,
                                    enabled = habilitado,
                                    role = Role.RadioButton,
                                    onClick = { onSeleccionarMedio(medio) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = estado.medio == medio, onClick = null, enabled = habilitado)
                            Text(
                                text = etiquetaDe(medio),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                estado.error?.let {
                    Text(textoDe(it), color = Error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (pagado != null) {
            Button(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso),
            ) {
                Text(stringResource(R.string.pago_listo))
            }
        } else {
            Button(
                onClick = onConfirmar,
                enabled = estado.medio != null && !estado.procesando,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso),
            ) {
                Text(stringResource(if (estado.procesando) R.string.pago_confirmando else R.string.pago_confirmar))
            }
            OutlinedButton(
                onClick = onVolver,
                enabled = !estado.procesando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pago_volver))
            }
        }
    }
}
