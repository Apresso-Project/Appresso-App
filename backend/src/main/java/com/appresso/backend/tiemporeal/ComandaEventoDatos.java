package com.appresso.backend.tiemporeal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de un evento de comanda. Es una foto resumida: para ver todo el detalle
 * el cliente consulta la API REST.
 *
 * @param comandaId       id de la comanda
 * @param mesaId          id de la mesa
 * @param estado          estado actual (nombre de EstadoComanda: BORRADOR, PENDIENTE, EN_PREPARACION, LISTO, CANCELADA)
 * @param estadoAnterior  estado previo; solo en COMANDA_ESTADO_CAMBIADO, en otro caso null
 * @param total           total actual de la comanda
 * @param itemsAgregados  solo en COMANDA_MODIFICADA: lo que se añadió (cocina lo resalta, RF-21); en otro caso vacío
 */
public record ComandaEventoDatos(
		Long comandaId,
		Long mesaId,
		String estado,
		String estadoAnterior,
		BigDecimal total,
		List<ItemAgregado> itemsAgregados) {

	public ComandaEventoDatos {
		itemsAgregados = itemsAgregados == null ? List.of() : List.copyOf(itemsAgregados);
	}

	/** Un producto añadido a una comanda ya enviada. */
	public record ItemAgregado(Long productoId, int cantidad, String observaciones) {
	}

}
