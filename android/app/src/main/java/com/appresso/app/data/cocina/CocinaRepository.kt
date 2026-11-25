package com.appresso.app.data.cocina

import com.appresso.app.data.catalogo.ResultadoCatalogo

/** Consulta de las comandas activas para cocina (RF-23, RF-24). */
interface CocinaRepository {

    /** Comandas activas, de la más antigua a la más reciente por hora de envío (el orden lo da el servidor). */
    suspend fun consultar(): ResultadoCatalogo<List<ComandaCocina>>
}
