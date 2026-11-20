package com.appresso.app.tiemporeal

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CambiosCatalogoTest {

    private fun evento(tipo: String) = EventoTiempoReal("id-$tipo", 1, tipo, "2026-11-19T10:00:00Z", JsonObject())

    @Test
    fun `solo pasan los eventos de categorias y productos`() = runBlocking {
        val eventos = flowOf(
            evento("CATEGORIA_CREADA"),
            evento("COMANDA_CREADA"),
            evento("PRODUCTO_ACTUALIZADO"),
            evento("EXISTENCIAS_ACTUALIZADAS"),
            evento("CATEGORIA_ACTUALIZADA"),
        )
        assertEquals(4, eventos.cambiosDeCatalogo().toList().size)
    }
}
