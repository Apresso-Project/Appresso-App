package com.appresso.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appresso.app.ContenedorApp
import com.appresso.app.ui.admin.AdminHomeScreen
import com.appresso.app.ui.login.LoginRoute
import com.appresso.app.ui.login.LoginViewModel
import com.appresso.app.ui.usuarios.UsuariosRoute
import com.appresso.app.ui.usuarios.UsuariosViewModel

/**
 * Raíz de la app: elige la pantalla según haya sesión iniciada o no (ver [destinoPara]).
 * Al iniciar sesión el almacén emite la sesión y la pantalla cambia sola, sin una librería de
 * navegación; cuando haya más pantallas se podrá reemplazar por una.
 */
@Composable
fun AppressoRaiz(contenedor: ContenedorApp) {
    val sesion by contenedor.almacenSesion.sesion.collectAsStateWithLifecycle()

    when (val destino = destinoPara(sesion)) {
        Destino.Login -> {
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(contenedor.authRepository, contenedor.almacenSesion),
            )
            LoginRoute(viewModel)
        }

        is Destino.InicioAdmin -> {
            // Navegación mínima sin librería: se reinicia sola al salir de este destino (cerrar sesión).
            var enUsuarios by rememberSaveable { mutableStateOf(false) }
            if (enUsuarios) {
                // La clave evita que otra cuenta que inicie sesión después herede la lista anterior.
                val viewModel: UsuariosViewModel = viewModel(
                    key = "usuarios-${sesion?.token}",
                    factory = UsuariosViewModel.factory(contenedor.usuariosRepository, contenedor.almacenSesion),
                )
                UsuariosRoute(viewModel = viewModel, onVolver = { enUsuarios = false })
            } else {
                AdminHomeScreen(nombre = destino.nombre, onAbrirUsuarios = { enUsuarios = true })
            }
        }

        is Destino.SinPantalla -> SinPantallaScreen(rol = destino.rol)
    }
}
