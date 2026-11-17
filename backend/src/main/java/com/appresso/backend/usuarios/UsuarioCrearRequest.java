package com.appresso.backend.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cuenta. El máximo de 72 caracteres de la contraseña es el límite de BCrypt;
 * el mínimo de 8 lo fija este módulo porque la documentación no define otro.
 */
public record UsuarioCrearRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 100, message = "El nombre no puede superar 100 caracteres")
		String nombre,

		@NotBlank(message = "El correo es obligatorio")
		@Email(message = "El correo no tiene un formato válido")
		@Size(max = 150, message = "El correo no puede superar 150 caracteres")
		String correo,

		@NotBlank(message = "La contraseña es obligatoria")
		@Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
		String contrasena,

		@NotNull(message = "El rol es obligatorio")
		Rol rol) {

}
