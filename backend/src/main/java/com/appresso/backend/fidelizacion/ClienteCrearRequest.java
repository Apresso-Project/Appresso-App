package com.appresso.backend.fidelizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para registrar un cliente en el programa de fidelización (RF-50).
 * Conforme a la Ley 1581 de 2012, es obligatorio que el cliente otorgue su autorización explícita
 * para el tratamiento de sus datos personales.
 */
public record ClienteCrearRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 80, message = "El nombre no puede superar 80 caracteres")
		String nombre,

		@NotBlank(message = "El apellido es obligatorio")
		@Size(max = 80, message = "El apellido no puede superar 80 caracteres")
		String apellido,

		@NotBlank(message = "El documento es obligatorio")
		@Size(max = 20, message = "El documento no puede superar 20 caracteres")
		String documento,

		@NotBlank(message = "El celular es obligatorio")
		@Size(max = 20, message = "El celular no puede superar 20 caracteres")
		String celular,

		@NotNull(message = "Debe especificar la aceptación de tratamiento de datos personales")
		Boolean aceptoTratamientoDatos
) {
}
