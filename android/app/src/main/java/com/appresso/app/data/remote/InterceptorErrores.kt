package com.appresso.app.data.remote

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Avisa de los 403 y de la pérdida de conexión en los endpoints protegidos.
 * El login se omite: ahí ResultadoLogin ya informa cada caso en la propia pantalla.
 */
class InterceptorErrores(private val bus: BusEventosRed) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val solicitud = chain.request()
        val esLogin = solicitud.url.encodedPath.endsWith(RUTA_LOGIN)

        val respuesta = try {
            chain.proceed(solicitud)
        } catch (e: IOException) {
            if (!esLogin) bus.emitir(EventoRed.SIN_CONEXION)
            throw e // el repositorio lo captura y lo convierte en un resultado
        }

        if (!esLogin && respuesta.code == 403) bus.emitir(EventoRed.PROHIBIDO)
        return respuesta
    }

    private companion object {
        const val RUTA_LOGIN = "api/auth/login"
    }
}
