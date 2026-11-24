package com.appresso.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dónde se guarda la sesión iniciada. El resto de la app solo conoce esta interfaz, de modo que
 * cambiar la forma de guardarla no obliga a tocar las pantallas.
 */
interface AlmacenSesion {

    /** La sesión actual, o null si nadie ha iniciado sesión. Se puede observar para reaccionar a cambios. */
    val sesion: StateFlow<Sesion?>

    fun guardar(sesion: Sesion)

    fun borrar()
}

/**
 * Implementación en memoria: la sesión vive solo mientras el proceso esté activo.
 * Útil para pruebas unitarias y entornos donde el proveedor Android Keystore no está disponible.
 * En producción se utiliza [AlmacenSesionKeystore] para almacenamiento seguro y cifrado por hardware.
 */
class AlmacenSesionEnMemoria : AlmacenSesion {

    private val _sesion = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    override fun guardar(sesion: Sesion) {
        _sesion.value = sesion
    }

    override fun borrar() {
        _sesion.value = null
    }
}
