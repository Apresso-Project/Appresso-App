package com.appresso.app.data.auth

import com.appresso.app.data.remote.AuthApi
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.LoginRequest
import com.appresso.app.data.remote.LoginResponse
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/** Inicia sesión contra el backend y traduce cada respuesta del contrato a un [ResultadoLogin]. */
class AuthRepositoryRemoto(private val api: AuthApi) : AuthRepository {

    override suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoLogin =
        try {
            traducir(api.login(LoginRequest(correo, contrasena)))
        } catch (e: IOException) {
            // Sin red, servidor inalcanzable, conexión caída o tiempo de espera agotado.
            ResultadoLogin.SinConexion
        } catch (e: SerializationException) {
            // El servidor respondió 2xx pero con un cuerpo que no se puede leer (o un rol desconocido).
            ResultadoLogin.ErrorDelServidor
        }

    private fun traducir(respuesta: Response<LoginResponse>): ResultadoLogin = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null) {
                ResultadoLogin.ErrorDelServidor
            } else {
                ResultadoLogin.Exito(Sesion(cuerpo.token, cuerpo.rol, cuerpo.nombre))
            }
        }

        respuesta.code() == 400 -> {
            val error = leerError(respuesta)
            ResultadoLogin.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
        }

        respuesta.code() == 401 -> ResultadoLogin.CredencialesInvalidas

        respuesta.code() == 423 -> ResultadoLogin.CuentaBloqueada(leerError(respuesta)?.mensaje)

        else -> ResultadoLogin.ErrorDelServidor
    }

    /** Lee el cuerpo de un error; si falta o no es JSON válido devuelve null en vez de fallar. */
    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}
