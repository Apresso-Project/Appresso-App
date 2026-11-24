package com.appresso.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedir un código de recuperación de contraseña (RF-65). Solo para cuentas de cliente. */
public record SolicitarCodigoRequest(
		@NotBlank(message = "Ingresa tu número de documento.")
		@Size(max = 30, message = "El documento debe tener entre 5 y 20 letras o números.")
		String documento) {

}
