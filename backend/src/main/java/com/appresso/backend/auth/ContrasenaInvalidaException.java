package com.appresso.backend.auth;

/**
 * La contraseña nueva no cumple una regla que solo se puede comprobar en el servicio (por ejemplo,
 * más de 72 bytes en UTF-8, el límite de BCrypt). Responde 400 sin consumir el código.
 */
public class ContrasenaInvalidaException extends RuntimeException {

	public ContrasenaInvalidaException() {
		super("La contraseña debe tener entre 8 y 72 caracteres.");
	}

}
