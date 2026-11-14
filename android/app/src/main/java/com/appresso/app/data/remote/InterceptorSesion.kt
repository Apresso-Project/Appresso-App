package com.appresso.app.data.remote

import com.appresso.app.data.auth.AlmacenSesion
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Pone el token de la sesión en cada petición y cierra la sesión cuando el backend la rechaza
 * (SCRUM-46, contrato en docs/api-auth.md y docs/permisos-por-rol.md).
 *
 * - Si hay sesión, agrega `Authorization: Bearer <token>`.
 * - Si el backend responde **401** (token vencido, alterado o cuenta desactivada), borra la sesión.
 *   Como `AppressoRaiz` observa el almacén, la app vuelve sola al login.
 * - Un **403** no cierra la sesión: el usuario sigue dentro y la pantalla muestra el mensaje.
 * - El login no lleva token, y su 401 ("Credenciales inválidas") no toca la sesión: lo maneja la
 *   pantalla de login.
 */
class InterceptorSesion(private val almacen: AlmacenSesion) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (esLogin(original)) return chain.proceed(original)

        val token = almacen.sesion.value?.token
        val solicitud = if (token == null) {
            original
        } else {
            original.newBuilder().header(ENCABEZADO, "Bearer $token").build()
        }

        val respuesta = chain.proceed(solicitud)

        // Solo se borra la sesión con la que se hizo la petición: si mientras tanto el usuario
        // volvió a iniciar sesión, el 401 de un token viejo no debe sacarlo.
        if (respuesta.code == 401 && token != null && almacen.sesion.value?.token == token) {
            almacen.borrar()
        }
        return respuesta
    }

    private fun esLogin(solicitud: Request) = solicitud.url.encodedPath.endsWith(RUTA_LOGIN)

    companion object {
        const val ENCABEZADO = "Authorization"
        const val RUTA_LOGIN = "/api/auth/login"
    }
}
