package com.appresso.app.tiemporeal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaReintentoTest {

    @Test
    fun sinVariacionDuplicaHastaElTope() {
        val p = PoliticaReintento(jitter = 0.0)
        val esperas = (0..7).map { p.siguienteEsperaMs(it) }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), esperas)
    }

    @Test
    fun laVariacionQuedaEntreMenos20YMas20PorCiento() {
        val minimo = PoliticaReintento(azar = { 0.0 }).siguienteEsperaMs(2) // 4 s - 20 %
        val maximo = PoliticaReintento(azar = { 1.0 }).siguienteEsperaMs(2) // 4 s + 20 %
        val medio = PoliticaReintento(azar = { 0.5 }).siguienteEsperaMs(2)
        assertEquals(3_200L, minimo)
        assertEquals(4_800L, maximo)
        assertEquals(4_000L, medio)
    }

    @Test
    fun cualquierAzarRespetaLosLimites() {
        val p = PoliticaReintento()
        for (intento in 0..40) repeat(50) {
            val espera = p.siguienteEsperaMs(intento)
            assertTrue("espera=$espera", espera in 800..36_000)
        }
    }

    @Test
    fun intentosMuyGrandesNoDesbordan() {
        assertEquals(30_000L, PoliticaReintento(jitter = 0.0).siguienteEsperaMs(Int.MAX_VALUE))
    }
}
