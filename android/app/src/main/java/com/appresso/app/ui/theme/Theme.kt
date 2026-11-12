package com.appresso.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// La marca Appresso tiene un solo tema (claro). Sin color dinámico de Android 12+
// ni modo oscuro por ahora, para que los colores coincidan con la guía de Figma.
private val AppressoColorScheme = lightColorScheme(
    primary = Espresso,
    onPrimary = Cream,
    secondary = Caramel,
    onSecondary = White,
    tertiary = Leaf,
    onTertiary = White,
    background = Cream,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    outline = Espresso.copy(alpha = 0.2f),
    error = Error,
    onError = White,
)

@Composable
fun AppressoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppressoColorScheme,
        typography = Typography,
        content = content,
    )
}
