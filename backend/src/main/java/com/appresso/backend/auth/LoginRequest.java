package com.appresso.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El máximo de 72 caracteres de la contraseña es el límite de BCrypt.
 */
public record LoginRequest(
		@NotBlank(message = "El correo es obligatorio")
		@Email(message = "El correo no tiene un formato válido")
		@Size(max = 150, message = "El correo no puede superar 150 caracteres")
		String correo,

		@NotBlank(message = "La contraseña es obligatoria")
		@Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
		String contrasena) {

}
