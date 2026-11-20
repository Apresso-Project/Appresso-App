package com.appresso.app.data.catalogo

import kotlinx.serialization.Serializable

@Serializable
data class Categoria(
    val id: Long,
    val nombre: String,
    val activa: Boolean = true,
    val totalProductos: Int = 0,
)

@Serializable
data class CategoriaCrearRequest(
    val nombre: String,
)

@Serializable
data class CategoriaEditarRequest(
    val nombre: String,
)

@Serializable
data class Producto(
    val id: Long,
    val categoriaId: Long,
    val categoriaNombre: String = "",
    val categoriaActiva: Boolean = true,
    val nombre: String,
    val descripcion: String? = null,
    val precio: Double,
    val imagenUrl: String? = null,
    val stockActual: Int = 0,
    val umbralMinimo: Int = 0,
    val activo: Boolean = true,
)

@Serializable
data class ProductoCrearRequest(
    val nombre: String,
    val descripcion: String? = null,
    val precio: Double,
    val categoriaId: Long,
    val imagenUrl: String? = null,
    val existenciasIniciales: Int? = null,
    val umbralMinimo: Int? = null,
)

@Serializable
data class ProductoEditarRequest(
    val nombre: String,
    val descripcion: String? = null,
    val precio: Double,
    val categoriaId: Long,
    val imagenUrl: String? = null,
    val umbralMinimo: Int? = null,
)

sealed interface ResultadoCatalogo<out T> {
    data class Exito<T>(val datos: T) : ResultadoCatalogo<T>
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String> = emptyMap()) : ResultadoCatalogo<Nothing>
    data class Conflicto(val mensaje: String?, val campos: Map<String, String> = emptyMap()) : ResultadoCatalogo<Nothing>
    data class NoEncontrado(val mensaje: String?) : ResultadoCatalogo<Nothing>
    data class SinPermiso(val mensaje: String?) : ResultadoCatalogo<Nothing>
    data object SinConexion : ResultadoCatalogo<Nothing>
    data object ErrorDelServidor : ResultadoCatalogo<Nothing>
}
