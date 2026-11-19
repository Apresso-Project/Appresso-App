package com.appresso.app.ui.pagos

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.pagos.PagosRepository
import com.appresso.app.R
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink

/**
 * Sección «Cobros» del menú. Mientras no exista la lista de comandas listas para pagar, la persona
 * escribe el número de la comanda y pasa a la confirmación. Cuando esa lista exista, abrirá
 * [PagoRoute] directamente con la comanda elegida y esta entrada manual se quita.
 */
@Composable
fun CobrosRoute(
    rol: Rol,
    token: String?,
    repositorio: PagosRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var texto by rememberSaveable(token) { mutableStateOf("") }
    var comandaId by rememberSaveable(token) { mutableStateOf<Long?>(null) }

    val id = comandaId
    if (id != null) {
        // La clave incluye la comanda y la sesión: otra comanda u otro usuario empiezan de cero y
        // con un identificador de operación nuevo.
        val viewModel: PagoViewModel = viewModel(
            key = "pago-$token-$id",
            factory = PagoViewModel.factory(id, mesa = null, total = null, rol = rol, repositorio = repositorio),
        )
        PagoRoute(
            viewModel = viewModel,
            onVolver = {
                comandaId = null
                texto = ""
            },
            modifier = modifier,
        )
        return
    }

    val numero = numeroDeComanda(texto)
    PantallaProvisional(titulo = stringResource(R.string.cobros_titulo), modifier = modifier) {
        Text(
            text = stringResource(R.string.cobros_instruccion),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it.filter(Char::isDigit).take(12) },
            label = { Text(stringResource(R.string.cobros_numero_comanda)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { comandaId = numero },
            enabled = numero != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Espresso),
        ) {
            Text(stringResource(R.string.cobros_continuar))
        }
        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pago_volver))
        }
    }
}
