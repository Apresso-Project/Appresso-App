package com.appresso.app

import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.AuthRepositoryRemoto
import com.appresso.app.data.remote.AuthApi
import com.appresso.app.data.remote.ClienteHttp
import retrofit2.Retrofit

/**
 * Crea y reúne los objetos que comparte toda la app (el almacén de sesión y el acceso al backend).
 * Es una inyección de dependencias manual: suficiente por ahora y sin librerías extra.
 */
class ContenedorApp(urlBase: String) {

    val almacenSesion: AlmacenSesion = AlmacenSesionEnMemoria()

    /**
     * Un solo Retrofit para toda la app: cada petición lleva el token de [almacenSesion] y un 401
     * cierra la sesión (SCRUM-46). Las APIs nuevas se crean con `retrofit.create(MiApi::class.java)`.
     */
    val retrofit: Retrofit by lazy { ClienteHttp.crearRetrofit(urlBase, almacenSesion) }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryRemoto(retrofit.create(AuthApi::class.java))
    }
}
