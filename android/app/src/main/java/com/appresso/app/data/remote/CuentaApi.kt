package com.appresso.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Llamadas públicas de la cuenta del cliente: registrarse (RF-50) y recuperar la contraseña con un
 * código enviado al correo (RF-65). No llevan token. Contrato en docs/api-clientes.md y
 * docs/api-auth.md.
 */
interface CuentaApi {

    @POST("api/auth/registro")
    suspend fun registrar(@Body solicitud: RegistroClienteRequest): Response<RegistroClienteRespuesta>

    @POST("api/auth/recuperar")
    suspend fun solicitarCodigo(@Body solicitud: SolicitarCodigoRequest): Response<MensajeRespuesta>

    @POST("api/auth/restablecer")
    suspend fun restablecer(@Body solicitud: RestablecerContrasenaRequest): Response<MensajeRespuesta>
}

@Serializable
data class RegistroClienteRequest(
    val nombre: String,
    val apellido: String,
    val documento: String,
    val celular: String,
    val correo: String,
    val contrasena: String,
    val aceptoTratamientoDatos: Boolean,
) {
    override fun toString() = "RegistroClienteRequest(documento=$documento, contrasena=***)"
}

@Serializable
data class RegistroClienteRespuesta(
    val mensaje: String? = null,
    val nombre: String? = null,
    val puntos: Int = 0,
)

@Serializable
data class SolicitarCodigoRequest(val documento: String)

@Serializable
data class RestablecerContrasenaRequest(
    val documento: String,
    val codigo: String,
    val contrasenaNueva: String,
) {
    override fun toString() = "RestablecerContrasenaRequest(documento=$documento, codigo=***, contrasenaNueva=***)"
}

@Serializable
data class MensajeRespuesta(val mensaje: String? = null)
