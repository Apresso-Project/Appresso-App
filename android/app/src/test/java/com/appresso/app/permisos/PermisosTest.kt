package com.appresso.app.permisos

import com.appresso.app.data.auth.Rol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué ve cada rol (SCRUM-48), verificado con los cinco roles. Las listas esperadas salen de
 * docs/permisos-por-rol.md: si cambia la política, cambian aquí a propósito.
 */
class PermisosTest {

    // ---------------------------------------------------------------- menú de cada rol

    @Test
    fun `el administrador ve la gestion del local pero no las tareas operativas`() {
        assertEquals(
            listOf(
                Opcion.USUARIOS, Opcion.PRODUCTOS_E_INVENTARIO, Opcion.PEDIDOS, Opcion.CAJA,
                Opcion.SOLICITUDES_RETIRO, Opcion.CLIENTES, Opcion.RECOMPENSAS_Y_PROMOCIONES,
            ),
            opcionesPara(Rol.ADMINISTRADOR),
        )
    }

    @Test
    fun `el cajero ve cobros pedidos caja y solicitudes de retiro`() {
        assertEquals(
            listOf(Opcion.COBROS, Opcion.PEDIDOS, Opcion.CAJA, Opcion.SOLICITUDES_RETIRO),
            opcionesPara(Rol.CAJERO),
        )
    }

    @Test
    fun `el mesero ve mesas y comandas cobros y clientes`() {
        assertEquals(listOf(Opcion.MESAS_Y_COMANDAS, Opcion.COBROS, Opcion.CLIENTES), opcionesPara(Rol.MESERO))
    }

    @Test
    fun `el cocinero solo ve cocina`() {
        assertEquals(listOf(Opcion.COCINA), opcionesPara(Rol.COCINERO))
    }

    @Test
    fun `el cliente ve sus puntos y las recompensas`() {
        assertEquals(listOf(Opcion.MIS_PUNTOS, Opcion.RECOMPENSAS_Y_PROMOCIONES), opcionesPara(Rol.CLIENTE))
    }

    @Test
    fun `solo el administrador ve usuarios`() {
        Rol.entries.forEach { rol ->
            assertEquals(rol.name, rol == Rol.ADMINISTRADOR, puedeVer(rol, Opcion.USUARIOS))
        }
    }

    // ---------------------------------------------------------------- acciones (botones)

    @Test
    fun `acciones exclusivas de un rol segun los requerimientos`() {
        val exclusivas = mapOf(
            Accion.COBRAR_EFECTIVO to Rol.CAJERO, // RF-29
            Accion.CAMBIAR_ESTADO_PREPARACION to Rol.COCINERO, // RF-25
            Accion.CREAR_COMANDA to Rol.MESERO, // RF-15
            Accion.CERRAR_CAJA to Rol.CAJERO, // RF-44
            Accion.DECIDIR_RETIRO to Rol.ADMINISTRADOR, // RF-36
            Accion.VER_MIS_PUNTOS to Rol.CLIENTE, // RF-55
        )
        exclusivas.forEach { (accion, unico) ->
            Rol.entries.forEach { rol ->
                assertEquals("$accion / $rol", rol == unico, puede(rol, accion))
            }
        }
    }

    @Test
    fun `el administrador no cobra en efectivo ni crea comandas`() {
        assertFalse(puede(Rol.ADMINISTRADOR, Accion.COBRAR_EFECTIVO))
        assertFalse(puede(Rol.ADMINISTRADOR, Accion.CREAR_COMANDA))
    }

    // ---------------------------------------------------------------- coherencia

    @Test
    fun `cada opcion del menu de un rol tiene al menos una accion que ese rol puede hacer`() {
        Rol.entries.forEach { rol ->
            opcionesPara(rol).forEach { opcion ->
                assertTrue("$rol ve $opcion pero no puede hacer nada ahí", opcion.acciones.any { puede(rol, it) })
            }
        }
    }

    @Test
    fun `toda accion permitida a un rol se alcanza desde alguna opcion de su menu`() {
        Rol.entries.forEach { rol ->
            Accion.entries.filter { puede(rol, it) }.forEach { accion ->
                assertTrue(
                    "$rol puede $accion pero ninguna opción de su menú la incluye",
                    opcionesPara(rol).any { accion in it.acciones },
                )
            }
        }
    }

    @Test
    fun `todos los roles tienen al menos una opcion`() {
        Rol.entries.forEach { rol -> assertTrue(rol.name, opcionesPara(rol).isNotEmpty()) }
    }
}
