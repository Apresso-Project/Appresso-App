package com.appresso.app.data.comandas

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cálculo de los importes de una comanda (RF-17), igual al del backend (`CalculadoraComanda.java`),
 * para mostrar subtotales y total **antes** de enviar. Todo con [BigDecimal] a 2 decimales y redondeo
 * HALF_UP, así el resultado coincide con el del servidor y con el cálculo a mano:
 *
 * - subtotal de una línea = cantidad × precio vigente;
 * - subtotal = suma de los subtotales de las líneas;
 * - descuento autorizado: porcentaje = subtotal × valor / 100; valor fijo = el valor, sin pasar del
 *   subtotal (el total nunca es negativo);
 * - total = subtotal − descuento.
 *
 * Es lógica pura, sin Android: se prueba en la JVM.
 */
object CalculadoraComanda {

    private val CIEN = BigDecimal.valueOf(100)
    private val CERO = BigDecimal.ZERO.setScale(2)

    /** Un descuento autorizado (recompensa de fidelización). La validación de que aplique la hace el backend. */
    sealed interface Descuento {
        /** `DESCUENTO_PORCENTAJE`: [porcentaje] de 0 a 100. */
        data class Porcentaje(val porcentaje: BigDecimal) : Descuento

        /** `DESCUENTO_VALOR`: un valor fijo en pesos. */
        data class Valor(val valor: BigDecimal) : Descuento
    }

    /** Una línea para calcular: precio vigente y cantidad. */
    data class Linea(val precioUnitario: BigDecimal, val cantidad: Int)

    data class Importes(val subtotal: BigDecimal, val descuento: BigDecimal, val total: BigDecimal)

    fun subtotalLinea(precioUnitario: BigDecimal, cantidad: Int): BigDecimal {
        require(cantidad >= 0) { "La cantidad no puede ser negativa" }
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad.toLong())).setScale(2, RoundingMode.HALF_UP)
    }

    fun calcular(lineas: List<Linea>, descuento: Descuento? = null): Importes {
        val subtotal = lineas.fold(CERO) { suma, l -> suma.add(subtotalLinea(l.precioUnitario, l.cantidad)) }
        val rebaja = descuento(subtotal, descuento)
        return Importes(subtotal, rebaja, subtotal.subtract(rebaja))
    }

    fun descuento(subtotal: BigDecimal, descuento: Descuento?): BigDecimal {
        val bruto = when (descuento) {
            null -> CERO
            is Descuento.Porcentaje -> subtotal.multiply(descuento.porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP)
            is Descuento.Valor -> descuento.valor.setScale(2, RoundingMode.HALF_UP)
        }
        return bruto.min(subtotal).max(CERO)
    }
}
