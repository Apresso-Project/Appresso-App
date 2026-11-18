package com.appresso.app

import android.content.Context
import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.AlmacenSesionKeystore
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.AuthRepositoryRemoto
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ClientesRepositoryRemoto
import com.appresso.app.data.remote.AuthApi
import com.appresso.app.data.remote.BusEventosRed
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import com.appresso.app.data.remote.UsuariosApi
import com.appresso.app.data.usuarios.UsuariosRepository
import com.appresso.app.data.usuarios.UsuariosRepositoryRemoto
import retrofit2.Retrofit

/**
 * Crea y reúne los objetos que comparte toda la app (el almacén de sesión y el acceso al backend).
 * Es una inyección de dependencias manual: suficiente por ahora y sin librerías extra.
 *
 * El token y la sesión se guardan de forma segura y cifrada en Android Keystore mediante
 * [AlmacenSesionKeystore], garantizando que nunca se persistan en texto plano.
 */
class ContenedorApp(urlBase: String, context: Context? = null) {

    val almacenSesion: AlmacenSesion =
        if (context != null) AlmacenSesionKeystore.crear(context) else AlmacenSesionEnMemoria()

    /** Canal por el que el cliente HTTP avisa de los 403 y de la pérdida de conexión. */
    val busEventosRed = BusEventosRed()

    /**
     * Un solo Retrofit para toda la app: cada petición lleva el token de [almacenSesion] y un 401
     * cierra la sesión (SCRUM-46). Las APIs nuevas se crean con `retrofit.create(MiApi::class.java)`.
     */
    val retrofit: Retrofit by lazy {
        ClienteHttp.crearRetrofit(urlBase, almacenSesion, bus = busEventosRed)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryRemoto(retrofit.create(AuthApi::class.java))
    }

    val usuariosRepository: UsuariosRepository by lazy {
        UsuariosRepositoryRemoto(retrofit.create(UsuariosApi::class.java))
    }

    val clientesRepository: ClientesRepository by lazy {
        ClientesRepositoryRemoto(retrofit.create(ClientesApi::class.java))
    }
}