package com.appresso.backend.tiemporeal;

import java.math.BigDecimal;

/**
 * Datos de un evento del catálogo: cómo quedó el producto después del cambio.
 */
public record ProductoEventoDatos(
		Long productoId,
		Long categoriaId,
		String nombre,
		BigDecimal precio,
		int stockActual,
		int umbralMinimo,
		boolean activo) {

}
