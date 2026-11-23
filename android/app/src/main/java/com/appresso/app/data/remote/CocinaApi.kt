package com.appresso.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * Comandas activas para la pantalla de cocina (HU 3.3.1, SCRUM-88). Contrato en docs/api-menu-cocina.md.
 * Lo consultan administrador, mesero, cajero y cocinero; el cliente recibe 403.
 */
interface CocinaApi {

    /** Comandas enviadas, en preparación o listas, de la más antigua a la más reciente por hora de envío. */
    @GET("api/comandas/cocina")
    suspend fun consultar(): Response<List<ComandaCocinaDto>>
}

@Serializable
data class ComandaCocinaDto(
    val id: Long,
    val mesaId: Long? = null,
    val mesa: String,
    /** Valor interno: `ENVIADA`, `EN_PREPARACION` o `LISTA`. */
    val estado: String,
    val estadoCocina: String? = null,
    /** Instante ISO-8601 (UTC). */
    val fechaEnvio: String? = null,
    val items: List<ItemCocinaDto> = emptyList(),
)

@Serializable
data class ItemCocinaDto(
    val productoId: Long? = null,
    val producto: String,
    val cantidad: Int,
    val observaciones: String? = null,
    val agregadoDespuesDeEnvio: Boolean = false,
)
