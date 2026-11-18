package com.appresso.app.data.clientes

interface ClientesRepository {

    suspend fun listar(): ResultadoClientes<List<Cliente>>

    suspend fun registrar(solicitud: ClienteCrearRequest): ResultadoClientes<Cliente>

    suspend fun buscarPorDocumento(documento: String): ResultadoClientes<Cliente>

    suspend fun eliminar(id: Long): ResultadoClientes<Unit>

    suspend fun solicitarEliminacion(id: Long, motivo: String? = null): ResultadoClientes<Unit>
}
