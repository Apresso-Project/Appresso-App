package com.appresso.backend.fidelizacion;

import jakarta.validation.constraints.Size;

/**
 * Petición para solicitar la eliminación o supresión de datos personales conforme al artículo 15
 * de la Ley 1581 de 2012 (Habeas Data). El motivo es opcional y no debe incluir datos personales:
 * queda en la auditoría, que no se puede borrar.
 */
public record SolicitudEliminacionRequest(
		@Size(max = 500, message = "El motivo no puede superar 500 caracteres")
		String motivo
) {
}
