package com.appresso.app.data.usuarios

import com.appresso.app.data.auth.Rol

/** Una cuenta tal como la lista el backend (docs/api-usuarios.md). Nunca incluye la contraseña. */
data class Usuario(
    val id: Long,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val activo: Boolean,
)

/**
 * Datos que el administrador escribe en el formulario. Al crear la [contrasena] es obligatoria;
 * al editar, null significa "no cambiarla".
 */
data class DatosUsuario(
    val nombre: String,
    val correo: String,
    val contrasena: String?,
    val rol: Rol,
) {
    override fun toString() = "DatosUsuario(nombre=$nombre, correo=$correo, rol=$rol, contrasena=***)"
}

/**
 * Resultado de una operación sobre usuarios, ya traducido de las respuestas del backend
 * (200/201, 400, 401, 403, 404, 409 y fallos de red; ver docs/api-usuarios.md).
 * Los textos que ve la persona no se definen aquí sino en quien lo consume.
 */
sealed interface ResultadoUsuarios<out T> {

    data class Exito<T>(val valor: T) : ResultadoUsuarios<T>

    /** 400: algún dato no es válido. [campos] asocia cada campo con su problema. */
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String>) : ResultadoUsuarios<Nothing>

    /**
     * 409: el correo ya existe (viene con `campos.correo`) o se violó una regla de negocio
     * (por ejemplo, desactivarse a uno mismo; viene solo [mensaje]).
     */
    data class Conflicto(val mensaje: String?, val campos: Map<String, String>) : ResultadoUsuarios<Nothing>

    /** 401: no hay sesión, el token venció o la cuenta ya no está activa. */
    data object SesionInvalida : ResultadoUsuarios<Nothing>

    /** 403: la cuenta no es de administrador. [mensaje] es el del servidor. */
    data class SinPermiso(val mensaje: String?) : ResultadoUsuarios<Nothing>

    /** 404: el usuario ya no existe. */
    data object NoEncontrado : ResultadoUsuarios<Nothing>

    data object SinConexion : ResultadoUsuarios<Nothing>

    data object ErrorDelServidor : ResultadoUsuarios<Nothing>
}
