package com.appresso.app.data.remote

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
     */
    fun crearCliente(tiempoLimiteSegundos: Long = TIEMPO_LIMITE_SEGUNDOS): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            .readTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            .writeTimeout(tiempoLimiteSegundos, TimeUnit.SECONDS)
            // Con esto activado, OkHttp puede reenviar la solicitud por otra conexión si la primera
            // falla (p. ej. una conexión inactiva que el servidor cerró). En un login eso reenviaría
            // la contraseña sin que el usuario lo sepa, y cada envío fallido cuenta como un intento
            // para el bloqueo de la cuenta.
            .retryOnConnectionFailure(false)
            .build()

    /**
     * @param urlBase debe terminar en "/" (en la app viene de `BuildConfig.API_BASE_URL`)
     * @param tiempoLimiteSegundos tiempo máximo de conexión y de espera de respuesta
     */
    fun crearAuthApi(urlBase: String, tiempoLimiteSegundos: Long = TIEMPO_LIMITE_SEGUNDOS): AuthApi {
        return Retrofit.Builder()
            .baseUrl(urlBase)
            .client(crearCliente(tiempoLimiteSegundos))
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }
}
