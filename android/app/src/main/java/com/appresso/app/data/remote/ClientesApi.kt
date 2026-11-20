package com.appresso.app.data.remote

import com.appresso.app.data.clientes.Cliente
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Consulta de clientes por el personal (mesero y administrador, RF-51). El personal no registra,
 * edita ni elimina clientes: cada cliente crea su cuenta desde la app. Contrato en
 * docs/api-clientes.md.
 */
interface ClientesApi {

    @GET("api/clientes/{documento}")
    suspend fun buscarPorDocumento(@Path("documento") documento: String): Response<Cliente>
}
