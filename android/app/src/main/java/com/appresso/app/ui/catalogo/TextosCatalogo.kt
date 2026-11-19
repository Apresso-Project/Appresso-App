package com.appresso.app.ui.catalogo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.appresso.app.R

@Composable
fun textoDe(error: ErrorNombre): String = when (error) {
    ErrorNombre.Obligatorio -> stringResource(R.string.cat_error_nombre_obligatorio)
    ErrorNombre.DemasiadoLargo -> stringResource(R.string.cat_error_nombre_largo, CategoriasViewModel.MAX_NOMBRE)
    ErrorNombre.Repetido -> stringResource(R.string.cat_error_nombre_repetido)
    is ErrorNombre.Servidor -> error.mensaje
}

/** Los mensajes del servidor (403, 400) se muestran tal cual; si faltan se usa uno propio. */
@Composable
fun textoDe(error: ErrorCategorias): String = when (error) {
    ErrorCategorias.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorCategorias.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    ErrorCategorias.NoEncontrada -> stringResource(R.string.cat_error_no_encontrada)
    is ErrorCategorias.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
    is ErrorCategorias.DatosInvalidos -> error.mensajeServidor ?: stringResource(R.string.cat_error_datos)
}

/** «Sin productos», «1 producto» o «N productos». */
@Composable
fun textoProductos(total: Long): String = when (total) {
    0L -> stringResource(R.string.cat_productos_ninguno)
    1L -> stringResource(R.string.cat_productos_uno)
    else -> stringResource(R.string.cat_productos_varios, total)
}
