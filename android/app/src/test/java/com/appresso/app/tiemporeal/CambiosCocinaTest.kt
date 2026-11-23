package com.appresso.app.tiemporeal

import com.google.gson.JsonObject
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CambiosCocinaTest {

    private val ahora = Instant.parse("2026-11-22T20:15:03.900Z")
    private val reloj = { ahora }

    private fun evento(tipo: String, fecha: String = "2026-11-22T20:15:03.412Z") =
        EventoTiempoReal("id-$tipo", 1, tipo, fecha, JsonObject())

    @Test
    fun `solo pasan los cambios de estado y los productos agregados`() = runBlocking {
        val eventos = flowOf(
            evento("COMANDA_CREADA"),
            evento("COMANDA_ESTADO_CAMBIADO"),
            evento("PRODUCTO_ACTUALIZADO"),
            evento("COMANDA_MODIFICADA"),
            evento("EXISTENCIAS_ACTUALIZADAS"),
        )

        assertEquals(
            listOf("COMANDA_ESTADO_CAMBIADO", "COMANDA_MODIFICADA"),
            eventos.cambiosDeCocina(reloj).toList().map { it.tipo },
        )
    }

    @Test
    fun `el cambio lleva la hora en que el servidor publico el evento`() = runBlocking {
        val cambio = flowOf(evento("COMANDA_ESTADO_CAMBIADO")).cambiosDeCocina(reloj).toList().single()

        assertEquals(Instant.parse("2026-11-22T20:15:03.412Z"), cambio.fechaServidor)
        assertEquals(ahora, cambio.recibidoEn)
    }

    @Test
    fun `una fecha ilegible no descarta el cambio`() = runBlocking {
        val cambio = flowOf(evento("COMANDA_MODIFICADA", fecha = "ayer")).cambiosDeCocina(reloj).toList().single()

        assertEquals("COMANDA_MODIFICADA", cambio.tipo)
        assertNull(cambio.fechaServidor)
    }

    @Test
    fun `una reconexion cuenta como cambio sin fecha`() = runBlocking {
        val cambios = flowOf("reconectado").comoResincronizacion(reloj).toList()

        assertEquals(listOf(CambioCocina(CambioCocina.RESINCRONIZACION, null, ahora)), cambios)
    }
}
