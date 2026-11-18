package com.appresso.backend.fidelizacion;

import java.time.Instant;

/**
 * Datos públicos del cliente registrados en el sistema, incluyendo la trazabilidad del consentimiento
 * legal de la Ley 1581 de 2012.
 */
public record ClienteRespuesta(
		Long id,
		String nombre,
		String apellido,
		String documento,
		String celular,
		int puntos,
		boolean aceptoTratamientoDatos,
		Instant fechaAceptacionDatos,
		Instant fechaRegistro
) {

	public static ClienteRespuesta desde(Cliente c) {
		return new ClienteRespuesta(
				c.getId(),
				c.getNombre(),
				c.getApellido(),
				c.getDocumento(),
				c.getCelular(),
				c.getPuntos(),
				c.isAceptoTratamientoDatos(),
				c.getFechaAceptacionDatos(),
				c.getFechaRegistro()
		);
	}

}
