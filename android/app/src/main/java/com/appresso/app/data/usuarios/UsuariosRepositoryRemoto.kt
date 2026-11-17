package com.appresso.app.data.usuarios

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.UsuarioCrearBody
import com.appresso.app.data.remote.UsuarioDto
import com.appresso.app.data.remote.UsuarioEditarBody
import com.appresso.app.data.remote.UsuariosApi
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Habla con `/api/usuarios` y traduce cada respuesta del contrato a un [ResultadoUsuarios]. El token
 * y el cierre de sesión ante un 401 los maneja el cliente HTTP compartido (`InterceptorSesion`).
 */
class UsuariosRepositoryRemoto(private val api: UsuariosApi) : UsuariosRepository {

    override suspend fun listar(): ResultadoUsuarios<List<Usuario>> =
        ejecutar({ api.listar() }) { lista -> lista.map { it.aUsuario() } }

    override suspend fun crear(datos: DatosUsuario): ResultadoUsuarios<Usuario> =
        ejecutar({
            api.crear(UsuarioCrearBody(datos.nombre, datos.correo, datos.contrasena.orEmpty(), datos.rol))
        }) { it.aUsuario() }

    override suspend fun editar(id: Long, datos: DatosUsuario): ResultadoUsuarios<Usuario> =
        ejecutar({
            api.editar(id, UsuarioEditarBody(datos.nombre, datos.correo, datos.rol, datos.contrasena))
        }) { it.aUsuario() }

    override suspend fun desactivar(id: Long): ResultadoUsuarios<Usuario> =
        ejecutar({ api.desactivar(id) }) { it.aUsuario() }

    override suspend fun activar(id: Long): ResultadoUsuarios<Usuario> =
        ejecutar({ api.activar(id) }) { it.aUsuario() }

    private suspend fun <T, R> ejecutar(
        llamada: suspend () -> Response<T>,
        transformar: (T) -> R,
    ): ResultadoUsuarios<R> =
        try {
            traducir(llamada(), transformar)
        } catch (e: IOException) {
            ResultadoUsuarios.SinConexion
        } catch (e: SerializationException) {
            // Respondió 2xx con un cuerpo ilegible (o un rol que la app no conoce).
            ResultadoUsuarios.ErrorDelServidor
        }

    private fun <T, R> traducir(respuesta: Response<T>, transformar: (T) -> R): ResultadoUsuarios<R> = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null) ResultadoUsuarios.ErrorDelServidor else ResultadoUsuarios.Exito(transformar(cuerpo))
        }

        respuesta.code() == 400 -> {
            val error = leerError(respuesta)
            ResultadoUsuarios.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
        }

        respuesta.code() == 401 -> ResultadoUsuarios.SesionInvalida

        respuesta.code() == 403 -> ResultadoUsuarios.SinPermiso(leerError(respuesta)?.mensaje)

        respuesta.code() == 404 -> ResultadoUsuarios.NoEncontrado

        respuesta.code() == 409 -> {
            val error = leerError(respuesta)
            ResultadoUsuarios.Conflicto(error?.mensaje, error?.campos.orEmpty())
        }

        else -> ResultadoUsuarios.ErrorDelServidor
    }

    /** Si el cuerpo falta o no es JSON válido devuelve null en vez de fallar. */
    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}

private fun UsuarioDto.aUsuario() = Usuario(id, nombre, correo, rol, activo)
