package com.appresso.app.data.remote

import com.appresso.app.data.auth.Rol
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Administración de usuarios del backend. Contrato en docs/api-usuarios.md. Todas las llamadas
 * exigen `Authorization: Bearer <token>` de un administrador; [autorizacion] lleva ese valor completo.
 */
interface UsuariosApi {

    @GET("api/usuarios")
    suspend fun listar(@Header("Authorization") autorizacion: String): Response<List<UsuarioDto>>

    @POST("api/usuarios")
    suspend fun crear(
        @Header("Authorization") autorizacion: String,
        @Body solicitud: UsuarioCrearBody,
    ): Response<UsuarioDto>

    @PUT("api/usuarios/{id}")
    suspend fun editar(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Long,
        @Body solicitud: UsuarioEditarBody,
    ): Response<UsuarioDto>

    @POST("api/usuarios/{id}/desactivar")
    suspend fun desactivar(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Long,
    ): Response<UsuarioDto>

    @POST("api/usuarios/{id}/activar")
    suspend fun activar(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Long,
    ): Response<UsuarioDto>
}

/** Usuario como lo envía el backend. La fecha de creación no se usa en la app, así que se ignora. */
@Serializable
data class UsuarioDto(
    val id: Long,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val activo: Boolean,
)

@Serializable
data class UsuarioCrearBody(
    val nombre: String,
    val correo: String,
    val contrasena: String,
    val rol: Rol,
) {
    override fun toString() = "UsuarioCrearBody(nombre=$nombre, correo=$correo, rol=$rol, contrasena=***)"
}

/** [contrasena] null no se envía (el JSON no lleva valores por defecto): el backend la deja igual. */
@Serializable
data class UsuarioEditarBody(
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val contrasena: String? = null,
) {
    override fun toString() = "UsuarioEditarBody(nombre=$nombre, correo=$correo, rol=$rol, contrasena=***)"
}
