package com.appresso.backend.comandas;

/**
 * Un producto de una comanda tal como lo ve cocina (RF-24): nombre, cantidad y observaciones sin
 * recortar. {@code agregadoDespuesDeEnvio} permite resaltarlo como «Agregado» (RF-21).
 */
public record ItemCocinaRespuesta(
		Long productoId,
		String producto,
		int cantidad,
		String observaciones,
		boolean agregadoDespuesDeEnvio) {

	static ItemCocinaRespuesta de(DetalleComanda d) {
		return new ItemCocinaRespuesta(d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad(),
				d.getObservaciones(), d.isAgregadoDespuesDeEnvio());
	}

}
