package com.appresso.app.ui.cuenta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso

/**
 * Marco de las pantallas públicas del cliente (registro y recuperación de contraseña): la cabecera
 * espresso del inicio de sesión, con un botón para volver, y el formulario en un panel crema que se
 * desplaza y deja espacio al teclado.
 */
@Composable
internal fun MarcoCuenta(
    titulo: String,
    subtitulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    volverHabilitado: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Espresso),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
        ) {
            TextButton(onClick = onVolver, enabled = volverHabilitado) {
                Text(
                    text = stringResource(R.string.cuenta_volver),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cream,
                )
            }
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineMedium,
                color = Cream,
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp)
                    .semantics { heading() },
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Cream),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = contenido,
            )
        }
    }
}

/** Botón principal de las pantallas de la cuenta; muestra una rueda mientras espera. */
@Composable
internal fun BotonCuenta(
    texto: String,
    textoCargando: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
) {
    Button(
        onClick = { if (!cargando) onClick() },
        enabled = habilitado,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Espresso,
            contentColor = Cream,
            disabledContainerColor = Espresso.copy(alpha = 0.4f),
            disabledContentColor = Cream.copy(alpha = 0.8f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = textoCargando },
                color = Cream,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Mensaje de error general; los lectores de pantalla lo anuncian al aparecer. */
@Composable
internal fun MensajeError(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
