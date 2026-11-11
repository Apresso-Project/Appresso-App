package com.appresso.backend.auth;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de las respuestas de error. {@code campos} solo aparece en errores de validación y
 * asocia cada campo con su problema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorRespuesta(String mensaje, Map<String, String> campos) {

	public ErrorRespuesta(String mensaje) {
		this(mensaje, null);
	}

}
