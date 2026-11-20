package com.appresso.app.data.clientes

import kotlinx.serialization.Serializable

/** Lo que el personal ve de un cliente al buscarlo: solo lo necesario para identificarlo y sus puntos. */
@Serializable
data class Cliente(
    val id: Long,
    val nombre: String,
    val apellido: String,
    val documento: String,
    val puntos: Int = 0,
)

/** Resultado de buscar un cliente, ya traducido de las respuestas del backend. */
sealed interface ResultadoBusqueda {
    data class Encontrado(val cliente: Cliente) : ResultadoBusqueda

    /** 404: "Cliente no encontrado". */
    data object NoEncontrado : ResultadoBusqueda

    /** 403. [mensaje] es el del servidor y puede faltar. */
    data class SinPermiso(val mensaje: String?) : ResultadoBusqueda

    data object SinConexion : ResultadoBusqueda
    data object ErrorDelServidor : ResultadoBusqueda
}

/** Búsqueda de clientes por documento (RF-51). Interfaz para poder sustituirla en las pruebas. */
interface ClientesRepository {

    /** Nunca lanza por errores de red o del servidor: los devuelve como [ResultadoBusqueda]. */
    suspend fun buscarPorDocumento(documento: String): ResultadoBusqueda
}
