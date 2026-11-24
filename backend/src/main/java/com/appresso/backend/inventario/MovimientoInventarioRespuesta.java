package com.appresso.backend.inventario;

import java.time.Instant;

public record MovimientoInventarioRespuesta(
		Long id, Long productoId, String producto, TipoMovimientoInventario tipo, int cantidad,
		int stockAnterior, int stockResultante, String concepto, Instant fecha, Long usuarioId, String usuario) {

	static MovimientoInventarioRespuesta de(MovimientoInventario movimiento) {
		int resultante = movimiento.getStockResultante();
		int cantidad = movimiento.getCantidad();
		int anterior = movimiento.getTipo() == TipoMovimientoInventario.ENTRADA_MANUAL
				? resultante - cantidad : resultante + cantidad;
		return new MovimientoInventarioRespuesta(movimiento.getId(), movimiento.getProducto().getId(),
				movimiento.getProducto().getNombre(), movimiento.getTipo(), cantidad, anterior, resultante,
				movimiento.getJustificacion(), movimiento.getFecha(), movimiento.getUsuario().getId(),
				movimiento.getUsuario().getNombre());
	}
}
