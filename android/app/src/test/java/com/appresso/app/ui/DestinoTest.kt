package com.appresso.app.ui

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.permisos.Opcion
import com.appresso.app.permisos.opcionesPara
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinoTest {

    @Test
    fun `sin sesion se muestra el login`() {
        assertEquals(Destino.Login, destinoPara(null))
    }

    @Test
    fun `sin sesion se muestra el login aunque haya una seccion abierta`() {
        assertEquals(Destino.Login, destinoPara(null, Opcion.USUARIOS))
    }

    @Test
    fun `cada rol llega a su inicio con su nombre y solo sus opciones`() {
        Rol.entries.forEach { rol ->
            val destino = destinoPara(Sesion("token", rol, "Ana"))

            assertEquals(rol.name, Destino.Inicio("Ana", rol, opcionesPara(rol)), destino)
        }
    }

    @Test
    fun `una seccion permitida se abre`() {
        val destino = destinoPara(Sesion("token", Rol.COCINERO, "Ana"), Opcion.COCINA)

        assertEquals(Destino.Seccion(Opcion.COCINA, Rol.COCINERO), destino)
    }

    @Test
    fun `una seccion de otro rol nunca se abre y se queda en el inicio`() {
        val destino = destinoPara(Sesion("token", Rol.MESERO, "Ana"), Opcion.USUARIOS)

        assertEquals(Destino.Inicio("Ana", Rol.MESERO, opcionesPara(Rol.MESERO)), destino)
    }
}
