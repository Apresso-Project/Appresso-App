package com.appresso.app.data.cocina

import java.time.Instant

/** Los tres estados que cocina ve (RF-23); las comandas sin enviar o cerradas no llegan. */
enum class EstadoCocina {
    /** Enviada por el mesero, todavía sin empezar (`ENVIADA`). */
    PENDIENTE,
    EN_PREPARACION,
    LISTA,
}

/** Un producto de una comanda tal como lo lee cocina (RF-24). */
data class ItemCocina(
    val producto: String,
    val cantidad: Int,
    /** Completas, sin recortar; null si no hay. */
    val observaciones: String?,
    /** Producto agregado después de enviar la comanda: cocina lo resalta como «Agregado» (RF-21). */
    val agregado: Boolean,
)

data class ComandaCocina(
    val id: Long,
    val mesa: String,
    val estado: EstadoCocina,
    /** Cuándo la envió el mesero; null si el servidor no lo dio o no se pudo leer. */
    val fechaEnvio: Instant?,
    val items: List<ItemCocina>,
)
