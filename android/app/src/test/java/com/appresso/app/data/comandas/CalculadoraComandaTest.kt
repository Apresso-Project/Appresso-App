package com.appresso.app.data.comandas

import com.appresso.app.data.comandas.CalculadoraComanda.Descuento
import com.appresso.app.data.comandas.CalculadoraComanda.Linea
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los cálculos de la comanda contra cuentas hechas a mano (RF-17). Son las mismas reglas que
 * `CalculadoraComandaTest` del backend: si una cambia allá, esta prueba debe cambiar igual.
 */
class CalculadoraComandaTest {

    private fun pesos(valor: String) = BigDecimal(valor).setScale(2)

    @Test
    fun `el subtotal de una linea es cantidad por precio vigente`() {
        assertEquals(pesos("13000"), CalculadoraComanda.subtotalLinea(BigDecimal("6500"), 2))
        assertEquals(pesos("4500.50"), CalculadoraComanda.subtotalLinea(BigDecimal("4500.50"), 1))
        assertEquals(pesos("0"), CalculadoraComanda.subtotalLinea(BigDecimal("3000"), 0))
    }

    @Test
    fun `el subtotal redondea a dos decimales con HALF_UP`() {
        // 3 × 1.335 = 4.005 → 4.01
        assertEquals(pesos("4.01"), CalculadoraComanda.subtotalLinea(BigDecimal("1.335"), 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una cantidad negativa no se acepta`() {
        CalculadoraComanda.subtotalLinea(BigDecimal("1000"), -1)
    }

    @Test
    fun `el total es la suma de los subtotales cuando no hay descuento`() {
        // 2 × 6.500 + 1 × 4.500,50 + 3 × 3.000 = 13.000 + 4.500,50 + 9.000 = 26.500,50
        val importes = CalculadoraComanda.calcular(
            listOf(Linea(BigDecimal("6500"), 2), Linea(BigDecimal("4500.50"), 1), Linea(BigDecimal("3000"), 3)),
        )
        assertEquals(pesos("26500.50"), importes.subtotal)
        assertEquals(pesos("0"), importes.descuento)
        assertEquals(pesos("26500.50"), importes.total)
    }

    @Test
    fun `una comanda sin lineas suma cero`() {
        val importes = CalculadoraComanda.calcular(emptyList())
        assertEquals(pesos("0"), importes.subtotal)
        assertEquals(pesos("0"), importes.total)
    }

    @Test
    fun `descuento por porcentaje sobre el subtotal`() {
        // 10 % de 26.000 = 2.600 → total 23.400
        val importes = CalculadoraComanda.calcular(
            listOf(Linea(BigDecimal("6500"), 4)),
            Descuento.Porcentaje(BigDecimal("10")),
        )
        assertEquals(pesos("26000"), importes.subtotal)
        assertEquals(pesos("2600"), importes.descuento)
        assertEquals(pesos("23400"), importes.total)
    }

    @Test
    fun `el descuento por porcentaje redondea con HALF_UP`() {
        // 15 % de 1.003 = 150,45 ; 12,5 % de 100,1 = 12,5125 → 12,51
        assertEquals(pesos("150.45"), CalculadoraComanda.descuento(pesos("1003"), Descuento.Porcentaje(BigDecimal("15"))))
        assertEquals(pesos("12.51"), CalculadoraComanda.descuento(pesos("100.10"), Descuento.Porcentaje(BigDecimal("12.5"))))
    }

    @Test
    fun `descuento por valor fijo`() {
        val importes = CalculadoraComanda.calcular(
            listOf(Linea(BigDecimal("6500"), 2)),
            Descuento.Valor(BigDecimal("3000")),
        )
        assertEquals(pesos("13000"), importes.subtotal)
        assertEquals(pesos("3000"), importes.descuento)
        assertEquals(pesos("10000"), importes.total)
    }

    @Test
    fun `el descuento nunca supera el subtotal y el total nunca es negativo`() {
        val importes = CalculadoraComanda.calcular(
            listOf(Linea(BigDecimal("4000"), 1)),
            Descuento.Valor(BigDecimal("10000")),
        )
        assertEquals(pesos("4000"), importes.descuento)
        assertEquals(pesos("0"), importes.total)

        val masDelCien = CalculadoraComanda.calcular(listOf(Linea(BigDecimal("4000"), 1)), Descuento.Porcentaje(BigDecimal("150")))
        assertEquals(pesos("0"), masDelCien.total)
    }

    @Test
    fun `un descuento negativo no aumenta el total`() {
        val importes = CalculadoraComanda.calcular(listOf(Linea(BigDecimal("4000"), 1)), Descuento.Valor(BigDecimal("-500")))
        assertEquals(pesos("0"), importes.descuento)
        assertEquals(pesos("4000"), importes.total)
    }

    @Test
    fun `total es siempre subtotal menos descuento`() {
        val casos = listOf(
            null,
            Descuento.Porcentaje(BigDecimal("7")),
            Descuento.Porcentaje(BigDecimal("33.3")),
            Descuento.Valor(BigDecimal("1234.56")),
        )
        val lineas = listOf(Linea(BigDecimal("1999.99"), 3), Linea(BigDecimal("850"), 7))
        casos.forEach { d ->
            val i = CalculadoraComanda.calcular(lineas, d)
            assertEquals("$d", i.subtotal.subtract(i.descuento), i.total)
            assertEquals("$d", 2, i.total.scale())
        }
    }

    // ---------------------------------------------------------------- ciclo de vida

    @Test
    fun `el ciclo de vida avanza un paso por vez y termina en cerrada`() {
        assertEquals(EstadoComanda.ENVIADA, EstadoComanda.PENDIENTE.siguiente)
        assertEquals(EstadoComanda.EN_PREPARACION, EstadoComanda.ENVIADA.siguiente)
        assertEquals(EstadoComanda.LISTA, EstadoComanda.EN_PREPARACION.siguiente)
        assertEquals(EstadoComanda.CERRADA, EstadoComanda.LISTA.siguiente)
        assertNull(EstadoComanda.CERRADA.siguiente)
        assertEquals(
            listOf("PENDIENTE", "ENVIADA", "EN_PREPARACION", "LISTA", "CERRADA"),
            EstadoComanda.entries.map { it.name },
        )
    }
}
