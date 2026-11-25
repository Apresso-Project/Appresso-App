package com.appresso.app.data.cocina

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.catalogo.ejecutarConsulta
import com.appresso.app.data.remote.CocinaApi
import com.appresso.app.data.remote.ComandaCocinaDto
import com.appresso.app.data.remote.ItemCocinaDto
import java.time.DateTimeException
import java.time.Instant

/** Habla con `GET /api/comandas/cocina` (docs/api-menu-cocina.md). */
class CocinaRepositoryRemoto(private val api: CocinaApi) : CocinaRepository {

    override suspend fun consultar(): ResultadoCatalogo<List<ComandaCocina>> =
        ejecutarConsulta({ api.consultar() }) { comandas -> comandas.mapNotNull { it.aComanda() } }
}

/** Un estado que cocina no conoce (no debería llegar) deja fuera la comanda en vez de inventarle uno. */
private fun ComandaCocinaDto.aComanda(): ComandaCocina? {
    val estadoCocina = when (estado) {
        "ENVIADA" -> EstadoCocina.PENDIENTE
        "EN_PREPARACION" -> EstadoCocina.EN_PREPARACION
        "LISTA" -> EstadoCocina.LISTA
        else -> return null
    }
    return ComandaCocina(
        id = id,
        mesa = mesa,
        estado = estadoCocina,
        fechaEnvio = fechaEnvio?.let(::leerInstante),
        items = items.map { it.aItem() },
    )
}

private fun ItemCocinaDto.aItem() = ItemCocina(
    producto = producto,
    cantidad = cantidad,
    observaciones = observaciones?.trim()?.takeIf { it.isNotEmpty() },
    agregado = agregadoDespuesDeEnvio,
)

private fun leerInstante(texto: String): Instant? = try {
    Instant.parse(texto)
} catch (e: DateTimeException) {
    null
}
