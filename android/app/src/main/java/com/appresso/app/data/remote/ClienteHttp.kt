package com.appresso.app.data.remote

import com.appresso.app.data.auth.AlmacenSesion
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Construye el cliente HTTP y las APIs del backend. */
object ClienteHttp {

    const val TIEMPO_LIMITE_SEGUNDOS = 15L

    /** `ignoreUnknownKeys`: si el backend agrega un campo, las versiones viejas de la app no se rompen. */
    val json = Json { ignoreUnknownKeys = true }

    /**
     * @param tiempoLimiteSegundos tiempo máximo de conexión, de envío y de espera de respuesta
     * @param almacenSesion si se pasa, cada petición lleva el token de la sesión y un 401 la cierra
     *   (ver [InterceptorSesion])
     * @param bus si se pasa, los 403 y la pérdida de conexión de los endpoints protegidos se avisan
     *   por ahí (ver [InterceptorErrores])
     */
    fun crearCliente(
        tiempoLimiteSegundos: Long = TIEMPO_LIMITE_SEGUNDOS,
        almacenSesion: AlmacenSesion? = null,
        bus: BusEventosRed? = null,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            .readTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            .writeTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            // Con esto activado, OkHttp puede reenviar la solicitud por otra conexión si la primera
            // falla (p. ej. una conexión inactiva que el servidor cerró). En un login eso reenviaría
            // la contraseña sin que el usuario lo sepa, y cada envío fallido cuenta como un intento
            // para el bloqueo de la cuenta.
            .retryOnConnectionFailure(false)
            .apply {
                if (almacenSesion != null) addInterceptor(InterceptorSesion(almacenSesion))
                if (bus != null) addInterceptor(InterceptorErrores(bus))
            }
            .build()

    /**
     * Retrofit configurado para el backend. Todas las APIs (login, usuarios, comandas...) se crean
     * desde aquí con `.create(MiApi::class.java)`, así comparten el cliente y llevan el token.
     *
     * @param urlBase debe terminar en "/" (en la app viene de `BuildConfig.API_BASE_URL`)
     */
    fun crearRetrofit(
        urlBase: String,
        almacenSesion: AlmacenSesion? = null,
        tiempoLimiteSegundos: Long = TIEMPO_LIMITE_SEGUNDOS,
        bus: BusEventosRed? = null,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(urlBase)
            .client(crearCliente(tiempoLimiteSegundos, almacenSesion, bus))
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

    /**
     * @param urlBase debe terminar en "/" (en la app viene de `BuildConfig.API_BASE_URL`)
     * @param tiempoLimiteSegundos tiempo máximo de conexión y de espera de respuesta
     */
    fun crearAuthApi(urlBase: String, tiempoLimiteSegundos: Long = TIEMPO_LIMITE_SEGUNDOS): AuthApi =
        crearRetrofit(urlBase, tiempoLimiteSegundos = tiempoLimiteSegundos).create(AuthApi::class.java)
}