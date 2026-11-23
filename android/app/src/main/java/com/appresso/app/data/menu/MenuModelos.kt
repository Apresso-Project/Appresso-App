package com.appresso.app.data.menu

import java.math.BigDecimal

/**
 * Existencias de un producto del menú (RNF-17). Los agotados no llegan: el backend los deja fuera
 * (RF-13), por eso no hay un valor para ellos.
 */
enum class Disponibilidad {
    /** Las existencias superan el umbral mínimo. */
    DISPONIBLE,

    /** Quedan pocas: igual o menos que el umbral mínimo, pero al menos una. */
    POCAS_UNIDADES,
}

/** Un producto tal como lo ve quien consulta el menú. */
data class MenuProducto(
    val id: Long,
    val nombre: String,
    val descripcion: String?,
    val precio: BigDecimal,
    val imagenUrl: String?,
    val existencias: Int,
    val disponibilidad: Disponibilidad,
)

/** Una categoría del menú con sus productos disponibles. Nunca llega vacía. */
data class MenuCategoria(
    val id: Long,
    val nombre: String,
    val productos: List<MenuProducto>,
)
