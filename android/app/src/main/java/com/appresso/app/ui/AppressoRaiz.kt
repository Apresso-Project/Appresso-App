package com.appresso.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appresso.app.ContenedorApp
import com.appresso.app.R
import com.appresso.app.data.auth.TokenSesion
import com.appresso.app.data.remote.EventoRed
import com.appresso.app.permisos.Accion
import com.appresso.app.permisos.Opcion
import com.appresso.app.permisos.puede
import com.appresso.app.ui.catalogo.CategoriasRoute
import com.appresso.app.ui.catalogo.CategoriasViewModel
import com.appresso.app.ui.catalogo.ProductosRoute
import com.appresso.app.ui.catalogo.ProductosViewModel
import com.appresso.app.ui.inicio.InicioScreen
import com.appresso.app.ui.inicio.SeccionProvisionalScreen
import com.appresso.app.ui.login.LoginRoute
import com.appresso.app.ui.login.LoginViewModel
import com.appresso.app.ui.usuarios.UsuariosRoute
import com.appresso.app.ui.usuarios.UsuariosViewModel

/**
 * Raíz de la app: elige la pantalla según la sesión y la sección abierta (ver [destinoPara]).
 *
 * Al iniciar sesión el almacén emite la sesión y la pantalla cambia sola, sin una librería de
 * navegación. Lo mismo al cerrarla: con el botón "Cerrar sesión" o cuando el backend responde 401
 * (InterceptorSesion), la sesión se borra y se vuelve al login.
 *
 * También muestra, sobre cualquier pantalla, los avisos de pérdida de conexión y de acceso
 * denegado (403) que publica el cliente HTTP.
 *
 * Para conectar la pantalla real de un módulo, reemplaza su caso en [Seccion] (abajo).
 */
@Composable
fun AppressoRaiz(contenedor: ContenedorApp) {
    val sesion by contenedor.almacenSesion.sesion.collectAsStateWithLifecycle()
    // La sección abierta se conserva al girar la pantalla, pero una sesión nueva (otro usuario u
    // otro rol) siempre empieza en su inicio: por eso depende del token.
    var abierta by rememberSaveable(sesion?.token) { mutableStateOf<Opcion?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val textoSinConexion = stringResource(R.string.login_error_no_connection)
    val textoProhibido = stringResource(R.string.error_prohibido)

    LaunchedEffect(contenedor) {
        contenedor.busEventosRed.eventos.collect { evento ->
            snackbar.showSnackbar(
                when (evento) {
                    EventoRed.SIN_CONEXION -> textoSinConexion
                    EventoRed.PROHIBIDO -> textoProhibido
                },
            )
        }
    }

    // contentWindowInsets en cero: cada pantalla ya deja su espacio para la barra de estado
    // (statusBarsPadding) y pinta su cabecera Espresso detrás de ella. Con los márgenes por defecto
    // del Scaffold, ese espacio se sumaba dos veces y aparecía una franja clara arriba.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val destino = destinoPara(sesion, abierta)) {
                Destino.Login -> {
                    val viewModel: LoginViewModel = viewModel(
                        factory = LoginViewModel.factory(contenedor.authRepository, contenedor.almacenSesion),
                    )
                    LoginRoute(viewModel)
                }

                is Destino.Inicio -> InicioScreen(
                    nombre = destino.nombre,
                    rol = destino.rol,
                    opciones = destino.opciones,
                    onAbrir = { abierta = it },
                    onCerrarSesion = { contenedor.almacenSesion.borrar() },
                )

                is Destino.Seccion -> {
                    BackHandler { abierta = null }
                    Seccion(destino, contenedor, token = sesion?.token, onVolver = { abierta = null })
                }
            }
        }
    }
}

/**
 * La pantalla de cada sección. Las que aún no tienen módulo son provisionales; cada módulo reemplaza
 * la suya con su caso.
 */
@Composable
private fun Seccion(destino: Destino.Seccion, contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    when (destino.opcion) {
        // Solo el administrador gestiona el catálogo; el menú de solo lectura del mesero (3.3.1) aún
        // no existe y queda en la pantalla provisional.
        Opcion.PRODUCTOS_E_INVENTARIO -> if (puede(destino.rol, Accion.GESTIONAR_CATALOGO)) {
            Catalogo(contenedor, token, onVolver)
        } else {
            SeccionProvisionalScreen(opcion = destino.opcion, onVolver = onVolver)
        }

        Opcion.USUARIOS -> {
            // La clave evita que otra cuenta que inicie sesión después herede la lista anterior.
            val viewModel: UsuariosViewModel = viewModel(
                key = "usuarios-$token",
                factory = UsuariosViewModel.factory(contenedor.usuariosRepository),
            )
            UsuariosRoute(
                viewModel = viewModel,
                onVolver = onVolver,
                miId = token?.let(TokenSesion::idDeUsuario),
            )
        }

        else -> SeccionProvisionalScreen(opcion = destino.opcion, onVolver = onVolver)
    }
}

/**
 * Gestión del catálogo del administrador (3.1.1): categorías y productos, con un botón para pasar de
 * una a otra. Cada visita crea ViewModels nuevos (la clave cambia) para que los conteos de productos
 * y la lista se actualicen al volver.
 */
@Composable
private fun Catalogo(contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    var enProductos by rememberSaveable(token) { mutableStateOf(false) }
    var visita by rememberSaveable(token) { mutableStateOf(0) }
    if (enProductos) {
        BackHandler {
            visita++
            enProductos = false
        }
        val viewModel: ProductosViewModel = viewModel(
            key = "productos-$token-$visita",
            factory = ProductosViewModel.factory(contenedor.catalogoRepository),
        )
        ProductosRoute(
            viewModel = viewModel,
            onVerCategorias = {
                visita++
                enProductos = false
            },
        )
    } else {
        val viewModel: CategoriasViewModel = viewModel(
            key = "categorias-$token-$visita",
            factory = CategoriasViewModel.factory(contenedor.catalogoRepository),
        )
        CategoriasRoute(viewModel = viewModel, onVerProductos = { enProductos = true }, onVolver = onVolver)
    }
}
