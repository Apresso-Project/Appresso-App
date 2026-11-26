package com.appresso.app.data.remote

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Mesas y comandas del backend (HU 4.1.1). Contrato en docs/api-comandas.md.
 *
 * - Mesas: listar, administrador y mesero; crear, solo administrador (403 a los demás).
 * - Comandas: crear, administrador y mesero; consultar y cambiar de estado, todo el personal (lo que
 *   cada rol puede poner lo decide el backend: transición inválida o rol que no corresponde → 400).
 */
interface ComandasApi {

    /** Mapa de mesas, ordenado por nombre sin distinguir mayúsculas. */
    @GET("api/mesas")
    suspend fun listarMesas(): Response<List<MesaDto>>

    /** 201 con la mesa `DISPONIBLE`; nombre vacío o largo → 400, repetido → 409 (`campos.nombre`). */
    @POST("api/mesas")
    suspend fun crearMesa(@Body solicitud: CrearMesaDto): Response<MesaDto>

    /** 201 con la comanda `PENDIENTE` y sus importes (subtotales, descuento y total). */
    @POST("api/comandas")
    suspend fun crearComanda(@Body solicitud: CrearComandaDto): Response<ComandaDto>

    @GET("api/comandas/{id}")
    suspend fun consultarComanda(@Path("id") id: Long): Response<ComandaDto>

    /** Avanza la comanda un paso (p. ej. `ENVIADA` para mandarla a cocina). */
    @PATCH("api/comandas/{id}/estado")
    suspend fun cambiarEstado(@Path("id") id: Long, @Body solicitud: CambioEstadoDto): Response<ComandaDto>
}

@Serializable
data class MesaDto(
    val id: Long,
    val nombre: String,
    /** `DISPONIBLE` u `OCUPADA`. */
    val estado: String,
)

@Serializable
data class CrearMesaDto(val nombre: String)

/** `clienteId` y `recompensaId` son opcionales: si son null no se envían (el descuento exige cliente). */
@Serializable
data class CrearComandaDto(
    val mesaId: Long,
    val items: List<ItemComandaDto>,
    val clienteId: Long? = null,
    val recompensaId: Long? = null,
)

@Serializable
data class ItemComandaDto(
    val productoId: Long,
    val cantidad: Int,
    val observaciones: String? = null,
)

@Serializable
data class CambioEstadoDto(val estado: String)

/** Los importes usan [PrecioSerializer]: se leen exactos, sin pasar por Double. */
@Serializable
data class ComandaDto(
    val id: Long,
    val mesaId: Long,
    val mesaNombre: String,
    val meseroId: Long? = null,
    val clienteId: Long? = null,
    /** `PENDIENTE`, `ENVIADA`, `EN_PREPARACION`, `LISTA` o `CERRADA`. */
    val estado: String,
    val lineas: List<LineaComandaDto> = emptyList(),
    @Serializable(with = PrecioSerializer::class) val subtotal: BigDecimal,
    @Serializable(with = PrecioSerializer::class) val descuento: BigDecimal,
    @Serializable(with = PrecioSerializer::class) val total: BigDecimal,
    val fechaCreacion: String? = null,
    val fechaEnvio: String? = null,
)

@Serializable
data class LineaComandaDto(
    val id: Long? = null,
    val productoId: Long,
    val productoNombre: String,
    val cantidad: Int,
    @Serializable(with = PrecioSerializer::class) val precioUnitario: BigDecimal,
    @Serializable(with = PrecioSerializer::class) val subtotal: BigDecimal,
    val observaciones: String? = null,
    val agregadoDespuesDeEnvio: Boolean = false,
)
