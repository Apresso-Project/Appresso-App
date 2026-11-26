package com.appresso.app.data.comandas

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.catalogo.ejecutarConsulta
import com.appresso.app.data.remote.CambioEstadoDto
import com.appresso.app.data.remote.ComandaDto
import com.appresso.app.data.remote.ComandasApi
import com.appresso.app.data.remote.CrearComandaDto
import com.appresso.app.data.remote.CrearMesaDto
import com.appresso.app.data.remote.ItemComandaDto
import com.appresso.app.data.remote.LineaComandaDto
import com.appresso.app.data.remote.MesaDto
import kotlinx.serialization.SerializationException

/**
 * Habla con `/api/mesas` y `/api/comandas` (docs/api-comandas.md). El token y el cierre de sesión ante
 * un 401 los maneja el cliente HTTP compartido.
 */
class ComandasRepositoryRemoto(private val api: ComandasApi) : ComandasRepository {

    override suspend fun listarMesas(): ResultadoCatalogo<List<Mesa>> =
        ejecutarConsulta({ api.listarMesas() }) { mesas -> mesas.map { it.aMesa() } }

    override suspend fun crearMesa(nombre: String): ResultadoCatalogo<Mesa> =
        ejecutarConsulta({ api.crearMesa(CrearMesaDto(nombre.trim())) }) { it.aMesa() }

    override suspend fun crearComanda(comanda: NuevaComanda): ResultadoCatalogo<Comanda> =
        ejecutarConsulta({ api.crearComanda(comanda.aDto()) }) { it.aComanda() }

    override suspend fun consultarComanda(id: Long): ResultadoCatalogo<Comanda> =
        ejecutarConsulta({ api.consultarComanda(id) }) { it.aComanda() }

    override suspend fun cambiarEstado(id: Long, estado: EstadoComanda): ResultadoCatalogo<Comanda> =
        ejecutarConsulta({ api.cambiarEstado(id, CambioEstadoDto(estado.name)) }) { it.aComanda() }
}

/** Un estado desconocido se muestra como ocupada: así nadie intenta abrir una comanda en ella. */
private fun MesaDto.aMesa() = Mesa(
    id = id,
    nombre = nombre,
    estado = if (estado == "DISPONIBLE") EstadoMesa.DISPONIBLE else EstadoMesa.OCUPADA,
)

private fun NuevaComanda.aDto() = CrearComandaDto(
    mesaId = mesaId,
    items = items.map { ItemComandaDto(it.productoId, it.cantidad, it.observaciones?.trim()?.takeIf(String::isNotEmpty)) },
    clienteId = clienteId,
    recompensaId = recompensaId,
)

/**
 * Un estado que la app no conoce no debería llegar (el backend solo tiene estos cinco); si llega, es
 * un cuerpo inválido y se trata como error del servidor.
 */
private fun ComandaDto.aComanda() = Comanda(
    id = id,
    mesaId = mesaId,
    mesaNombre = mesaNombre,
    estado = EstadoComanda.entries.firstOrNull { it.name == estado }
        ?: throw SerializationException("Estado de comanda desconocido: $estado"),
    lineas = lineas.map { it.aLinea() },
    subtotal = subtotal,
    descuento = descuento,
    total = total,
)

private fun LineaComandaDto.aLinea() = LineaComanda(
    productoId = productoId,
    productoNombre = productoNombre,
    cantidad = cantidad,
    precioUnitario = precioUnitario,
    subtotal = subtotal,
    observaciones = observaciones?.trim()?.takeIf { it.isNotEmpty() },
)
