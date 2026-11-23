package com.appresso.app.data.catalogo

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Ejecuta una llamada del menú o de la cocina y la traduce a un [ResultadoCatalogo]: 2xx con cuerpo →
 * éxito; 400, 401, 403, 404 y 409 → su resultado; fallo de red → sin conexión; cualquier otra cosa →
 * error del servidor. Misma traducción que [CatalogoRepositoryRemoto], para los repositorios nuevos
 * de solo lectura. Nunca lanza excepciones de red.
 */
internal suspend fun <T, R> ejecutarConsulta(
    llamada: suspend () -> Response<T>,
    transformar: (T) -> R,
): ResultadoCatalogo<R> =
    try {
        traducirRespuesta(llamada(), transformar)
    } catch (e: IOException) {
        ResultadoCatalogo.SinConexion
    } catch (e: SerializationException) {
        // Respondió 2xx con un cuerpo ilegible.
        ResultadoCatalogo.ErrorDelServidor
    }

internal fun <T, R> traducirRespuesta(respuesta: Response<T>, transformar: (T) -> R): ResultadoCatalogo<R> = when {
    respuesta.isSuccessful -> {
        val cuerpo = respuesta.body()
        if (cuerpo == null) ResultadoCatalogo.ErrorDelServidor else ResultadoCatalogo.Exito(transformar(cuerpo))
    }

    respuesta.code() == 400 -> {
        val error = leerErrorDe(respuesta)
        ResultadoCatalogo.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
    }

    respuesta.code() == 401 -> ResultadoCatalogo.SesionInvalida

    respuesta.code() == 403 -> ResultadoCatalogo.SinPermiso(leerErrorDe(respuesta)?.mensaje)

    respuesta.code() == 404 -> ResultadoCatalogo.NoEncontrado

    respuesta.code() == 409 -> {
        val error = leerErrorDe(respuesta)
        ResultadoCatalogo.Conflicto(error?.mensaje, error?.campos.orEmpty())
    }

    else -> ResultadoCatalogo.ErrorDelServidor
}

/** Si el cuerpo falta o no es JSON válido devuelve null en vez de fallar. */
private fun leerErrorDe(respuesta: Response<*>): ErrorRespuesta? =
    try {
        respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
    } catch (e: SerializationException) {
        null
    } catch (e: IOException) {
        null
    }
