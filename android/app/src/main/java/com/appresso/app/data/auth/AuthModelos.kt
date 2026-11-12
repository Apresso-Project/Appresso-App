package com.appresso.app.data.auth

import kotlinx.serialization.Serializable

/** Roles del sistema. Los nombres coinciden con los que devuelve el backend (docs/api-auth.md). */
@Serializable
enum class Rol {
    ADMINISTRADOR,
    CAJERO,
    MESERO,
    COCINERO,
    CLIENTE,
}

/** Datos de la sesión iniciada. El [token] es un dato sensible: nunca debe aparecer en un log. */
data class Sesion(
    val token: String,
    val rol: Rol,
    val nombre: String,
) {
    override fun toString() = "Sesion(rol=$rol, nombre=$nombre, token=***)"
}

/**
 * Resultado de un intento de inicio de sesión, ya traducido de las respuestas del backend
 * (códigos 200, 400, 401, 423 y fallos de red; ver docs/api-auth.md).
 *
 * Los textos que ve el usuario no se definen aquí sino en quien lo consume.
 */
sealed interface ResultadoLogin {

    data class Exito(val sesion: Sesion) : ResultadoLogin

    /** 400: algún dato no es válido. [campos] asocia cada campo con su problema; puede venir vacío. */
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String>) : ResultadoLogin

    /** 401: correo inexistente, contraseña incorrecta o cuenta inactiva (no se puede distinguir cuál). */
    data object CredencialesInvalidas : ResultadoLogin

    /** 423: demasiados intentos fallidos. [mensaje] es el del servidor e incluye los minutos que faltan. */
    data class CuentaBloqueada(val mensaje: String?) : ResultadoLogin

    /** No se pudo hablar con el servidor: sin red, servidor apagado o tiempo de espera agotado. */
    data object SinConexion : ResultadoLogin

    /** El servidor respondió algo inesperado: error 5xx, código desconocido o cuerpo ilegible. */
    data object ErrorDelServidor : ResultadoLogin
}
