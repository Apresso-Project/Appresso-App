package com.appresso.app.ui.clientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.clientes.Cliente
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.cuenta.BotonCuenta
import com.appresso.app.ui.cuenta.MensajeError
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.White

/**
 * Búsqueda de clientes por documento para el mesero y el administrador (RF-51). Muestra el nombre y
 * los puntos, o "Cliente no encontrado" con la indicación de que el cliente se registre desde la
 * app. Solo dibuja: el estado es de [ClientesViewModel].
 */
@Composable
fun ClientesScreen(
    estado: ClientesUiState,
    error: String?,
    onDocumentoChange: (String) -> Unit,
    onBuscar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.clientes_titulo), modifier = modifier) {
        Text(
            text = stringResource(R.string.clientes_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        CampoTexto(
            etiqueta = stringResource(R.string.registro_documento),
            placeholder = stringResource(R.string.registro_documento_placeholder),
            valor = estado.documento,
            onValorChange = onDocumentoChange,
            soloLectura = estado.buscando,
            teclado = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search,
            ),
            acciones = KeyboardActions(onSearch = { onBuscar() }),
        )
        BotonCuenta(
            texto = stringResource(R.string.clientes_buscar),
            textoCargando = stringResource(R.string.clientes_buscando),
            onClick = onBuscar,
            cargando = estado.buscando,
            habilitado = estado.documento.isNotBlank(),
        )

        if (error != null) MensajeError(error)
        when (val resultado = estado.resultado) {
            is ResultadoVista.Encontrado -> TarjetaCliente(resultado.cliente)
            ResultadoVista.NoEncontrado -> NoEncontrado()
            null -> Unit
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onVolver,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.seccion_volver), color = Espresso)
        }
    }
}

@Composable
private fun TarjetaCliente(cliente: Cliente) {
    Card(
        colors = CardDefaults.cardColors(containerColor = White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${cliente.nombre} ${cliente.apellido}",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = stringResource(R.string.cliente_documento_formato, cliente.documento),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Text(
                text = pluralStringResource(R.plurals.cliente_puntos, cliente.puntos, cliente.puntos),
                style = MaterialTheme.typography.headlineSmall,
                color = Caramel,
            )
        }
    }
}

@Composable
private fun NoEncontrado() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.cliente_no_encontrado),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.cliente_no_encontrado_ayuda),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ClientesScreenPreview() {
    AppressoTheme {
        ClientesScreen(
            estado = ClientesUiState(
                documento = "1234567890",
                resultado = ResultadoVista.Encontrado(Cliente(1, "Ana", "Pérez", "1234567890", 3)),
            ),
            error = null,
            onDocumentoChange = {}, onBuscar = {}, onVolver = {},
        )
    }
}
