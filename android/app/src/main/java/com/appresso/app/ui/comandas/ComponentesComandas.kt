package com.appresso.app.ui.comandas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.White

// Piezas comunes de las pantallas de mesas y comandas, con los componentes de la guía de estilo:
// botón primario (Espresso), secundario (borde Espresso), radios de 12/16 dp y área táctil de 48 dp.

@Composable
internal fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Espresso,
            contentColor = Cream,
            disabledContainerColor = Espresso.copy(alpha = 0.35f),
            disabledContentColor = Cream,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(texto, style = MaterialTheme.typography.titleMedium) }
}

@Composable
internal fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, if (habilitado) Espresso else Espresso.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(texto, style = MaterialTheme.typography.titleMedium) }
}

/** Mensaje en una tarjeta blanca con borde de color (rojo para errores, verde para avisos de éxito). */
@Composable
internal fun MensajeConBorde(texto: String, modifier: Modifier = Modifier, color: Color = Error) {
    Text(
        text = texto,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.5.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Etiqueta de estado como las de la guía («Activo», «Pendiente»…): fondo suave y texto del mismo color. */
@Composable
internal fun ChipEstado(texto: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Contenido centrado en el espacio libre (cargando, vacío y error). */
@Composable
internal fun ColumnScope.Centrado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = contenido,
    )
}

@Composable
internal fun Cargando(descripcion: String) {
    CircularProgressIndicator(
        color = Caramel,
        strokeWidth = 4.dp,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = descripcion },
    )
}

/** Una fila «Subtotal ……… $ 19.500». [destacado] usa Fraunces para el total. */
@Composable
internal fun FilaImporte(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            etiqueta,
            style = if (destacado) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            color = if (destacado) Espresso else Muted,
        )
        Text(
            valor,
            style = if (destacado) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = if (destacado) Espresso else Ink,
        )
    }
}

@Composable
internal fun textoDe(error: ErrorMesas): String = when (error) {
    ErrorMesas.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorMesas.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    is ErrorMesas.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
    is ErrorMesas.DelServidor -> error.mensaje
}

@Composable
internal fun textoDe(error: ErrorNombreMesa): String = when (error) {
    ErrorNombreMesa.Vacio -> stringResource(R.string.mesa_error_vacio)
    ErrorNombreMesa.MuyLargo -> stringResource(R.string.mesa_error_largo)
    ErrorNombreMesa.Repetido -> stringResource(R.string.mesa_error_repetido)
    is ErrorNombreMesa.DelServidor -> error.mensaje
}

@Composable
internal fun textoDe(error: ErrorComanda): String = when (error) {
    ErrorComanda.Vacia -> stringResource(R.string.comanda_error_vacia)
    ErrorComanda.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorComanda.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    is ErrorComanda.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
    is ErrorComanda.DelServidor -> error.mensaje
}
