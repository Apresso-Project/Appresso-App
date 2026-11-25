package com.appresso.app

import android.content.Context
import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.AuthRepository
import com.appresso.app.data.auth.AuthRepositoryRemoto
import com.appresso.app.data.clientes.ClientesRepository
import com.appresso.app.data.clientes.ClientesRepositoryRemoto
import com.appresso.app.data.cuenta.CuentaClienteRepository
import com.appresso.app.data.cuenta.CuentaClienteRepositoryRemoto
import com.appresso.app.data.cuenta.MisDatosRepository
import com.appresso.app.data.cuenta.MisDatosRepositoryRemoto
import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.CatalogoRepositoryRemoto
import com.appresso.app.data.cocina.CocinaRepository
import com.appresso.app.data.cocina.CocinaRepositoryRemoto
import com.appresso.app.data.menu.MenuRepository
import com.appresso.app.data.menu.MenuRepositoryRemoto
import com.appresso.app.data.remote.CocinaApi
import com.appresso.app.data.remote.MenuApi
import com.appresso.app.data.catalogo.InventarioRepository
import com.appresso.app.data.remote.AuthApi
import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.BusEventosRed
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ClientesApi
import com.appresso.app.data.remote.CuentaApi
import com.appresso.app.data.remote.MisDatosApi
import com.appresso.app.data.remote.UsuariosApi
import com.appresso.app.data.usuarios.UsuariosRepository
import com.appresso.app.data.usuarios.UsuariosRepositoryRemoto
import com.appresso.app.tiemporeal.ConexionTiempoReal
import com.appresso.app.tiemporeal.Topicos
import com.appresso.app.tiemporeal.urlTiempoReal
import retrofit2.Retrofit

/**
 * Crea y reúne los objetos que comparte toda la app (el almacén de sesión y el acceso al backend).
 * Es una inyección de dependencias manual: suficiente por ahora y sin librerías extra.
 */
class ContenedorApp(
    private val urlBase: String,
    /** En la app, `AlmacenSesionKeystore` (sesión cifrada, 8.4.1). */
    val almacenSesion: AlmacenSesion,
) {

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

    /** Registro y recuperación de contraseña del cliente: rutas públicas, sin token. */
    val cuentaClienteRepository: CuentaClienteRepository by lazy {
        CuentaClienteRepositoryRemoto(retrofit.create(CuentaApi::class.java))
    }

    private val catalogoRemoto by lazy {
        CatalogoRepositoryRemoto(retrofit.create(CatalogoApi::class.java))
    }

    /** Menú de solo lectura para mesero y cliente (3.3.1). */
    val menuRepository: MenuRepository by lazy {
        MenuRepositoryRemoto(retrofit.create(MenuApi::class.java))
    }

    /** Comandas activas para la pantalla de cocina (3.3.1). */
    val cocinaRepository: CocinaRepository by lazy {
        CocinaRepositoryRemoto(retrofit.create(CocinaApi::class.java))
    }
    val catalogoRepository: CatalogoRepository get() = catalogoRemoto
    val inventarioRepository: InventarioRepository get() = catalogoRemoto

    val usuariosRepository: UsuariosRepository by lazy {
        UsuariosRepositoryRemoto(retrofit.create(UsuariosApi::class.java))
    }

    val clientesRepository: ClientesRepository by lazy {
        ClientesRepositoryRemoto(retrofit.create(ClientesApi::class.java))
    }

    /** Eliminación de los datos del cliente que inició sesión (Ley 1581). */
    val misDatosRepository: MisDatosRepository by lazy {
        MisDatosRepositoryRemoto(retrofit.create(MisDatosApi::class.java))
    }

    private val conexionesTiempoReal = mutableMapOf<String, ConexionTiempoReal>()

    /**
     * Reutiliza la conexión del tópico que necesita cada pantalla. Cada CONNECT toma el token de
     * la sesión actual. El menú y el inventario solo requieren eventos del catálogo.
     */
    @Synchronized
    fun tiempoReal(context: Context, topico: String = Topicos.CATALOGO): ConexionTiempoReal =
        conexionesTiempoReal.getOrPut(topico) {
            ConexionTiempoReal(
                context.applicationContext,
                urlTiempoReal(urlBase),
                topicos = setOf(topico),
                token = { almacenSesion.sesion.value?.token },
            )
        }
}
