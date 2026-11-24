package com.appresso.app.data.cuenta

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.EliminarMisDatosRequest
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.MisDatosApi
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/** Resultado de pedir la eliminación de los datos propios, ya traducido de las respuestas del backend. */
sealed interface ResultadoEliminacion {
    data object Eliminado : ResultadoEliminacion

    /** 400 (contraseña incorrecta) o 423 (cuenta bloqueada). [mensaje] es el del servidor y puede faltar. */
    data class Rechazado(val mensaje: String?) : ResultadoEliminacion

    data object SinConexion : ResultadoEliminacion
    data object ErrorDelServidor : ResultadoEliminacion
}

/** Eliminación de los datos personales por el propio cliente (Ley 1581). Interfaz para las pruebas. */
interface MisDatosRepository {

    /** Es irreversible. Nunca lanza por errores de red o del servidor. */
    suspend fun eliminar(contrasena: String): ResultadoEliminacion
}

class MisDatosRepositoryRemoto(private val api: MisDatosApi) : MisDatosRepository {

    override suspend fun eliminar(contrasena: String): ResultadoEliminacion =
        try {
            val respuesta = api.eliminar(EliminarMisDatosRequest(contrasena))
            when {
                respuesta.isSuccessful -> ResultadoEliminacion.Eliminado
                respuesta.code() == 400 || respuesta.code() == 423 -> ResultadoEliminacion.Rechazado(leerMensaje(respuesta))
                else -> ResultadoEliminacion.ErrorDelServidor
            }
        } catch (e: IOException) {
            ResultadoEliminacion.SinConexion
        }

    private fun leerMensaje(respuesta: Response<*>): String? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it).mensaje }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}
