package com.appresso.backend.fidelizacion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos con los que el propio cliente crea su cuenta desde la app (RF-50). Inicia sesión con su
 * documento y la contraseña; el correo sirve para recuperar la contraseña (RF-65). Es obligatorio
 * aceptar el tratamiento de los datos personales (Ley 1581 de 2012).
 * <p>
 * El documento admite puntos, espacios y guiones: se normalizan antes de validarlo (ver
 * {@link DocumentoIdentidad}). El máximo de 72 caracteres de la contraseña es el límite de BCrypt.
 */
public record RegistroClienteRequest(
		@NotBlank(message = "Ingresa tu nombre.")
		@Size(max = 80, message = "El nombre no puede superar 80 caracteres.")
		String nombre,

		@NotBlank(message = "Ingresa tu apellido.")
		@Size(max = 80, message = "El apellido no puede superar 80 caracteres.")
		String apellido,

		@NotBlank(message = "Ingresa tu número de documento.")
		@Size(max = 30, message = DocumentoIdentidad.MENSAJE_INVALIDO)
		String documento,

		@NotBlank(message = "Ingresa tu número de celular.")
		@Pattern(regexp = "\\+?[0-9 ]{7,20}", message = "Ingresa un número de celular válido.")
		String celular,

		@NotBlank(message = "Ingresa tu correo electrónico.")
		@Size(max = 150, message = RegistroClienteRequest.MENSAJE_CORREO)
		@Email(regexp = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+", message = RegistroClienteRequest.MENSAJE_CORREO)
		String correo,

		@NotBlank(message = "Ingresa una contraseña.")
		@Size(min = 8, max = 72, message = RegistroClienteRequest.MENSAJE_CONTRASENA)
		String contrasena,

		@NotNull(message = RegistroClienteRequest.MENSAJE_CONSENTIMIENTO)
		@AssertTrue(message = RegistroClienteRequest.MENSAJE_CONSENTIMIENTO)
		Boolean aceptoTratamientoDatos) {

	public static final String MENSAJE_CORREO = "Ingresa un correo electrónico válido.";
	public static final String MENSAJE_CONTRASENA = "La contraseña debe tener entre 8 y 72 caracteres.";
	public static final String MENSAJE_CONSENTIMIENTO = "Debes aceptar el tratamiento de datos personales.";

	@Override
	public String toString() {
		return "RegistroClienteRequest(documento=" + documento + ", contrasena=***)";
	}

}
