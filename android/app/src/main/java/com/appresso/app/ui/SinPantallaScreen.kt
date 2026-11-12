package com.appresso.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Ink

/** Aviso provisional para los roles que todavía no tienen pantalla propia. */
@Composable
fun SinPantallaScreen(rol: Rol, modifier: Modifier = Modifier) {
    PantallaProvisional(
        titulo = stringResource(R.string.sin_pantalla_title),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.sin_pantalla_message, rol.name.lowercase()),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SinPantallaScreenPreview() {
    AppressoTheme { SinPantallaScreen(rol = Rol.MESERO) }
}
