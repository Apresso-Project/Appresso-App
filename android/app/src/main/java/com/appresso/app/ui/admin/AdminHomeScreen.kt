package com.appresso.app.ui.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.appresso.app.R
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink

/**
 * Pantalla básica del administrador, a la que se llega tras iniciar sesión (entregable 2.1.1).
 * Es provisional: las opciones de administración (usuarios, productos, caja...) llegan con
 * sus módulos.
 */
@Composable
fun AdminHomeScreen(nombre: String, modifier: Modifier = Modifier) {
    PantallaProvisional(
        titulo = stringResource(R.string.admin_home_title),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.admin_home_greeting, nombre),
            style = MaterialTheme.typography.titleLarge,
            color = Espresso,
        )
        Text(
            text = stringResource(R.string.admin_home_pending),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AdminHomeScreenPreview() {
    AppressoTheme { AdminHomeScreen(nombre = "Administrador de prueba") }
}
