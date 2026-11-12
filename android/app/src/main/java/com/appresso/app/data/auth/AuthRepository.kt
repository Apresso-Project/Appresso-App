package com.appresso.app.data.auth

/** Punto de entrada para iniciar sesión. Existe como interfaz para poder sustituirlo en las pruebas. */
interface AuthRepository {

    /** Nunca lanza por errores de red o del servidor: los devuelve como [ResultadoLogin]. */
    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin
}
