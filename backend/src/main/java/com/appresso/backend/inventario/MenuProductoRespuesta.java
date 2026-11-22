package com.appresso.backend.inventario;

import java.math.BigDecimal;

/**
 * Un producto tal como lo ve quien consulta el menú. No lleva el umbral mínimo (dato del
 * administrador): quien consulta solo necesita saber si quedan pocas unidades ({@code disponibilidad}).
 */
public record MenuProductoRespuesta(
		Long id,
		String nombre,
		String descripcion,
		BigDecimal precio,
		String imagenUrl,
		int existencias,
		DisponibilidadProducto disponibilidad) {

	static MenuProductoRespuesta de(Producto p) {
		return new MenuProductoRespuesta(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
				p.getImagenUrl(), p.getStockActual(),
				DisponibilidadProducto.de(p.getStockActual(), p.getUmbralMinimo()));
	}

}
