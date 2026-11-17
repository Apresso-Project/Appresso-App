package com.appresso.app.ui.inicio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.permisos.Opcion
import com.appresso.app.permisos.opcionesPara
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.White

/**
 * Inicio de cada rol (SCRUM-48): solo muestra las opciones que [opcionesPara] le da a ese rol.
 * Solo dibuja; quién decide las opciones es `Permisos.kt` y la navegación, `AppressoRaiz`.
 */
@Composable
fun InicioScreen(
    nombre: String,
    rol: Rol,
    opciones: List<Opcion>,
    onAbrir: (Opcion) -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Espresso),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = Cream.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(R.string.inicio_saludo, nombre),
                style = MaterialTheme.typography.headlineMedium,
                color = Cream,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(rol.nombreVisible()).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Caramel)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Cream)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.inicio_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            items(opciones, key = { it.name }) { opcion ->
                TarjetaOpcion(opcion = opcion, onClick = { onAbrir(opcion) })
            }
            item {
                OutlinedButton(
                    onClick = onCerrarSesion,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Espresso),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Espresso),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .heightIn(min = 54.dp),
                ) {
                    Text(
                        text = stringResource(R.string.inicio_cerrar_sesion),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaOpcion(opcion: Opcion, onClick: () -> Unit) {
    val titulo = stringResource(opcion.titulo())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .clickable(role = Role.Button, onClickLabel = titulo, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Cream),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = titulo.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = Espresso,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                text = stringResource(opcion.descripcion()),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
        Text(text = "›", style = MaterialTheme.typography.titleLarge, color = Caramel)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun InicioAdministradorPreview() {
    AppressoTheme {
        InicioScreen(
            nombre = "Administrador de prueba",
            rol = Rol.ADMINISTRADOR,
            opciones = opcionesPara(Rol.ADMINISTRADOR),
            onAbrir = {},
            onCerrarSesion = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun InicioMeseroPreview() {
    AppressoTheme {
        InicioScreen(
            nombre = "Ana",
            rol = Rol.MESERO,
            opciones = opcionesPara(Rol.MESERO),
            onAbrir = {},
            onCerrarSesion = {},
        )
    }
}
