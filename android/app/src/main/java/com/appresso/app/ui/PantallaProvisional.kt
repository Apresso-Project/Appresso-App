package com.appresso.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso

/**
 * Marco común de las pantallas provisionales: la misma cabecera espresso de la pantalla de
 * login (así los íconos de la barra de estado se siguen viendo) y un cuerpo crema.
 * Se reemplaza por las pantallas reales cuando existan los demás módulos.
 */
@Composable
fun PantallaProvisional(
    titulo: String,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Espresso),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = Cream.copy(alpha = 0.7f),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp),
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium,
            color = Cream,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Cream)
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenido,
        )
    }
}
