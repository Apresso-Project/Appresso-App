package com.appresso.app.tiemporeal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Los eventos que cambian el catálogo (`CATEGORIA_*`, `PRODUCTO_*`, `EXISTENCIAS_*`; ver
 * docs/tiempo-real.md), como un aviso sin datos: las pantallas del catálogo reaccionan volviendo a
 * pedir su lista por REST, que es la fuente de verdad. Los eventos de comandas se descartan.
 */
fun Flow<EventoTiempoReal>.cambiosDeCatalogo(): Flow<Unit> =
    filter { it.esDeCatalogo() }.map { }

internal fun EventoTiempoReal.esDeCatalogo(): Boolean =
    tipo.startsWith("CATEGORIA_") || tipo.startsWith("PRODUCTO_") || tipo.startsWith("EXISTENCIAS_")
