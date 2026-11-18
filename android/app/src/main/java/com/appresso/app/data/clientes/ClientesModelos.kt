package com.appresso.app.data.clientes

import kotlinx.serialization.Serializable

@Serializable
data class Cliente(
    val id: Long,
    val nombre: String,
    val apellido: String,
    val documento: String,
    val celular: String,
    val puntos: Int = 0,
    val aceptoTratamientoDatos: Boolean,
    val fechaAceptacionDatos: String? = null,
    val fechaRegistro: String? = null,
)

@Serializable
data class ClienteCrearRequest(
    val nombre: String,
    val apellido: String,
    val documento: String,
    val celular: String,
    val aceptoTratamientoDatos: Boolean,
)

@Serializable
data class SolicitudEliminacionRequest(
    val motivo: String? = null,
)

sealed interface ResultadoClientes<out T> {
    data class Exito<T>(val datos: T) : ResultadoClientes<T>
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String> = emptyMap()) : ResultadoClientes<Nothing>
    data class Conflicto(val mensaje: String?) : ResultadoClientes<Nothing>
    data class NoEncontrado(val mensaje: String?) : ResultadoClientes<Nothing>
    data class SinPermiso(val mensaje: String?) : ResultadoClientes<Nothing>
    data object SinConexion : ResultadoClientes<Nothing>
    data object ErrorDelServidor : ResultadoClientes<Nothing>
}
