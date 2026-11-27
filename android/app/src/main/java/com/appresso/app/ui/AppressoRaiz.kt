package com.appresso.app.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appresso.app.ContenedorApp
import com.appresso.app.R
import com.appresso.app.data.auth.TokenSesion
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.remote.EventoRed
import com.appresso.app.permisos.Accion
import com.appresso.app.permisos.Opcion
import com.appresso.app.ui.clientes.ClientesRoute
import com.appresso.app.ui.comandas.ComandaRoute
import com.appresso.app.ui.comandas.ComandaViewModel
import com.appresso.app.ui.comandas.MesasRoute
import com.appresso.app.ui.comandas.MesasViewModel
import com.appresso.app.ui.comandas.PasoComanda
import com.appresso.app.data.auth.Rol
import com.appresso.app.ui.cocina.CocinaRoute
import com.appresso.app.ui.cocina.CocinaViewModel
import com.appresso.app.ui.clientes.ClientesViewModel
import com.appresso.app.ui.cuenta.MisDatosRoute
import com.appresso.app.ui.cuenta.MisDatosViewModel
import com.appresso.app.ui.cuenta.RecuperarContrasenaRoute
import com.appresso.app.ui.cuenta.RecuperarContrasenaViewModel
import com.appresso.app.ui.cuenta.RegistroClienteRoute
import com.appresso.app.ui.cuenta.RegistroClienteViewModel
import com.appresso.app.permisos.puede
import com.appresso.app.ui.catalogo.CategoriasRoute
import com.appresso.app.ui.catalogo.CategoriasViewModel
import com.appresso.app.ui.catalogo.HistorialInventarioRoute
import com.appresso.app.ui.catalogo.InventarioViewModel
import com.appresso.app.ui.catalogo.ProductosRoute
import com.appresso.app.ui.catalogo.ProductosViewModel
import com.appresso.app.ui.inicio.InicioScreen
import com.appresso.app.ui.inicio.SeccionProvisionalScreen
import com.appresso.app.ui.login.LoginRoute
import com.appresso.app.ui.login.LoginViewModel
import com.appresso.app.ui.menu.MenuRoute
import com.appresso.app.ui.menu.MenuViewModel
import com.appresso.app.ui.usuarios.UsuariosRoute
import com.appresso.app.ui.usuarios.UsuariosViewModel
import com.appresso.app.tiemporeal.MedidorCocina
import com.appresso.app.tiemporeal.Topicos
import com.appresso.app.tiemporeal.cambiosDeCatalogo
import com.appresso.app.tiemporeal.EstadoConexion
import com.appresso.app.tiemporeal.cambiosDeCocina
import com.appresso.app.tiemporeal.comoResincronizacion
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import java.util.UUID

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
 * Sin sesión, además del login están las pantallas públicas del cliente: crear su cuenta y
 * recuperar su contraseña ([PantallaPublica]).
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
                Destino.Login -> PantallasPublicas(contenedor)

                is Destino.Inicio -> InicioScreen(
                    nombre = destino.nombre,
                    rol = destino.rol,
                    opciones = destino.opciones,
                    onAbrir = { abierta = it },
                    onCerrarSesion = { contenedor.almacenSesion.borrar() },
                    productosConStockBajo = if (puede(destino.rol, Accion.GESTIONAR_INVENTARIO)) {
                        observarStockBajo(contenedor, sesion?.token)
                    } else emptyList(),
                )

                is Destino.Seccion -> {
                    BackHandler { abierta = null }
                    Seccion(destino, contenedor, token = sesion?.token, onVolver = { abierta = null })
                }
            }
        }
    }
}

@Composable
private fun observarStockBajo(contenedor: ContenedorApp, token: String?): List<Producto> {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val tiempoReal = remember(contenedor) { contenedor.tiempoReal(contexto) }
    var productos by remember(token) { mutableStateOf(emptyList<Producto>()) }
    DisposableEffect(tiempoReal, ciclo, token) {
        ciclo.addObserver(tiempoReal)
        onDispose {
            ciclo.removeObserver(tiempoReal)
            tiempoReal.detener()
        }
    }

    LaunchedEffect(tiempoReal, token) {
        merge(
            tiempoReal.eventos.cambiosDeCatalogo(),
            tiempoReal.resincronizar.map { },
            tiempoReal.estado.filter { it.estado == EstadoConexion.CONECTADO }.map { },
        ).onStart { emit(Unit) }.conflate().collect {
            val resultado = contenedor.inventarioRepository.alertas()
            if (resultado is ResultadoCatalogo.Exito) productos = resultado.valor
        }
    }
    return productos
}

/** Las pantallas a las que se llega sin sesión. */
private enum class PantallaPublica { LOGIN, REGISTRO, RECUPERAR }

/**
 * El login y las pantallas públicas del cliente. Al terminar el registro o la recuperación se vuelve
 * al login con el documento ya escrito. Cada visita al registro o a la recuperación empieza de cero.
 */
@Composable
private fun PantallasPublicas(contenedor: ContenedorApp) {
    var pantalla by rememberSaveable { mutableStateOf(PantallaPublica.LOGIN) }
    // Cambia en cada visita, para que el formulario anterior no reaparezca al volver a entrar.
    var visita by rememberSaveable { mutableIntStateOf(0) }
    val login: LoginViewModel = viewModel(
        factory = LoginViewModel.factory(contenedor.authRepository, contenedor.almacenSesion),
    )
    val alLogin: (String?) -> Unit = { documento ->
        if (!documento.isNullOrBlank()) login.onIdentificadorChange(documento)
        pantalla = PantallaPublica.LOGIN
    }
    val abrir: (PantallaPublica) -> Unit = {
        visita++
        pantalla = it
    }

    when (pantalla) {
        PantallaPublica.LOGIN -> LoginRoute(
            viewModel = login,
            onCrearCuenta = { abrir(PantallaPublica.REGISTRO) },
            onOlvideContrasena = { abrir(PantallaPublica.RECUPERAR) },
        )

        PantallaPublica.REGISTRO -> {
            BackHandler { alLogin(null) }
            val viewModel: RegistroClienteViewModel = viewModel(
                key = "registro-$visita",
                factory = RegistroClienteViewModel.factory(contenedor.cuentaClienteRepository),
            )
            RegistroClienteRoute(viewModel = viewModel, onVolver = { alLogin(null) }, onIrAlLogin = alLogin)
        }

        PantallaPublica.RECUPERAR -> {
            BackHandler { alLogin(null) }
            val viewModel: RecuperarContrasenaViewModel = viewModel(
                key = "recuperar-$visita",
                factory = RecuperarContrasenaViewModel.factory(contenedor.cuentaClienteRepository),
            )
            RecuperarContrasenaRoute(viewModel = viewModel, onVolver = { alLogin(null) }, onIrAlLogin = alLogin)
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
        // Solo el administrador gestiona el catálogo; el mesero y el cliente ven el menú de solo
        // lectura desde su propia opción (Opcion.MENU).
        Opcion.PRODUCTOS_E_INVENTARIO -> if (puede(destino.rol, Accion.GESTIONAR_CATALOGO)) {
            Catalogo(contenedor, token, onVolver)
        } else {
            SeccionProvisionalScreen(opcion = destino.opcion, onVolver = onVolver)
        }

        Opcion.MENU -> MenuSeccion(contenedor, token, onVolver)

        Opcion.COCINA -> CocinaSeccion(contenedor, token, onVolver)

        Opcion.MESAS_Y_COMANDAS -> MesasYComandasSeccion(destino.rol, contenedor, token, onVolver)

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

        Opcion.CLIENTES -> {
            val viewModel: ClientesViewModel = viewModel(
                key = "clientes-$token",
                factory = ClientesViewModel.factory(contenedor.clientesRepository),
            )
            ClientesRoute(
                viewModel = viewModel,
                onVolver = onVolver,
            )
        }

        Opcion.MIS_DATOS -> {
            val viewModel: MisDatosViewModel = viewModel(
                key = "mis-datos-$token",
                factory = MisDatosViewModel.factory(contenedor.misDatosRepository),
            )
            MisDatosRoute(
                viewModel = viewModel,
                onVolver = onVolver,
                onCerrarSesion = { contenedor.almacenSesion.borrar() },
            )
        }

        else -> SeccionProvisionalScreen(opcion = destino.opcion, onVolver = onVolver)
    }
}

/**
 * Gestión del catálogo del administrador (3.1.1): categorías y productos, con un botón para pasar de
 * una a otra. Cada visita crea ViewModels nuevos (la clave cambia) para que los conteos de productos
 * y la lista se actualicen al volver.
 *
 * Tiempo real: mientras la sección está abierta y la app en primer plano, la conexión STOMP escucha
 * `/topic/catalogo`; cada cambio hecho en otro dispositivo (o una reconexión, que pudo perder eventos)
 * recarga en silencio la lista visible. Al salir de la sección la conexión se detiene.
 */
@Composable
private fun Catalogo(contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    var enProductos by rememberSaveable(token) { mutableStateOf(false) }
    var enHistorial by rememberSaveable(token) { mutableStateOf(false) }
    var visita by rememberSaveable(token) { mutableIntStateOf(0) }
    val inventario: InventarioViewModel = viewModel(
        key = "inventario-$token",
        factory = InventarioViewModel.factory(contenedor.inventarioRepository),
    )

    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val tiempoReal = remember(contenedor) { contenedor.tiempoReal(contexto) }
    DisposableEffect(tiempoReal, ciclo) {
        ciclo.addObserver(tiempoReal) // conecta ya si la app está en primer plano
        onDispose {
            ciclo.removeObserver(tiempoReal)
            tiempoReal.detener()
        }
    }
    val cambios = remember(tiempoReal) {
        merge(
            tiempoReal.eventos.cambiosDeCatalogo(),
            tiempoReal.resincronizar.map { },
            tiempoReal.estado.filter { it.estado == EstadoConexion.CONECTADO }.map { },
        )
    }

    if (enHistorial) {
        BackHandler { enHistorial = false }
        HistorialInventarioRoute(inventario, onVolver = { enHistorial = false })
    } else if (enProductos) {
        BackHandler {
            visita++
            enProductos = false
        }
        val viewModel: ProductosViewModel = viewModel(
            key = "productos-$token-$visita",
            factory = ProductosViewModel.factory(contenedor.catalogoRepository),
        )
        LaunchedEffect(viewModel, cambios) { cambios.onStart { emit(Unit) }.collect { viewModel.recargarEnSilencio() } }
        ProductosRoute(
            viewModel = viewModel,
            inventario = inventario,
            onVerHistorial = { enHistorial = true },
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
        LaunchedEffect(viewModel, cambios) { cambios.collect { viewModel.recargarEnSilencio() } }
        CategoriasRoute(viewModel = viewModel, onVerProductos = { enProductos = true }, onVolver = onVolver)
    }
}

/**
 * Menú de solo lectura (3.3.1) del administrador, el mesero y el cliente. Con la sección abierta y la
 * app en primer plano escucha `/topic/catalogo`: un cambio de precio, de stock o de disponibilidad en
 * otro dispositivo (o una reconexión, que pudo perder eventos) vuelve a pedir el menú en silencio.
 */
@Composable
private fun MenuSeccion(contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val tiempoReal = remember(contenedor) { contenedor.tiempoReal(contexto) }
    DisposableEffect(tiempoReal, ciclo) {
        ciclo.addObserver(tiempoReal)
        onDispose {
            ciclo.removeObserver(tiempoReal)
            tiempoReal.detener()
        }
    }
    val cambios = remember(tiempoReal) {
        // CONECTADO solo se publica después del RECEIPT. Releer cubre posibles cambios previos.
        merge(
            tiempoReal.eventos.cambiosDeCatalogo(),
            tiempoReal.resincronizar.map { },
            tiempoReal.estado.filter { it.estado == EstadoConexion.CONECTADO }.map { },
        )
    }
    // La clave evita que otra cuenta, o una visita posterior, herede el menú anterior.
    val visita = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: MenuViewModel = viewModel(
        key = "menu-$token-$visita",
        factory = MenuViewModel.factory(contenedor.menuRepository),
    )
    LifecycleStartEffect(viewModel) {
        viewModel.reanudarActualizaciones()
        onStopOrDispose { viewModel.pausarActualizaciones() }
    }
    LaunchedEffect(viewModel, cambios) { cambios.collect { viewModel.recargarEnSilencio() } }
    MenuRoute(viewModel = viewModel, onVolver = onVolver)
}

/**
 * Pantalla de cocina (3.3.1, RF-26): las comandas activas, que se actualizan solas. Mientras la
 * sección está abierta y la app en primer plano, la conexión STOMP escucha `/topic/comandas`; cada
 * cambio de estado o producto agregado vuelve a pedir la lista, y una reconexión (que pudo perder
 * eventos) también. El indicador de la pantalla avisa cuando no hay conexión en vivo (RNF-21).
 */
@Composable
private fun CocinaSeccion(contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val tiempoReal = remember(contenedor) { contenedor.tiempoReal(contexto, Topicos.COMANDAS) }
    DisposableEffect(tiempoReal, ciclo) {
        ciclo.addObserver(tiempoReal)
        onDispose {
            ciclo.removeObserver(tiempoReal)
            tiempoReal.detener()
        }
    }
    val cambios = remember(tiempoReal) {
        // CONECTADO solo se publica después del RECEIPT. Releer cubre posibles cambios previos.
        merge(
            tiempoReal.eventos.cambiosDeCocina(),
            tiempoReal.resincronizar.comoResincronizacion(),
            tiempoReal.estado.filter { it.estado == EstadoConexion.CONECTADO }.comoResincronizacion(),
        )
    }
    // Mide cada cambio (SCRUM-93) y escribe una línea por muestra en logcat: `adb logcat -s Cocina`.
    val medidor = remember { MedidorCocina(registro = { Log.i("Cocina", it) }) }
    // La clave evita que otra cuenta, o una visita posterior, herede la lista anterior.
    val visita = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: CocinaViewModel = viewModel(
        key = "cocina-$token-$visita",
        factory = CocinaViewModel.factory(contenedor.cocinaRepository, medidor),
    )
    LifecycleStartEffect(viewModel) {
        viewModel.reanudarActualizaciones()
        onStopOrDispose { viewModel.pausarActualizaciones() }
    }
    LaunchedEffect(viewModel, cambios) { cambios.collect { viewModel.alCambiar(it) } }
    CocinaRoute(viewModel = viewModel, conexion = tiempoReal.estado, onVolver = onVolver)
}

/**
 * Mesas y comandas (4.1.1): el mapa de mesas y, al tocar una mesa disponible, la pantalla para armar su
 * comanda. Si la mesa está ocupada, se abre la comanda activa del mapa. El administrador también
 * crea mesas aquí. Al volver se vuelve a consultar el mapa.
 */
@Composable
private fun MesasYComandasSeccion(rol: Rol, contenedor: ContenedorApp, token: String?, onVolver: () -> Unit) {
    var mesaId by rememberSaveable(token) { mutableStateOf<Long?>(null) }
    var mesaNombre by rememberSaveable(token) { mutableStateOf("") }
    var comandaActivaId by rememberSaveable(token) { mutableStateOf<Long?>(null) }
    var visita by rememberSaveable(token) { mutableIntStateOf(0) }
    val mesas: MesasViewModel = viewModel(
        key = "mesas-$token",
        factory = MesasViewModel.factory(contenedor.comandasRepository),
    )

    val abierta = mesaId
    if (abierta == null) {
        MesasRoute(
            viewModel = mesas,
            puedeCrearMesa = puede(rol, Accion.CREAR_MESA),
            puedeCrearComanda = puede(rol, Accion.CREAR_COMANDA),
            onAbrirMesa = {
                visita++
                mesaNombre = it.nombre
                comandaActivaId = it.comandaActivaId
                mesaId = it.id
            },
            onVolver = onVolver,
        )
    } else {
        val comanda: ComandaViewModel = viewModel(
            key = "comanda-$token-$abierta-$visita",
            factory = ComandaViewModel.factory(
                abierta, mesaNombre, contenedor.menuRepository, contenedor.comandasRepository, comandaActivaId,
            ),
        )
        val alMapa = {
            mesaId = null
            comandaActivaId = null
            mesas.recargarEnSilencio()
        }
        val estado by comanda.estado.collectAsStateWithLifecycle()
        LaunchedEffect(estado.guardada?.id) {
            estado.guardada?.let { comandaActivaId = it.id }
        }
        BackHandler {
            if (!estado.guardando) {
                if (estado.paso == PasoComanda.RESUMEN) comanda.seguirEditando() else alMapa()
            }
        }
        ComandaRoute(viewModel = comanda, onVolverAMesas = alMapa)
    }
}
