package com.appresso.app.data.clientes

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import com.appresso.app.data.remote.ErrorRespuesta
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

class ClientesRepositoryRemoto(private val api: ClientesApi) : ClientesRepository {

    override suspend fun listar(): ResultadoClientes<List<Cliente>> =
        ejecutar({ api.listar() }) { it }

    override suspend fun registrar(solicitud: ClienteCrearRequest): ResultadoClientes<Cliente> =
        ejecutar({ api.registrar(solicitud) }) { it }

    override suspend fun buscarPorDocumento(documento: String): ResultadoClientes<Cliente> =
        ejecutar({ api.buscarPorDocumento(documento) }) { it }

    override suspend fun eliminar(id: Long): ResultadoClientes<Unit> =
        ejecutar({ api.eliminar(id) }) { Unit }

    override suspend fun solicitarEliminacion(id: Long, motivo: String?): ResultadoClientes<Unit> =
        ejecutar({ api.solicitarEliminacion(id, SolicitudEliminacionRequest(motivo)) }) { Unit }

    private suspend fun <T, R> ejecutar(
        llamada: suspend () -> Response<T>,
        transformar: (T) -> R,
    ): ResultadoClientes<R> =
        try {
            traducir(llamada(), transformar)
        } catch (e: IOException) {
            ResultadoClientes.SinConexion
        } catch (e: SerializationException) {
            ResultadoClientes.ErrorDelServidor
        }

    private fun <T, R> traducir(respuesta: Response<T>, transformar: (T) -> R): ResultadoClientes<R> = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null && respuesta.code() != 204) {
                ResultadoClientes.ErrorDelServidor
            } else {
                @Suppress("UNCHECKED_CAST")
                ResultadoClientes.Exito(if (cuerpo != null) transformar(cuerpo) else Unit as R)
            }
        }

        respuesta.code() == 400 -> {
            val error = leerError(respuesta)
            ResultadoClientes.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
        }

        respuesta.code() == 403 -> ResultadoClientes.SinPermiso(leerError(respuesta)?.mensaje)

        respuesta.code() == 404 -> ResultadoClientes.NoEncontrado(leerError(respuesta)?.mensaje)

        respuesta.code() == 409 -> {
            val error = leerError(respuesta)
            ResultadoClientes.Conflicto(error?.mensaje)
        }

        else -> ResultadoClientes.ErrorDelServidor
    }

    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: Exception) {
            null
        }
}
