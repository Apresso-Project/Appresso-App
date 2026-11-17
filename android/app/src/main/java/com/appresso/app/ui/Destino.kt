package com.appresso.app.ui

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.permisos.Opcion
import com.appresso.app.permisos.opcionesPara
import com.appresso.app.permisos.puedeVer

/** A qué pantalla debe ir la app según la sesión y la sección que el usuario abrió. */
sealed interface Destino {

    data object Login : Destino

    /** El menú de inicio del rol, solo con las opciones que le corresponden (SCRUM-48). */
    data class Inicio(val nombre: String, val rol: Rol, val opciones: List<Opcion>) : Destino

    /** Una sección del menú ya abierta. */
    data class Seccion(val opcion: Opcion, val rol: Rol) : Destino
}

/**
 * @param abierta la sección que el usuario tocó en el menú, o null si está en el inicio.
 *   Si el rol no puede verla, se ignora y se muestra el inicio: nunca se abre una sección ajena.
 */
fun destinoPara(sesion: Sesion?, abierta: Opcion? = null): Destino = when {
    sesion == null -> Destino.Login
    abierta != null && puedeVer(sesion.rol, abierta) -> Destino.Seccion(abierta, sesion.rol)
    else -> Destino.Inicio(sesion.nombre, sesion.rol, opcionesPara(sesion.rol))
}
