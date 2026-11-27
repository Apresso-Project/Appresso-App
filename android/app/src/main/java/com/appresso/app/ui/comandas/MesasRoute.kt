package com.appresso.app.ui.comandas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R
import com.appresso.app.data.comandas.Mesa
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Error

/** Conecta [MesasScreen] y el formulario «Nueva mesa» con [MesasViewModel]. */
@Composable
fun MesasRoute(
    viewModel: MesasViewModel,
    puedeCrearMesa: Boolean,
    puedeCrearComanda: Boolean,
    onAbrirMesa: (Mesa) -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    MesasScreen(
        estado = estado,
        puedeCrearMesa = puedeCrearMesa,
        puedeCrearComanda = puedeCrearComanda,
        onNuevaMesa = viewModel::nuevaMesa,
        onAbrirMesa = onAbrirMesa,
        onReintentar = viewModel::cargar,
        onVolver = onVolver,
        modifier = modifier,
    )

    val formulario = estado.formulario
    if (puedeCrearMesa && formulario != null) {
        AlertDialog(
            onDismissRequest = viewModel::cerrarFormulario,
            containerColor = Cream,
            title = {
                Text(stringResource(R.string.mesa_form_titulo), style = MaterialTheme.typography.headlineMedium, color = Espresso)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(
                        etiqueta = stringResource(R.string.mesa_form_nombre),
                        placeholder = stringResource(R.string.mesa_form_placeholder),
                        valor = formulario.nombre,
                        onValorChange = viewModel::onNombreChange,
                        soloLectura = formulario.guardando,
                        error = formulario.errorNombre?.let { textoDe(it) },
                        teclado = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        acciones = KeyboardActions(onDone = { viewModel.guardar() }),
                    )
                    formulario.error?.let { Text(textoDe(it), color = Error, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                BotonPrimario(
                    stringResource(if (formulario.guardando) R.string.mesa_form_guardando else R.string.mesa_form_crear),
                    onClick = viewModel::guardar,
                    habilitado = !formulario.guardando,
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::cerrarFormulario, enabled = !formulario.guardando) {
                    Text(stringResource(R.string.mesa_form_cancelar), color = Espresso)
                }
            },
        )
    }
}
