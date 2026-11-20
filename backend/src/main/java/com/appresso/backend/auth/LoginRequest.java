package com.appresso.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code identificador} es el correo para el personal o el número de documento para el cliente
 * (RF-01): si trae {@code @} se trata como correo. El máximo de 72 caracteres de la contraseña es el
 * límite de BCrypt.
 */
public record LoginRequest(
		@NotBlank(message = "El correo o documento es obligatorio")
		@Size(max = 150, message = "El correo o documento no puede superar 150 caracteres")
		String identificador,

		@NotBlank(message = "La contraseña es obligatoria")
		@Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
		String contrasena) {

}
