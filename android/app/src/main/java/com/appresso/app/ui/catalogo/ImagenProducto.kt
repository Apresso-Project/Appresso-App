package com.appresso.app.ui.catalogo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Muted

private sealed interface EstadoImagen {
    data object Cargando : EstadoImagen
    data object SinImagen : EstadoImagen
    data object Fallo : EstadoImagen
    data class Lista(val imagen: ImageBitmap) : EstadoImagen
}

/**
 * Imagen del producto. Sin URL o con una que no carga se muestra un recuadro con texto («Sin
 * imagen», «No se pudo cargar»): el producto nunca queda sin un indicador de lo que pasó.
 */
@Composable
fun ImagenProducto(url: String?, nombre: String, lado: Dp, modifier: Modifier = Modifier) {
    var estado by remember(url) {
        mutableStateOf<EstadoImagen>(if (url.isNullOrBlank()) EstadoImagen.SinImagen else EstadoImagen.Cargando)
    }
    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) {
            estado = CargadorImagen.cargar(url)?.let { EstadoImagen.Lista(it) } ?: EstadoImagen.Fallo
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(lado)
            .clip(RoundedCornerShape(12.dp))
            .background(Cream),
    ) {
        when (val actual = estado) {
            is EstadoImagen.Lista -> Image(
                bitmap = actual.imagen,
                contentDescription = nombre,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(lado),
            )

            EstadoImagen.Cargando -> CircularProgressIndicator(color = Caramel, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))

            EstadoImagen.SinImagen, EstadoImagen.Fallo -> Text(
                text = stringResource(if (actual == EstadoImagen.Fallo) R.string.prod_imagen_fallo else R.string.prod_imagen_ninguna),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
