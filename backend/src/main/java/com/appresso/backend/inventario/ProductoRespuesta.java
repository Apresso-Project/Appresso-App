package com.appresso.backend.inventario;

import java.math.BigDecimal;

/**
 * Un producto tal como lo ve la app. {@code categoriaActiva} explica por qué un producto activo
 * puede no aparecer en el menú: si su categoría está desactivada, queda oculto.
 */
public record ProductoRespuesta(
		Long id,
		Long categoriaId,
		String categoriaNombre,
		boolean categoriaActiva,
		String nombre,
		String descripcion,
		BigDecimal precio,
		String imagenUrl,
		int stockActual,
		int umbralMinimo,
		boolean activo) {

	public static ProductoRespuesta de(Producto p) {
		Categoria c = p.getCategoria();
		return new ProductoRespuesta(p.getId(), c.getId(), c.getNombre(), c.isActiva(), p.getNombre(),
				p.getDescripcion(), p.getPrecio(), p.getImagenUrl(), p.getStockActual(), p.getUmbralMinimo(),
				p.isActivo());
	}

}
