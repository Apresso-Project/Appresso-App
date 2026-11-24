package com.appresso.app.data.catalogo

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class TipoAjuste { ENTRADA_MANUAL, SALIDA_MANUAL, AJUSTE_MERMA }

data class MovimientoInventario(
    val id: Long,
    val producto: String,
    val tipo: String,
    val cantidad: Int,
    val stockAnterior: Int,
    val stockResultante: Int,
    val concepto: String?,
    val fecha: String,
    val usuario: String,
)

interface InventarioRepository {
    suspend fun ajustar(id: Long, tipo: TipoAjuste, cantidad: Int, concepto: String): ResultadoCatalogo<Producto>
    suspend fun historial(
        producto: String?,
        desde: LocalDate?,
        hasta: LocalDate?,
    ): ResultadoCatalogo<List<MovimientoInventario>>
    suspend fun alertas(): ResultadoCatalogo<List<Producto>>
}
