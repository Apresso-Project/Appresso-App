package com.appresso.app.data.clientes

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import com.appresso.app.data.remote.ErrorRespuesta
import java.io.IOException
import kotlinx.serialization.SerializationException

/** Busca clientes en el backend y traduce cada respuesta a un [ResultadoBusqueda]. */
class ClientesRepositoryRemoto(private val api: ClientesApi) : ClientesRepository {

    override suspend fun buscarPorDocumento(documento: String): ResultadoBusqueda =
        try {
            val respuesta = api.buscarPorDocumento(documento)
            val cuerpo = respuesta.body()
            when {
                respuesta.isSuccessful && cuerpo != null -> ResultadoBusqueda.Encontrado(cuerpo)
                respuesta.code() == 404 -> ResultadoBusqueda.NoEncontrado
                respuesta.code() == 403 -> ResultadoBusqueda.SinPermiso(leerMensaje(respuesta.errorBody()?.string()))
                else -> ResultadoBusqueda.ErrorDelServidor
            }
        } catch (e: IOException) {
            ResultadoBusqueda.SinConexion
        } catch (e: SerializationException) {
            ResultadoBusqueda.ErrorDelServidor
        }

    private fun leerMensaje(cuerpo: String?): String? =
        try {
            cuerpo?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it).mensaje }
        } catch (e: SerializationException) {
            null
        }
}
