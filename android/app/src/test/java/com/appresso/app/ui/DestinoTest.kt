package com.appresso.app.ui

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinoTest {

    @Test
    fun `sin sesion se muestra el login`() {
        assertEquals(Destino.Login, destinoPara(null))
    }

    @Test
    fun `un administrador va a su pantalla con su nombre`() {
        val destino = destinoPara(Sesion("token", Rol.ADMINISTRADOR, "Administrador de prueba"))

        assertEquals(Destino.InicioAdmin("Administrador de prueba"), destino)
    }

    @Test
    fun `los demas roles van al aviso provisional con su rol`() {
        listOf(Rol.CAJERO, Rol.MESERO, Rol.COCINERO, Rol.CLIENTE).forEach { rol ->
            assertEquals(rol.name, Destino.SinPantalla(rol), destinoPara(Sesion("token", rol, "Ana")))
        }
    }
}
