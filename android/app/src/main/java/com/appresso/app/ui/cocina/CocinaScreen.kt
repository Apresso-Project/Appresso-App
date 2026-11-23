package com.appresso.app.ui.cocina

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.cocina.ComandaCocina
import com.appresso.app.data.cocina.EstadoCocina
import com.appresso.app.data.cocina.ItemCocina
import com.appresso.app.tiemporeal.EstadoConexion
import com.appresso.app.tiemporeal.InfoConexion
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Leaf
import com.appresso.app.ui.theme.White
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla de cocina (SCRUM-92): las comandas activas en tarjetas grandes, de la más antigua a la más
 * reciente, con mesa, estado, hora de envío y cada producto con su cantidad y observaciones completas.
 * Es de solo lectura: cambiar el estado de preparación es otra historia (RF-25). Una rejilla adaptable
 * pone varias columnas en una tableta y una sola en el teléfono, sin desplazamiento horizontal.
 * Solo dibuja: el estado viene de [CocinaViewModel] y la conexión de [ConexionTiempoReal].
 */
@Composable
fun CocinaScreen(
    estado: CocinaUiState,
    conexion: InfoConexion,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.cocina_titulo), modifier = modifier) {
        OutlinedButton(
            onClick = onVolver,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, Espresso),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.cocina_volver), style = MaterialTheme.typography.titleMedium) }

        IndicadorConexion(conexion)

        when {
            estado.cargando && estado.comandas.isEmpty() -> Centrado {
                val descripcion = stringResource(R.string.cocina_cargando)
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = descripcion },
                )
            }

            estado.error != null && estado.comandas.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.error))
                OutlinedButton(
                    onClick = onReintentar,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Espresso),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.cat_reintentar), style = MaterialTheme.typography.titleMedium) }
            }

            estado.comandas.isEmpty() -> Centrado {
                Text(
                    stringResource(R.string.cocina_vacio),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(estado.comandas, key = { it.id }) { TarjetaComanda(it) }
            }
        }
    }
}

/**
 * Dice si lo que se ve puede estar atrasado (RNF-21): mientras no haya conexión en vivo la lista
 * puede no reflejar los últimos cambios. Con la conexión activa no muestra nada.
 */
@Composable
private fun IndicadorConexion(conexion: InfoConexion) {
    val texto = when (conexion.estado) {
        EstadoConexion.CONECTADO -> return
        EstadoConexion.DESCONECTADO, EstadoConexion.CONECTANDO -> stringResource(R.string.cocina_conectando)
        EstadoConexion.ESPERANDO_REINTENTO -> {
            val segundos = conexion.esperaMs?.let { ((it + 999) / 1000).toInt() }
            if (segundos != null && segundos > 0) {
                stringResource(R.string.cocina_sin_conexion_reintento, segundos)
            } else {
                stringResource(R.string.cocina_sin_conexion)
            }
        }
    }
    val sinConexion = conexion.estado == EstadoConexion.ESPERANDO_REINTENTO
    Text(
        text = texto,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.5.dp, if (sinConexion) Error else Caramel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ColumnScope.Centrado(contenido: @Composable ColumnScope.() -> Unit) {
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
private fun MensajeConBorde(texto: String) {
    Text(
        text = texto,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.5.dp, Error, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun TarjetaComanda(comanda: ComandaCocina) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White)
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = comanda.mesa,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            EtiquetaEstado(comanda.estado)
        }
        comanda.fechaEnvio?.let {
            Text(
                text = stringResource(R.string.cocina_enviada_a_las, horaLocal(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
        comanda.items.forEach { ItemDeComanda(it) }
    }
}

/** El estado va siempre en texto; el color solo acompaña (RNF-17). */
@Composable
private fun EtiquetaEstado(estado: EstadoCocina) {
    val (fondo, texto) = when (estado) {
        EstadoCocina.PENDIENTE -> Color(0xFFFBE7B8) to R.string.cocina_estado_pendiente
        EstadoCocina.EN_PREPARACION -> Color(0xFFF3CFA8) to R.string.cocina_estado_en_preparacion
        EstadoCocina.LISTA -> Color(0xFFCFE3C4) to R.string.cocina_estado_lista
    }
    val borde = when (estado) {
        EstadoCocina.PENDIENTE -> Caramel
        EstadoCocina.EN_PREPARACION -> Espresso
        EstadoCocina.LISTA -> Leaf
    }
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .border(1.5.dp, borde, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Cantidad y producto grandes; las observaciones y la marca «Agregado» destacadas y sin recortar. */
@Composable
private fun ItemDeComanda(item: ItemCocina) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cocina_item, item.cantidad, item.producto),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.agregado) {
                Text(
                    text = stringResource(R.string.cocina_agregado),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Cream,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Espresso)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        item.observaciones?.let {
            Text(
                text = stringResource(R.string.cocina_observaciones, it),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Cream)
                    .border(1.5.dp, Caramel, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun textoDe(error: ErrorCocina): String = when (error) {
    ErrorCocina.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorCocina.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    is ErrorCocina.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
}

private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** La hora de envío en la zona del dispositivo, p. ej. «20:15». */
internal fun horaLocal(instante: Instant, zona: ZoneId = ZoneId.systemDefault()): String =
    FORMATO_HORA.format(instante.atZone(zona))
