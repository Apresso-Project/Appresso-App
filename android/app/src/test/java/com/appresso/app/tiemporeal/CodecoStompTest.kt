package com.appresso.app.tiemporeal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecoStompTest {

    @Test
    fun codificaSubscribeConSusCabeceras() {
        val texto = CodecoStomp.codificar(
            MarcoStomp("SUBSCRIBE", linkedMapOf("id" to "sub-0", "destination" to "/topic/comandas")),
        )
        assertEquals("SUBSCRIBE\nid:sub-0\ndestination:/topic/comandas\n\n\u0000", texto)
    }

    @Test
    fun decodificaMessageDeSpring() {
        val json = """{"id":"a","secuencia":1}"""
        val texto = "MESSAGE\nsubscription:sub-0\nmessage-id:abc\ndestination:/topic/comandas\ncontent-length:${json.length}\n\n$json\u0000"
        val marco = CodecoStomp.decodificar(texto)!!
        assertEquals("MESSAGE", marco.comando)
        assertEquals("/topic/comandas", marco.cabeceras["destination"])
        assertEquals(json, marco.cuerpo)
    }

    @Test
    fun conectedSinCuerpo() {
        val marco = CodecoStomp.decodificar("CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\u0000")!!
        assertEquals("CONNECTED", marco.comando)
        assertEquals("10000,10000", marco.cabeceras["heart-beat"])
        assertEquals("", marco.cuerpo)
    }

    @Test
    fun unLatidoNoEsUnMarco() {
        assertNull(CodecoStomp.decodificar("\n"))
        assertNull(CodecoStomp.decodificar("\r\n"))
        assertNull(CodecoStomp.decodificar(""))
    }

    @Test
    fun aceptaFinesDeLineaWindows() {
        val marco = CodecoStomp.decodificar("MESSAGE\r\ndestination:/topic/catalogo\r\n\r\n{}\u0000")!!
        assertEquals("/topic/catalogo", marco.cabeceras["destination"])
        assertEquals("{}", marco.cuerpo)
    }

    @Test
    fun escapaYDesescapaCabeceras() {
        val original = MarcoStomp("SEND", mapOf("a:b" to "x\ny\\z"), "hola")
        val ida = CodecoStomp.codificar(original)
        assertEquals("SEND\na\\cb:x\\ny\\\\z\n\nhola\u0000", ida)
        assertEquals(original, CodecoStomp.decodificar(ida))
    }

    @Test
    fun connectNoEscapaCabeceras() {
        val texto = CodecoStomp.codificar(MarcoStomp("CONNECT", linkedMapOf("heart-beat" to "10000,10000")))
        assertEquals("CONNECT\nheart-beat:10000,10000\n\n\u0000", texto)
    }
}
