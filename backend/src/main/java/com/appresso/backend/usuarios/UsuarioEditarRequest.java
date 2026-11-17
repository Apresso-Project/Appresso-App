package com.appresso.backend.usuarios;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para editar una cuenta. {@code contrasena} es opcional: si no viene (o es {@code null}) la
 * contraseña no cambia; si viene, reemplaza a la anterior.
 */
public record UsuarioEditarRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 100, message = "El nombre no puede superar 100 caracteres")
		String nombre,

		@NotBlank(message = "El correo es obligatorio")
		@Email(message = "El correo no tiene un formato válido")
		@Size(max = 150, message = "El correo no puede superar 150 caracteres")
		String correo,

		@Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
		String contrasena,

		@NotNull(message = "El rol es obligatorio")
		Rol rol) {

}
