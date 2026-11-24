package com.appresso.backend.auth;

/**
 * Cuenta inexistente (correo o documento), contraseña incorrecta, cuenta inactiva o identificador
 * que no corresponde al rol (un cliente con su correo, el personal con un documento). Todos se
 * responden igual a propósito, para no revelar qué cuentas están registradas.
 */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Credenciales inválidas");
	}

}
