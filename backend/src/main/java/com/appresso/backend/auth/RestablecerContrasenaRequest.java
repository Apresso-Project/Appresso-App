package com.appresso.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cambiar la contraseña con el código recibido por correo (RF-65). El máximo de 72 caracteres es el
 * límite de BCrypt; el servicio además comprueba los 72 bytes.
 */
public record RestablecerContrasenaRequest(
		@NotBlank(message = "Ingresa tu número de documento.")
		@Size(max = 30, message = "El documento debe tener entre 5 y 20 letras o números.")
		String documento,

		@NotBlank(message = "Ingresa el código que te enviamos.")
		@Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos.")
		String codigo,

		@NotBlank(message = "Ingresa la contraseña nueva.")
		@Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
		String contrasenaNueva) {

}
