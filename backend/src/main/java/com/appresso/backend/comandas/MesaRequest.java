package com.appresso.backend.comandas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear una mesa. */
public record MesaRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 30, message = "El nombre no puede superar 30 caracteres")
		String nombre) {
}
