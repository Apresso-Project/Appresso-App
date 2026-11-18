package com.appresso.app.data.remote

import com.appresso.app.data.clientes.Cliente
import com.appresso.app.data.clientes.ClienteCrearRequest
import com.appresso.app.data.clientes.SolicitudEliminacionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ClientesApi {

    @POST("api/clientes")
    suspend fun registrar(@Body solicitud: ClienteCrearRequest): Response<Cliente>

    @GET("api/clientes")
    suspend fun listar(): Response<List<Cliente>>

    @GET("api/clientes/{documento}")
    suspend fun buscarPorDocumento(@Path("documento") documento: String): Response<Cliente>

    @DELETE("api/clientes/{id}")
    suspend fun eliminar(@Path("id") id: Long): Response<Unit>

    @POST("api/clientes/{id}/solicitar-eliminacion")
    suspend fun solicitarEliminacion(
        @Path("id") id: Long,
        @Body solicitud: SolicitudEliminacionRequest = SolicitudEliminacionRequest(),
    ): Response<Unit>
}
