package com.appresso.app.data.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenSesionTest {

    private fun token(cuerpo: String): String {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        return "${b64.encodeToString("{}".toByteArray())}.${b64.encodeToString(cuerpo.toByteArray())}.firma"
    }

    @Test
    fun `lee el id del campo sub`() {
        assertEquals(42L, TokenSesion.idDeUsuario(token("""{"sub":"42","rol":"ADMINISTRADOR"}""")))
    }

    @Test
    fun `devuelve null si el token no se puede leer`() {
        assertNull(TokenSesion.idDeUsuario("no-es-un-token"))
        assertNull(TokenSesion.idDeUsuario("a.%%%.c"))
        assertNull(TokenSesion.idDeUsuario(token("no es json")))
        assertNull(TokenSesion.idDeUsuario(token("""{"sub":"abc"}""")))
        assertNull(TokenSesion.idDeUsuario(token("""{"rol":"MESERO"}""")))
    }
}
