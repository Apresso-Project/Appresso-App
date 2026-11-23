package com.appresso.app.data.menu

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.catalogo.ejecutarConsulta
import com.appresso.app.data.remote.MenuApi
import com.appresso.app.data.remote.MenuCategoriaDto
import com.appresso.app.data.remote.MenuProductoDto

/**
 * Habla con `GET /api/menu` (docs/api-menu-cocina.md). El token y el cierre de sesión ante un 401 los
 * maneja el cliente HTTP compartido.
 */
class MenuRepositoryRemoto(private val api: MenuApi) : MenuRepository {

    override suspend fun consultar(): ResultadoCatalogo<List<MenuCategoria>> =
        ejecutarConsulta({ api.consultar() }) { categorias ->
            // Una categoría sin productos no debería llegar; si llega, no se muestra vacía.
            categorias.map { it.aCategoria() }.filter { it.productos.isNotEmpty() }
        }
}

private fun MenuCategoriaDto.aCategoria() = MenuCategoria(id, nombre, productos.map { it.aProducto() })

private fun MenuProductoDto.aProducto() = MenuProducto(
    id = id,
    nombre = nombre,
    descripcion = descripcion?.trim()?.takeIf { it.isNotEmpty() },
    precio = precio,
    imagenUrl = imagenUrl?.trim()?.takeIf { it.isNotEmpty() },
    existencias = existencias,
    // Un valor desconocido se muestra como disponible: el servidor ya dejó fuera lo agotado.
    disponibilidad = if (disponibilidad == "POCAS_UNIDADES") Disponibilidad.POCAS_UNIDADES else Disponibilidad.DISPONIBLE,
)
