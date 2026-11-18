package com.appresso.backend.inventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos para crear o renombrar una categoría. */
public record CategoriaRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar 80 caracteres")
		String nombre) {

}
