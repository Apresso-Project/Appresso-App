package com.appresso.app.data.auth

/** Punto de entrada para iniciar sesión. Existe como interfaz para poder sustituirlo en las pruebas. */
interface AuthRepository {

    /**
     * [identificador] es el correo del personal o el documento del cliente.
     * Nunca lanza por errores de red o del servidor: los devuelve como [ResultadoLogin].
     */
    suspend fun iniciarSesion(identificador: String, contrasena: String): ResultadoLogin
}
