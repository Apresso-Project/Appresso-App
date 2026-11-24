package com.appresso.app.data.auth

import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** El cifrado con una clave AES-256 por software: el Keystore solo existe en un dispositivo. */
class CifradorSesionTest {

    private val cifrador = CifradorSesion(claveFija())

    @Test
    fun `lo cifrado se recupera igual`() {
        val texto = """{"token":"abc.def.ghi","nombre":"Íñigo Peña"}"""

        assertEquals(texto, cifrador.descifrar(cifrador.cifrar(texto)))
    }

    @Test
    fun `lo cifrado no contiene el texto`() {
        val cifrado = cifrador.cifrar("token-secreto")

        assertFalse(cifrado.contains("token-secreto"))
        assertFalse(String(Base64.getDecoder().decode(cifrado), Charsets.ISO_8859_1).contains("token-secreto"))
    }

    @Test
    fun `cifrar dos veces lo mismo da resultados distintos`() {
        assertNotEquals(cifrador.cifrar("token"), cifrador.cifrar("token"))
    }

    @Test
    fun `un dato alterado no se descifra`() {
        val bytes = Base64.getDecoder().decode(cifrador.cifrar("token"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        assertThrows(Exception::class.java) { cifrador.descifrar(Base64.getEncoder().encodeToString(bytes)) }
    }

    @Test
    fun `con otra clave no se descifra`() {
        val cifrado = cifrador.cifrar("token")

        assertThrows(Exception::class.java) { CifradorSesion(claveFija()).descifrar(cifrado) }
    }

    @Test
    fun `un dato incompleto o que no es Base64 se rechaza`() {
        assertThrows(Exception::class.java) { cifrador.descifrar("") }
        assertThrows(Exception::class.java) { cifrador.descifrar("AAAA") }
        assertThrows(Exception::class.java) { cifrador.descifrar("token en claro") }
    }

    private fun claveFija(): () -> SecretKey {
        val clave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return { clave }
    }
}
