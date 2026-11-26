package com.appresso.app.ui.comandas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.comandas.Mesa
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

/**
 * Mapa de mesas (RF-15, RF-64): una cuadrícula con cada mesa y su estado. Las disponibles se pueden
 * tocar para armar su comanda (si el rol puede crear comandas); las ocupadas se ven atenuadas.
 * El administrador ve además el botón «Nueva mesa». Solo dibuja lo que entrega [MesasViewModel].
 */
@Composable
fun MesasScreen(
    estado: MesasUiState,
    puedeCrearMesa: Boolean,
    puedeCrearComanda: Boolean,
    onNuevaMesa: () -> Unit,
    onAbrirMesa: (Mesa) -> Unit,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.mesas_titulo), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (puedeCrearMesa) {
                BotonPrimario(stringResource(R.string.mesas_nueva), onNuevaMesa, Modifier.weight(1f))
            }
            BotonSecundario(
                stringResource(R.string.mesas_volver),
                onVolver,
                if (puedeCrearMesa) Modifier else Modifier.fillMaxWidth(),
            )
        }

        estado.mesaCreada?.let { MensajeConBorde(stringResource(R.string.mesa_creada, it), color = Success) }

        when {
            estado.cargando && estado.mesas.isEmpty() -> Centrado { Cargando(stringResource(R.string.mesas_cargando)) }

            estado.error != null && estado.mesas.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.error))
                BotonSecundario(stringResource(R.string.cat_reintentar), onReintentar)
            }

            estado.mesas.isEmpty() -> Centrado {
                Text(
                    stringResource(if (puedeCrearMesa) R.string.mesas_vacio_admin else R.string.mesas_vacio_mesero),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.mesas_resumen, estado.disponibles, estado.ocupadas),
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                    )
                    if (puedeCrearComanda) {
                        Text(
                            stringResource(R.string.mesas_ayuda),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(estado.mesas, key = { it.id }) { mesa ->
                        TarjetaMesa(
                            mesa = mesa,
                            seleccionable = puedeCrearComanda && mesa.disponible,
                            onClick = { onAbrirMesa(mesa) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaMesa(mesa: Mesa, seleccionable: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    val textoEstado = stringResource(
        if (mesa.disponible) R.string.mesa_estado_disponible else R.string.mesa_estado_ocupada,
    )
    val descripcion = stringResource(R.string.mesa_descripcion, mesa.nombre, textoEstado)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .shadow(if (mesa.disponible) 2.dp else 0.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(if (mesa.disponible) White else Cream)
            .border(1.5.dp, if (mesa.disponible) Success.copy(alpha = 0.5f) else Espresso.copy(alpha = 0.15f), forma)
            .clickable(enabled = seleccionable, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = descripcion
                if (seleccionable) role = Role.Button
            }
            .padding(16.dp),
    ) {
        Text(
            mesa.nombre,
            style = MaterialTheme.typography.titleLarge,
            color = if (mesa.disponible) Espresso else Muted,
            maxLines = 2,
        )
        ChipEstado(textoEstado, if (mesa.disponible) Success else Caramel)
    }
}
