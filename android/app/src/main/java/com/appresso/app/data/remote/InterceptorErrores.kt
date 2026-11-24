package com.appresso.app.data.remote

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Avisa de los 403 y de la pérdida de conexión en los endpoints protegidos. Las llamadas
 * canceladas por la app no cuentan como pérdida de conexión.
 * Las rutas públicas de la cuenta (`/api/auth/`: login, registro y recuperación) se omiten: ahí
 * cada pantalla informa el caso por su cuenta.
 */
class InterceptorErrores(private val bus: BusEventosRed) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val solicitud = chain.request()
        val esLogin = solicitud.url.encodedPath.contains(RUTAS_PUBLICAS)

        val respuesta = try {
            chain.proceed(solicitud)
        } catch (e: IOException) {
            // Una llamada cancelada (la pantalla se cerró, la corrutina terminó) también lanza
            // IOException, pero no es una falla de red: avisarlo sería un falso «Sin conexión».
            if (!esLogin && !chain.call().isCanceled()) bus.emitir(EventoRed.SIN_CONEXION)
            throw e // el repositorio lo captura y lo convierte en un resultado
        }

        if (!esLogin && respuesta.code == 403) bus.emitir(EventoRed.PROHIBIDO)
        return respuesta
    }

    private companion object {
        const val RUTAS_PUBLICAS = "/api/auth/"
    }
}
