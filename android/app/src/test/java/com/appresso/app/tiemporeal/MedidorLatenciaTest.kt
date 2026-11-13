package com.appresso.app.tiemporeal

import com.google.gson.JsonObject
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedidorLatenciaTest {

    @Test
    fun percentil95Y3Segundos() {
        val m = MedidorLatencia()
        assertNull(m.percentil95())
        (1..100).forEach { m.registrar(it * 10L) } // 10..1000 ms
        assertEquals(950L, m.percentil95())
        assertEquals(100.0, m.porcentajeBajoLimite(), 0.0)
        m.registrar(5_000)
        assertEquals(100.0 * 100 / 101, m.porcentajeBajoLimite(), 0.001)
    }

    @Test
    fun calculaLaLatenciaDesdeLaFechaDelEvento() {
        val enviado = Instant.parse("2026-11-11T20:00:00.000Z")
        val evento = EventoTiempoReal("x", 1, "COMANDA_CREADA", enviado.toString(), JsonObject())
        val m = MedidorLatencia()
        assertEquals(250L, m.registrarEvento(evento, enviado.plusMillis(250)))
        assertEquals(1, m.cantidad())
    }

    @Test
    fun fechaIlegibleSeIgnora() {
        val evento = EventoTiempoReal("x", 1, "COMANDA_CREADA", "ayer", JsonObject())
        val m = MedidorLatencia()
        assertNull(m.registrarEvento(evento))
        assertEquals(0, m.cantidad())
    }
}
