package com.appresso.app.ui.pagos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.appresso.app.R
import com.appresso.app.data.pagos.MedioPago
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun etiquetaDe(medio: MedioPago): String = stringResource(
    when (medio) {
        MedioPago.EFECTIVO -> R.string.pago_medio_efectivo
        MedioPago.TRANSFERENCIA -> R.string.pago_medio_transferencia
    },
)

/** Los mensajes del servidor (403, 409, 400) se muestran tal cual; si faltan se usa uno propio. */
@Composable
fun textoDe(error: ErrorPago): String = when (error) {
    ErrorPago.SinConexion -> stringResource(R.string.pago_error_sin_conexion)
    ErrorPago.ErrorDelServidor -> stringResource(R.string.pago_error_servidor)
    ErrorPago.NoEncontrada -> stringResource(R.string.pago_error_no_encontrada)
    is ErrorPago.SinPermiso -> error.mensajeServidor ?: stringResource(R.string.error_prohibido)
    is ErrorPago.Conflicto -> error.mensajeServidor ?: stringResource(R.string.pago_error_conflicto)
    is ErrorPago.DatosInvalidos -> error.mensajeServidor ?: stringResource(R.string.pago_error_datos)
}

/** Pesos colombianos sin decimales si el valor es entero, p. ej. «$ 12.500». */
fun formatoPesos(valor: BigDecimal): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(valor)

/** El número que escribe la persona, o null si no es un número de comanda válido. */
fun numeroDeComanda(texto: String): Long? = texto.trim().toLongOrNull()?.takeIf { it > 0 }
