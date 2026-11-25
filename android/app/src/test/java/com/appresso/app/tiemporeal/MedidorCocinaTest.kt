package com.appresso.app.tiemporeal

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La medición del plazo de 3 s de cocina (SCRUM-93), con un reloj simulado. */
class MedidorCocinaTest {

    private fun t(ms: Long) = Instant.ofEpochMilli(1_000_000L + ms)

    private var ahora = t(0)
    private val lineas = mutableListOf<String>()
    private val medidor = MedidorCocina(reloj = { ahora }, registro = { lineas += it })

    private fun cambio(publicadoMs: Long?, recibidoMs: Long, tipo: String = "COMANDA_ESTADO_CAMBIADO") =
        CambioCocina(tipo, publicadoMs?.let(::t), t(recibidoMs))

    @Test
    fun `mide llegada actualizacion y total de un cambio`() {
        // El servidor publica en 0, el teléfono lo recibe en 150 y la lista está en pantalla en 400.
        ahora = t(400)

        val m = medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 150))

        assertEquals(150L, m.llegadaMs)
        assertEquals(250L, m.actualizacionMs)
        assertEquals(400L, m.totalMs)
        assertEquals(1, medidor.cantidad())
    }

    @Test
    fun `una resincronizacion no tiene fecha del servidor y solo mide la actualizacion`() {
        ahora = t(300)

        val m = medidor.alMostrarse(cambio(publicadoMs = null, recibidoMs = 100, tipo = CambioCocina.RESINCRONIZACION))

        assertNull(m.llegadaMs)
        assertNull(m.totalMs)
        assertEquals(200L, m.actualizacionMs)
        assertNull(medidor.percentil95())
        assertEquals(0.0, medidor.porcentajeBajoLimite(), 0.0)
    }

    @Test
    fun `percentil 95 maximo y porcentaje bajo 3 segundos`() {
        // 20 cambios: 19 de 1 a 19 décimas de segundo (100 a 1900 ms) y uno lento de 3500 ms.
        (1..19).forEach { i ->
            ahora = t(i * 100L)
            medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 0))
        }
        ahora = t(3_500)
        medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 0))

        assertEquals(1_900L, medidor.percentil95())
        assertEquals(3_500L, medidor.maximo())
        assertEquals(95.0, medidor.porcentajeBajoLimite(), 0.001)
    }

    @Test
    fun `exactamente 3 segundos ya no cuenta como bajo el plazo`() {
        ahora = t(3_000)
        medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 0))

        assertEquals(0.0, medidor.porcentajeBajoLimite(), 0.0)
    }

    @Test
    fun `cada muestra escribe una linea legible con el resumen`() {
        ahora = t(400)
        medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 150))

        val linea = lineas.single()
        assertTrue(linea, linea.startsWith("evento=COMANDA_ESTADO_CAMBIADO llegada=150 ms actualizacion=250 ms total=400 ms"))
        assertTrue(linea, linea.contains("n=1, p95=400 ms, máximo=400 ms, bajo 3 s: 100.0 %"))
    }

    @Test
    fun `la linea de una resincronizacion marca n-d donde no hay fecha`() {
        ahora = t(300)
        medidor.alMostrarse(cambio(publicadoMs = null, recibidoMs = 100, tipo = CambioCocina.RESINCRONIZACION))

        val linea = lineas.single()
        assertTrue(linea, linea.contains("llegada=n/d actualizacion=200 ms total=n/d"))
        assertTrue(linea, linea.contains("Sin muestras con fecha del servidor"))
    }

    @Test
    fun `un reloj de servidor adelantado da una llegada negativa que se ve en el registro`() {
        // El teléfono recibe «antes» de la hora que dice el servidor: relojes desincronizados.
        ahora = t(500)

        val m = medidor.alMostrarse(cambio(publicadoMs = 800, recibidoMs = 100))

        assertEquals(-700L, m.llegadaMs)
        assertTrue(lineas.single().contains("llegada=-700 ms"))
    }

    @Test
    fun `reiniciar borra las muestras`() {
        ahora = t(100)
        medidor.alMostrarse(cambio(publicadoMs = 0, recibidoMs = 0))

        medidor.reiniciar()

        assertEquals(0, medidor.cantidad())
        assertNull(medidor.maximo())
    }

    @Test
    fun `el resumen sin muestras lo dice`() {
        assertEquals("Sin muestras con fecha del servidor (n=0)", medidor.resumen())
    }
}
