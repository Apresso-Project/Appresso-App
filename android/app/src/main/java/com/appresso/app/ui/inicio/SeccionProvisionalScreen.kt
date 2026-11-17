package com.appresso.app.ui.inicio

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.permisos.Opcion
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted

/**
 * Sección del menú que todavía no tiene su pantalla real. Cada módulo la reemplaza en
 * `AppressoRaiz` cuando tenga la suya.
 */
@Composable
fun SeccionProvisionalScreen(opcion: Opcion, onVolver: () -> Unit, modifier: Modifier = Modifier) {
    PantallaProvisional(titulo = stringResource(opcion.titulo()), modifier = modifier) {
        Text(
            text = stringResource(opcion.descripcion()),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.seccion_provisional),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onVolver,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Espresso, contentColor = Cream),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
        ) {
            Text(text = stringResource(R.string.seccion_volver), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SeccionProvisionalPreview() {
    AppressoTheme { SeccionProvisionalScreen(opcion = Opcion.COCINA, onVolver = {}) }
}
