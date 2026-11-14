package com.appresso.app.permisos

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Rol.ADMINISTRADOR
import com.appresso.app.data.auth.Rol.CAJERO
import com.appresso.app.data.auth.Rol.CLIENTE
import com.appresso.app.data.auth.Rol.COCINERO
import com.appresso.app.data.auth.Rol.MESERO

/**
 * Qué puede hacer y ver cada rol en la app (SCRUM-48).
 *
 * Refleja la política del backend (docs/permisos-por-rol.md): la app oculta lo que el backend
 * igual rechazaría con 403. Ocultar no es la seguridad (esa la da el backend); es para que cada
 * rol vea solo lo suyo. Si cambia una regla en el backend, se cambia también aquí.
 *
 * Cómo usarlo en una pantalla:
 * ```
 * if (puede(sesion.rol, Accion.GESTIONAR_CATALOGO)) { BotonNuevoProducto(...) }
 * ```
 */
enum class Accion(vararg permitidos: Rol) {
    // Usuarios
    GESTIONAR_USUARIOS(ADMINISTRADOR),

    // Inventario
    VER_CATALOGO(ADMINISTRADOR, MESERO),
    GESTIONAR_CATALOGO(ADMINISTRADOR),
    GESTIONAR_INVENTARIO(ADMINISTRADOR),

    // Comandas y cocina
    VER_MESAS(ADMINISTRADOR, MESERO),
    CREAR_COMANDA(MESERO),
    APLICAR_RECOMPENSA(MESERO),
    VER_COMANDAS(ADMINISTRADOR, CAJERO, MESERO, COCINERO),
    CAMBIAR_ESTADO_PREPARACION(COCINERO),

    // Caja y pagos
    COBRAR_EFECTIVO(CAJERO),
    CONFIRMAR_TRANSFERENCIA(CAJERO, MESERO),
    VER_QR(CAJERO, MESERO),
    VER_PAGOS_Y_MOVIMIENTOS(ADMINISTRADOR, CAJERO),
    VER_SALDOS(ADMINISTRADOR),
    REGISTRAR_INGRESO(ADMINISTRADOR),
    REGISTRAR_RETIRO(ADMINISTRADOR),
    SOLICITAR_RETIRO(CAJERO),
    VER_SOLICITUDES_RETIRO(ADMINISTRADOR, CAJERO),
    DECIDIR_RETIRO(ADMINISTRADOR),
    CERRAR_CAJA(CAJERO),
    REGISTRAR_BASE(ADMINISTRADOR),
    ABRIR_JORNADA(ADMINISTRADOR, CAJERO),
    VER_INFORMES_CIERRE(ADMINISTRADOR, CAJERO),

    // Fidelización
    REGISTRAR_CLIENTE(ADMINISTRADOR, MESERO),
    BUSCAR_CLIENTE(ADMINISTRADOR, MESERO),
    VER_MIS_PUNTOS(CLIENTE),
    VER_RECOMPENSAS(ADMINISTRADOR, MESERO, CLIENTE),
    GESTIONAR_RECOMPENSAS(ADMINISTRADOR),
    VER_PROMOCIONES(ADMINISTRADOR, CLIENTE),
    GESTIONAR_PROMOCIONES(ADMINISTRADOR),
    ;

    val roles: Set<Rol> = permitidos.toSet()
}

/**
 * Las entradas del menú de inicio. Cada una agrupa las acciones que se hacen en esa sección; la
 * sección real de cada módulo reemplaza a la provisional cuando exista.
 */
enum class Opcion(vararg incluidas: Accion) {
    USUARIOS(Accion.GESTIONAR_USUARIOS),
    PRODUCTOS_E_INVENTARIO(Accion.VER_CATALOGO, Accion.GESTIONAR_CATALOGO, Accion.GESTIONAR_INVENTARIO),
    MESAS_Y_COMANDAS(
        Accion.VER_MESAS, Accion.CREAR_COMANDA, Accion.VER_COMANDAS, Accion.VER_CATALOGO,
        Accion.APLICAR_RECOMPENSA, Accion.VER_RECOMPENSAS,
    ),
    PEDIDOS(Accion.VER_COMANDAS, Accion.VER_MESAS),
    COCINA(Accion.VER_COMANDAS, Accion.CAMBIAR_ESTADO_PREPARACION),
    COBROS(Accion.COBRAR_EFECTIVO, Accion.CONFIRMAR_TRANSFERENCIA, Accion.VER_QR),
    CAJA(
        Accion.VER_SALDOS, Accion.VER_PAGOS_Y_MOVIMIENTOS, Accion.REGISTRAR_INGRESO, Accion.REGISTRAR_RETIRO,
        Accion.CERRAR_CAJA, Accion.REGISTRAR_BASE, Accion.ABRIR_JORNADA, Accion.VER_INFORMES_CIERRE,
    ),
    SOLICITUDES_RETIRO(Accion.SOLICITAR_RETIRO, Accion.VER_SOLICITUDES_RETIRO, Accion.DECIDIR_RETIRO),
    CLIENTES(Accion.BUSCAR_CLIENTE, Accion.REGISTRAR_CLIENTE, Accion.VER_RECOMPENSAS),
    RECOMPENSAS_Y_PROMOCIONES(
        Accion.VER_RECOMPENSAS, Accion.GESTIONAR_RECOMPENSAS, Accion.VER_PROMOCIONES, Accion.GESTIONAR_PROMOCIONES,
    ),
    MIS_PUNTOS(Accion.VER_MIS_PUNTOS),
    ;

    val acciones: Set<Accion> = incluidas.toSet()
}

/** ¿El rol puede hacer esta acción? Úsalo para mostrar u ocultar botones dentro de una pantalla. */
fun puede(rol: Rol, accion: Accion): Boolean = rol in accion.roles

/** El menú de inicio de cada rol, en el orden en que se muestra. */
fun opcionesPara(rol: Rol): List<Opcion> = when (rol) {
    ADMINISTRADOR -> listOf(
        Opcion.USUARIOS,
        Opcion.PRODUCTOS_E_INVENTARIO,
        Opcion.PEDIDOS,
        Opcion.CAJA,
        Opcion.SOLICITUDES_RETIRO,
        Opcion.CLIENTES,
        Opcion.RECOMPENSAS_Y_PROMOCIONES,
    )
    CAJERO -> listOf(Opcion.COBROS, Opcion.PEDIDOS, Opcion.CAJA, Opcion.SOLICITUDES_RETIRO)
    MESERO -> listOf(Opcion.MESAS_Y_COMANDAS, Opcion.COBROS, Opcion.CLIENTES)
    COCINERO -> listOf(Opcion.COCINA)
    CLIENTE -> listOf(Opcion.MIS_PUNTOS, Opcion.RECOMPENSAS_Y_PROMOCIONES)
}

/** ¿El rol puede abrir esta sección? */
fun puedeVer(rol: Rol, opcion: Opcion): Boolean = opcion in opcionesPara(rol)
