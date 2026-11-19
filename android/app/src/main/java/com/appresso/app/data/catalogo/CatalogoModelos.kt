package com.appresso.app.data.catalogo

import java.math.BigDecimal

/** Una categoría tal como la entrega el backend (docs/api-catalogo.md). */
data class Categoria(
    val id: Long,
    val nombre: String,
    val activa: Boolean,
    /** Cuántos productos tiene (activos o no). */
    val totalProductos: Long,
)

/**
 * Un producto del catálogo. Está en el menú solo si [activo] y [categoriaActiva] son true: desactivar
 * una categoría saca del menú a sus productos sin cambiar su [activo].
 */
data class Producto(
    val id: Long,
    val categoriaId: Long,
    val categoriaNombre: String,
    val categoriaActiva: Boolean,
    val nombre: String,
    val descripcion: String?,
    val precio: BigDecimal,
    val imagenUrl: String?,
    val stockActual: Int,
    val umbralMinimo: Int,
    val activo: Boolean,
) {
    val enElMenu: Boolean get() = activo && categoriaActiva
}

/**
 * Datos que el administrador escribe para crear o editar un producto. Al editar se ignoran
 * [existenciasIniciales] (las existencias solo cambian con ajustes de inventario).
 * Los textos vacíos se envían como «no indicado».
 */
data class DatosProducto(
    val nombre: String,
    val precio: BigDecimal,
    val categoriaId: Long,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val existenciasIniciales: Int? = null,
    val umbralMinimo: Int? = null,
)

/**
 * Resultado de una operación del catálogo, ya traducido de las respuestas del backend (200/201, 400,
 * 401, 403, 404, 409 y fallos de red; ver docs/api-catalogo.md). Los textos que ve la persona no se
 * definen aquí sino en quien lo consume, salvo los mensajes del servidor.
 */
sealed interface ResultadoCatalogo<out T> {

    data class Exito<T>(val valor: T) : ResultadoCatalogo<T>

    /** 400: algún dato no es válido. [campos] asocia cada campo con su problema (p. ej. `nombre`, `precio`). */
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String>) : ResultadoCatalogo<Nothing>

    /**
     * 409: nombre de categoría repetido (`campos.nombre`) o categoría desactivada al crear o mover un
     * producto (`campos.categoriaId`).
     */
    data class Conflicto(val mensaje: String?, val campos: Map<String, String>) : ResultadoCatalogo<Nothing>

    /** 401: no hay sesión o el token venció. */
    data object SesionInvalida : ResultadoCatalogo<Nothing>

    /** 403: el rol no puede hacerlo. [mensaje] es el del servidor. */
    data class SinPermiso(val mensaje: String?) : ResultadoCatalogo<Nothing>

    /** 404: la categoría o el producto ya no existe. */
    data object NoEncontrado : ResultadoCatalogo<Nothing>

    data object SinConexion : ResultadoCatalogo<Nothing>

    data object ErrorDelServidor : ResultadoCatalogo<Nothing>
}
