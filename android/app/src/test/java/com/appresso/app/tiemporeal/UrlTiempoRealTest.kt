package com.appresso.app.tiemporeal

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlTiempoRealTest {

    @Test
    fun httpPasaAWsConLaRutaDelWebSocket() {
        assertEquals("ws://10.0.2.2:8080/ws/websocket", urlTiempoReal("http://10.0.2.2:8080/"))
    }

    @Test
    fun httpsPasaAWss() {
        assertEquals("wss://appresso.example/ws/websocket", urlTiempoReal("https://appresso.example/"))
    }

    @Test
    fun aceptaLaUrlSinBarraFinal() {
        assertEquals("ws://192.168.1.10:8080/ws/websocket", urlTiempoReal("http://192.168.1.10:8080"))
    }

    @Test
    fun conservaUnPrefijoDeRuta() {
        assertEquals("wss://servidor.co/appresso/ws/websocket", urlTiempoReal("HTTPS://servidor.co/appresso/"))
    }
}
