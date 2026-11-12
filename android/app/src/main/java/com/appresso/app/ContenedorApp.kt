package com.appresso.app

import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.AuthRepositoryRemoto
import com.appresso.app.data.remote.ClienteHttp

/**
 * Crea y reúne los objetos que comparte toda la app (el almacén de sesión y el acceso al backend).
 * Es una inyección de dependencias manual: suficiente por ahora y sin librerías extra.
 */
class ContenedorApp(urlBase: String) {

    val almacenSesion: AlmacenSesion = AlmacenSesionEnMemoria()

    val authRepository: AuthRepository by lazy {
        AuthRepositoryRemoto(ClienteHttp.crearAuthApi(urlBase))
    }
}
