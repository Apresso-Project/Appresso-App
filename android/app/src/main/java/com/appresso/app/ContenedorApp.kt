package com.appresso.app

import android.content.Context
import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.AuthRepositoryRemoto
import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.CatalogoRepositoryRemoto
import com.appresso.app.data.remote.AuthApi
import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.BusEventosRed
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.UsuariosApi
import com.appresso.app.data.usuarios.UsuariosRepository
import com.appresso.app.data.usuarios.UsuariosRepositoryRemoto
import com.appresso.app.tiemporeal.ConexionTiempoReal
import com.appresso.app.tiemporeal.urlTiempoReal
import retrofit2.Retrofit

/**
 * Crea y reúne los objetos que comparte toda la app (el almacén de sesión y el acceso al backend).
 * Es una inyección de dependencias manual: suficiente por ahora y sin librerías extra.
 */
class ContenedorApp(private val urlBase: String) {

    val almacenSesion: AlmacenSesion = AlmacenSesionEnMemoria()

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

    val catalogoRepository: CatalogoRepository by lazy {
        CatalogoRepositoryRemoto(retrofit.create(CatalogoApi::class.java))
    }

    val usuariosRepository: UsuariosRepository by lazy {
        UsuariosRepositoryRemoto(retrofit.create(UsuariosApi::class.java))
    }

    private var tiempoReal: ConexionTiempoReal? = null

    /**
     * Una sola conexión de tiempo real para toda la app (docs/tiempo-real.md), con el token de la
     * sesión actual en cada CONNECT. Se crea la primera vez que una pantalla la pide; cada pantalla
     * la conecta mientras está visible y la detiene al salir.
     */
    @Synchronized
    fun tiempoReal(context: Context): ConexionTiempoReal =
        tiempoReal ?: ConexionTiempoReal(
            context.applicationContext,
            urlTiempoReal(urlBase),
            token = { almacenSesion.sesion.value?.token },
        ).also { tiempoReal = it }
}