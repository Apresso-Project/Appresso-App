package com.appresso.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlmacenSesionEnMemoriaTest {

    private val almacen = AlmacenSesionEnMemoria()

    @Test
    fun `al empezar no hay sesion`() {
        assertNull(almacen.sesion.value)
    }

    @Test
    fun `guardar deja la sesion disponible`() {
        val sesion = Sesion("token", Rol.ADMINISTRADOR, "Admin")

        almacen.guardar(sesion)

        assertEquals(sesion, almacen.sesion.value)
    }

    @Test
    fun `guardar de nuevo reemplaza la sesion anterior`() {
        almacen.guardar(Sesion("uno", Rol.MESERO, "Ana"))
        val segunda = Sesion("dos", Rol.CAJERO, "Luis")

        almacen.guardar(segunda)

        assertEquals(segunda, almacen.sesion.value)
    }

    @Test
    fun `borrar cierra la sesion`() {
        almacen.guardar(Sesion("token", Rol.MESERO, "Ana"))

        almacen.borrar()

        assertNull(almacen.sesion.value)
    }

    @Test
    fun `borrar sin sesion no falla`() {
        almacen.borrar()

        assertNull(almacen.sesion.value)
    }
}
