package com.appresso.app.data.remote

import java.math.BigDecimal
import com.appresso.app.data.catalogo.TipoAjuste
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
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Catálogo del backend: categorías y productos (HU 3.1.1). Contrato en docs/api-catalogo.md.
 * Se crea con el Retrofit compartido (`ContenedorApp.retrofit`), que agrega el token y cierra la
 * sesión ante un 401.
 *
 * Los parámetros `todas` / `todos` (solo administrador) se envían únicamente cuando valen `true`:
 * con `null` no aparecen en la URL, que es lo que usa el mesero para ver solo el menú.
 */
interface CatalogoApi {

    // ---------------------------------------------------------------- categorías

    @GET("api/categorias")
    suspend fun listarCategorias(
        @Query("todas") todas: Boolean?,
    ): Response<List<CategoriaDto>>

    @POST("api/categorias")
    suspend fun crearCategoria(
        @Body solicitud: CategoriaBody,
    ): Response<CategoriaDto>

    @PUT("api/categorias/{id}")
    suspend fun renombrarCategoria(
        @Path("id") id: Long,
        @Body solicitud: CategoriaBody,
    ): Response<CategoriaDto>

    @POST("api/categorias/{id}/desactivar")
    suspend fun desactivarCategoria(
        @Path("id") id: Long,
    ): Response<CategoriaDto>

    @POST("api/categorias/{id}/activar")
    suspend fun activarCategoria(
        @Path("id") id: Long,
    ): Response<CategoriaDto>

    // ---------------------------------------------------------------- productos

    @GET("api/productos")
    suspend fun listarProductos(
        @Query("categoriaId") categoriaId: Long?,
        @Query("todos") todos: Boolean?,
    ): Response<List<ProductoDto>>

    @GET("api/productos/{id}")
    suspend fun obtenerProducto(
        @Path("id") id: Long,
    ): Response<ProductoDto>

    @POST("api/productos")
    suspend fun crearProducto(
        @Body solicitud: ProductoCrearBody,
    ): Response<ProductoDto>

    @PUT("api/productos/{id}")
    suspend fun editarProducto(
        @Path("id") id: Long,
        @Body solicitud: ProductoEditarBody,
    ): Response<ProductoDto>

    @POST("api/productos/{id}/desactivar")
    suspend fun desactivarProducto(
        @Path("id") id: Long,
    ): Response<ProductoDto>

    @POST("api/productos/{id}/activar")
    suspend fun activarProducto(
        @Path("id") id: Long,
    ): Response<ProductoDto>

    @POST("api/inventario/productos/{id}/ajustes")
    suspend fun ajustar(@Path("id") id: Long, @Body ajuste: AjusteExistenciasBody): Response<ProductoDto>

    @GET("api/inventario/movimientos")
    suspend fun historial(
        @Query("producto") producto: String?,
        @Query("desde") desde: String?,
        @Query("hasta") hasta: String?,
    ): Response<List<MovimientoInventarioDto>>

    @GET("api/inventario/alertas")
    suspend fun alertas(): Response<List<ProductoDto>>
}

@Serializable
data class AjusteExistenciasBody(val tipo: TipoAjuste, val cantidad: Int, val concepto: String)

@Serializable
data class MovimientoInventarioDto(
    val id: Long,
    val producto: String,
    val tipo: String,
    val cantidad: Int,
    val stockAnterior: Int,
    val stockResultante: Int,
    val concepto: String? = null,
    val fecha: String,
    val usuario: String,
)

@Serializable
data class CategoriaDto(
    val id: Long,
    val nombre: String,
    val activa: Boolean,
    val totalProductos: Long,
)

@Serializable
data class CategoriaBody(
    val nombre: String,
)

@Serializable
data class ProductoDto(
    val id: Long,
    val categoriaId: Long,
    val categoriaNombre: String,
    val categoriaActiva: Boolean,
    val nombre: String,
    val descripcion: String? = null,
    @Serializable(with = PrecioSerializer::class) val precio: BigDecimal,
    val imagenUrl: String? = null,
    val stockActual: Int,
    val umbralMinimo: Int,
    val activo: Boolean,
)

/**
 * Los campos opcionales con valor `null` no se envían (el JSON de la app no escribe valores por
 * defecto): el backend los toma como «no indicado».
 */
@Serializable
data class ProductoCrearBody(
    val nombre: String,
    @Serializable(with = PrecioSerializer::class) val precio: BigDecimal,
    val categoriaId: Long,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val existenciasIniciales: Int? = null,
    val umbralMinimo: Int? = null,
)

/**
 * Editar no envía existencias: solo cambian con ajustes de inventario (RF-07). Un `umbralMinimo`
 * ausente conserva el actual.
 */
@Serializable
data class ProductoEditarBody(
    val nombre: String,
    @Serializable(with = PrecioSerializer::class) val precio: BigDecimal,
    val categoriaId: Long,
    val descripcion: String? = null,
    val imagenUrl: String? = null,
    val umbralMinimo: Int? = null,
)

/**
 * Precio sin pasar por Double (que pierde exactitud). El backend lo envía como número
 * (`6500.50`); también se acepta texto. Se envía como texto con punto decimal, que el backend
 * convierte a número.
 */
object PrecioSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Precio", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val texto = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()
        return try {
            BigDecimal(texto)
        } catch (e: NumberFormatException) {
            throw SerializationException("Precio inválido: $texto", e)
        }
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
}
