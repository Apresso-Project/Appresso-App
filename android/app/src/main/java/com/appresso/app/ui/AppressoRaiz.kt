package com.appresso.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appresso.app.ContenedorApp
import com.appresso.app.ui.admin.AdminHomeScreen
import com.appresso.app.ui.login.LoginRoute
import com.appresso.app.ui.login.LoginViewModel

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

        is Destino.InicioAdmin -> AdminHomeScreen(nombre = destino.nombre)

        is Destino.SinPantalla -> SinPantallaScreen(rol = destino.rol)
    }
}
