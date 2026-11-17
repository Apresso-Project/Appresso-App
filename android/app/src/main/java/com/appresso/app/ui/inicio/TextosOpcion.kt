package com.appresso.app.ui.inicio

import androidx.annotation.StringRes
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.permisos.Opcion

/** Textos de cada opción del menú (en strings.xml). */
@StringRes
fun Opcion.titulo(): Int = when (this) {
    Opcion.USUARIOS -> R.string.opcion_usuarios
    Opcion.PRODUCTOS_E_INVENTARIO -> R.string.opcion_productos_inventario
    Opcion.MESAS_Y_COMANDAS -> R.string.opcion_mesas_comandas
    Opcion.PEDIDOS -> R.string.opcion_pedidos
    Opcion.COCINA -> R.string.opcion_cocina
    Opcion.COBROS -> R.string.opcion_cobros
    Opcion.CAJA -> R.string.opcion_caja
    Opcion.SOLICITUDES_RETIRO -> R.string.opcion_solicitudes_retiro
    Opcion.CLIENTES -> R.string.opcion_clientes
    Opcion.RECOMPENSAS_Y_PROMOCIONES -> R.string.opcion_recompensas_promociones
    Opcion.MIS_PUNTOS -> R.string.opcion_mis_puntos
}

@StringRes
fun Opcion.descripcion(): Int = when (this) {
    Opcion.USUARIOS -> R.string.opcion_usuarios_desc
    Opcion.PRODUCTOS_E_INVENTARIO -> R.string.opcion_productos_inventario_desc
    Opcion.MESAS_Y_COMANDAS -> R.string.opcion_mesas_comandas_desc
    Opcion.PEDIDOS -> R.string.opcion_pedidos_desc
    Opcion.COCINA -> R.string.opcion_cocina_desc
    Opcion.COBROS -> R.string.opcion_cobros_desc
    Opcion.CAJA -> R.string.opcion_caja_desc
    Opcion.SOLICITUDES_RETIRO -> R.string.opcion_solicitudes_retiro_desc
    Opcion.CLIENTES -> R.string.opcion_clientes_desc
    Opcion.RECOMPENSAS_Y_PROMOCIONES -> R.string.opcion_recompensas_promociones_desc
    Opcion.MIS_PUNTOS -> R.string.opcion_mis_puntos_desc
}

@StringRes
fun Rol.nombreVisible(): Int = when (this) {
    Rol.ADMINISTRADOR -> R.string.rol_administrador
    Rol.CAJERO -> R.string.rol_cajero
    Rol.MESERO -> R.string.rol_mesero
    Rol.COCINERO -> R.string.rol_cocinero
    Rol.CLIENTE -> R.string.rol_cliente
}
