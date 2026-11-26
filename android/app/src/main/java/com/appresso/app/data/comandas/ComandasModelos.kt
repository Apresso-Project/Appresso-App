package com.appresso.app.data.comandas

import java.math.BigDecimal

/** Una mesa queda `OCUPADA` al crear su comanda y vuelve a `DISPONIBLE` al cerrarla (lo decide el backend). */
enum class EstadoMesa { DISPONIBLE, OCUPADA }

/** Una mesa del mapa de mesas. */
data class Mesa(
    val id: Long,
    val nombre: String,
    val estado: EstadoMesa,
) {
    val disponible: Boolean get() = estado == EstadoMesa.DISPONIBLE
}

/**
 * Ciclo de vida de una comanda (docs/api-comandas.md). Solo avanza un paso por vez, sin saltos ni
 * retrocesos; quién puede ejecutar cada paso lo valida el backend (400 si no corresponde).
 */
enum class EstadoComanda {
    PENDIENTE,
    ENVIADA,
    EN_PREPARACION,
    LISTA,
    CERRADA,
    ;

    /** El único estado al que se puede pasar desde este, o null si ya está cerrada. */
    val siguiente: EstadoComanda? get() = entries.getOrNull(ordinal + 1)
}

/** Un producto que el mesero pide: cuántas unidades y, si quiere, una nota para cocina. */
data class ItemPedido(
    val productoId: Long,
    val cantidad: Int,
    val observaciones: String? = null,
)

/** Lo necesario para crear una comanda. [recompensaId] (descuento autorizado) exige [clienteId]. */
data class NuevaComanda(
    val mesaId: Long,
    val items: List<ItemPedido>,
    val clienteId: Long? = null,
    val recompensaId: Long? = null,
)

/** Una línea de la comanda tal como la guardó el backend: el precio queda fijo al crearla. */
data class LineaComanda(
    val productoId: Long,
    val productoNombre: String,
    val cantidad: Int,
    val precioUnitario: BigDecimal,
    val subtotal: BigDecimal,
    val observaciones: String?,
)

/** Una comanda con los importes que calculó el backend (subtotal − descuento = total). */
data class Comanda(
    val id: Long,
    val mesaId: Long,
    val mesaNombre: String,
    val estado: EstadoComanda,
    val lineas: List<LineaComanda>,
    val subtotal: BigDecimal,
    val descuento: BigDecimal,
    val total: BigDecimal,
)
