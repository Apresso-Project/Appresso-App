package com.appresso.backend.fidelizacion;

/**
 * Petición para solicitar la eliminación o supresión de datos personales conforme al artículo 15
 * de la Ley 1581 de 2012 (Habeas Data).
 */
public record SolicitudEliminacionRequest(
		String motivo
) {
}
