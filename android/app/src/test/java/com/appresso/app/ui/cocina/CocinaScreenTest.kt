package com.appresso.app.ui.cocina

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CocinaScreenTest {

    @Test
    fun `la hora de envio se muestra en la zona del dispositivo`() {
        val instante = Instant.parse("2026-11-22T20:15:03.412Z")

        assertEquals("20:15", horaLocal(instante, ZoneId.of("UTC")))
        assertEquals("15:15", horaLocal(instante, ZoneId.of("America/Bogota"))) // UTC-5
    }

    @Test
    fun `la hora de madrugada lleva cero a la izquierda`() {
        assertEquals("05:07", horaLocal(Instant.parse("2026-11-22T10:07:00Z"), ZoneId.of("America/Bogota")))
    }
}
