package com.appresso.app.data.remote

import com.appresso.app.data.auth.Rol
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Llamadas de autenticación del backend. Contrato en docs/api-auth.md. */
interface AuthApi {

    /**
     * Se devuelve [Response] y no el cuerpo directamente porque los errores (400, 401, 423) son
     * parte normal del contrato y hay que leer su código y su cuerpo.
     */
    @POST("api/auth/login")
    suspend fun login(@Body solicitud: LoginRequest): Response<LoginResponse>
}

/**
 * [identificador] es el correo (personal) o el número de documento (cliente); ver docs/api-auth.md.
 * El campo se llama `contrasena`, sin ñ, igual que en el backend.
 */
@Serializable
data class LoginRequest(
    val identificador: String,
    val contrasena: String,
) {
    override fun toString() = "LoginRequest(identificador=$identificador, contrasena=***)"
}

@Serializable
data class LoginResponse(
    val token: String,
    val rol: Rol,
    val nombre: String,
) {
    override fun toString() = "LoginResponse(rol=$rol, nombre=$nombre, token=***)"
}

/** Cuerpo de los errores del backend. Ambos campos son opcionales: `campos` solo viene en los 400. */
@Serializable
data class ErrorRespuesta(
    val mensaje: String? = null,
    val campos: Map<String, String>? = null,
)
