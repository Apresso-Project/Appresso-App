package com.appresso.app.data.remote

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/**
 * Menú de solo lectura del backend (HU 3.3.1, SCRUM-87). Contrato en docs/api-menu-cocina.md.
 * Lo consultan el administrador, el mesero y el cliente; el cajero y el cocinero reciben 403.
 */
interface MenuApi {

    /** Categorías con sus productos disponibles (activos, de categoría activa y con existencias). */
    @GET("api/menu")
    suspend fun consultar(): Response<List<MenuCategoriaDto>>
}

@Serializable
data class MenuCategoriaDto(
    val id: Long,
    val nombre: String,
    val productos: List<MenuProductoDto> = emptyList(),
)

/** El precio usa [PrecioSerializer]: se lee exacto, sin pasar por Double. */
@Serializable
data class MenuProductoDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    @Serializable(with = PrecioSerializer::class) val precio: BigDecimal,
    val imagenUrl: String? = null,
    val existencias: Int,
    /** `DISPONIBLE` o `POCAS_UNIDADES`. */
    val disponibilidad: String,
)
