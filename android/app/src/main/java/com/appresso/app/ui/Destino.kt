package com.appresso.app.ui

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion

/** A qué pantalla debe ir la app según haya o no una sesión iniciada. */
sealed interface Destino {

    data object Login : Destino

    data class InicioAdmin(val nombre: String) : Destino

    /** Roles que todavía no tienen pantalla propia (llegan con los demás módulos). */
    data class SinPantalla(val rol: Rol) : Destino
}

fun destinoPara(sesion: Sesion?): Destino = when {
    sesion == null -> Destino.Login
    sesion.rol == Rol.ADMINISTRADOR -> Destino.InicioAdmin(sesion.nombre)
    else -> Destino.SinPantalla(sesion.rol)
}
