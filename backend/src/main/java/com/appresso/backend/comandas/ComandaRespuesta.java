package com.appresso.backend.comandas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Una comanda tal como la ve la app: líneas con su subtotal, subtotal general, descuento y total
 * (RF-17). Los importes salen de lo guardado, no se recalculan al leer.
 */
public record ComandaRespuesta(Long id, Long mesaId, String mesaNombre, Long meseroId, Long clienteId,
		EstadoComanda estado, List<Linea> lineas, BigDecimal subtotal, BigDecimal descuento, BigDecimal total,
		Instant fechaCreacion, Instant fechaEnvio) {

	public record Linea(Long id, Long productoId, String productoNombre, int cantidad, BigDecimal precioUnitario,
			BigDecimal subtotal, String observaciones, boolean agregadoDespuesDeEnvio) {
	}

	/** Debe llamarse dentro de una transacción: lee las líneas y el producto de cada una. */
	public static ComandaRespuesta de(Comanda c) {
		List<Linea> lineas = c.getDetalles().stream()
				.map(d -> new Linea(d.getId(), d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad(),
						d.getPrecioUnitario(), d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())),
						d.getObservaciones(), d.isAgregadoDespuesDeEnvio()))
				.toList();
		return new ComandaRespuesta(c.getId(), c.getMesa().getId(), c.getMesa().getNombre(), c.getMesero().getId(),
				c.getCliente() == null ? null : c.getCliente().getId(), c.getEstado(), lineas, c.getSubtotal(),
				c.getDescuento(), c.getTotal(), c.getFechaCreacion(), c.getFechaEnvio());
	}

}
