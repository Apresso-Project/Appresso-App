package com.appresso.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Lo que el cliente hace con sus propios datos (Ley 1581). Contrato en docs/api-clientes.md. */
interface MisDatosApi {

    @POST("api/clientes/yo/eliminacion")
    suspend fun eliminar(@Body solicitud: EliminarMisDatosRequest): Response<Unit>
}

@Serializable
data class EliminarMisDatosRequest(val contrasena: String) {
    override fun toString() = "EliminarMisDatosRequest(contrasena=***)"
}
