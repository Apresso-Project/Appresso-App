package com.appresso.backend.auth;

/**
 * Correo inexistente, contraseña incorrecta o cuenta inactiva. Los tres casos se responden igual
 * a propósito, para no revelar qué correos están registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Credenciales inválidas");
	}

}
