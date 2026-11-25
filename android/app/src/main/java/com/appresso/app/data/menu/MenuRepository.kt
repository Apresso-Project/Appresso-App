package com.appresso.app.data.menu

import com.appresso.app.data.catalogo.ResultadoCatalogo

/**
 * Consulta del menú de solo lectura (RF-11, RF-13). Devuelve [ResultadoCatalogo]: es el mismo
 * conjunto de resultados que el catálogo (éxito, sin permiso, sin conexión…).
 */
interface MenuRepository {

    /** Categorías con sus productos disponibles, en el orden que manda el servidor (por nombre). */
    suspend fun consultar(): ResultadoCatalogo<List<MenuCategoria>>
}
