package com.appresso.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import com.appresso.app.data.remote.EventoRed
import com.appresso.app.permisos.Opcion
import com.appresso.app.ui.inicio.InicioScreen
import com.appresso.app.ui.inicio.SeccionProvisionalScreen
import com.appresso.app.ui.login.LoginRoute
import com.appresso.app.ui.login.LoginViewModel

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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
                    Seccion(destino, onVolver = { abierta = null })
                }
            }
        }
    }
}

/**
 * La pantalla de cada sección. Hoy todas son provisionales; cada módulo reemplaza la suya, por
 * ejemplo: `Opcion.USUARIOS -> UsuariosRoute(...)`.
 */
@Composable
private fun Seccion(destino: Destino.Seccion, onVolver: () -> Unit) {
    when (destino.opcion) {
        else -> SeccionProvisionalScreen(opcion = destino.opcion, onVolver = onVolver)
    }
}