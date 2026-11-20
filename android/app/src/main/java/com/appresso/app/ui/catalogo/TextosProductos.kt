package com.appresso.app.ui.catalogo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.appresso.app.R

@Composable
fun textoDe(error: ErrorCampo): String = when (error) {
    ErrorCampo.Obligatorio -> stringResource(R.string.prod_error_obligatorio)
    ErrorCampo.PrecioInvalido -> stringResource(R.string.prod_error_precio)
    ErrorCampo.UrlInvalida -> stringResource(R.string.prod_error_url)
    is ErrorCampo.DemasiadoLargo -> stringResource(R.string.prod_error_largo, error.maximo)
    is ErrorCampo.NumeroInvalido -> stringResource(R.string.prod_error_numero, error.maximo)
    is ErrorCampo.Servidor -> error.mensaje
}

@Composable
fun textoDe(error: ErrorProductos): String = when (error) {
    ErrorProductos.SinConexion -> stringResource(R.string.error_sin_conexion)
    ErrorProductos.ErrorDelServidor -> stringResource(R.string.cat_error_servidor)
    ErrorProductos.NoEncontrado -> stringResource(R.string.prod_error_no_encontrado)
    is ErrorProductos.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
    is ErrorProductos.DatosInvalidos -> error.mensajeServidor ?: stringResource(R.string.cat_error_datos)
}

/** «Disponible», «Pocas unidades» o «Agotado», siempre con la cantidad (RNF-17). */
@Composable
fun textoExistencias(estado: EstadoExistencias, cantidad: Int): String = when (estado) {
    EstadoExistencias.AGOTADO -> stringResource(R.string.prod_stock_agotado)
    EstadoExistencias.POCAS_UNIDADES -> stringResource(R.string.prod_stock_pocas, cantidad)
    EstadoExistencias.DISPONIBLE -> stringResource(R.string.prod_stock_disponible, cantidad)
}
