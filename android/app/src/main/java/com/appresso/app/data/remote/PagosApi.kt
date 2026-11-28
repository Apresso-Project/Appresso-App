package com.appresso.app.data.remote

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Confirmación de pagos del backend (6.1.1). Contrato en docs/api-pagos.md. Se crea con el
 * Retrofit compartido (`ContenedorApp.retrofit`), que agrega el token y cierra la sesión ante un 401.
 *
 * El valor no se envía: es el total de la comanda y lo calcula el servidor.
 * Respuestas: 201 pago registrado; 200 reintento de un pago ya registrado (mismo identificador).
 */
interface PagosApi {

    @POST("api/pagos/efectivo")
    suspend fun efectivo(
        @Body solicitud: PagoBody,
    ): Response<PagoDto>

    @POST("api/pagos/transferencia")
    suspend fun transferencia(
        @Body solicitud: PagoBody,
    ): Response<PagoDto>
}

@Serializable
data class PagoBody(
    val comandaId: Long,
    val identificadorOperacion: String,
)

/** Medio de pago como lo escribe el backend. */
@Serializable
enum class MedioPagoDto { EFECTIVO, TRANSFERENCIA }

@Serializable
data class PagoDto(
    val id: Long,
    val comandaId: Long,
    val mesa: String,
    @Serializable(with = DineroSerializer::class) val valor: BigDecimal,
    val medio: MedioPagoDto,
    val fecha: String,
    val confirmadoPorId: Long,
    val confirmadoPorNombre: String,
    val identificadorOperacion: String,
    val jornadaId: Long,
    val comandaEstado: String,
)

/**
 * Lee un valor de dinero del JSON sin pasar por Double (que pierde exactitud). El backend lo envía
 * como número (`12500.00`); también se acepta como texto.
 */
object DineroSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Dinero", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val texto = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()
        return try {
            BigDecimal(texto)
        } catch (e: NumberFormatException) {
            throw SerializationException("Valor de dinero inválido: $texto", e)
        }
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
}
