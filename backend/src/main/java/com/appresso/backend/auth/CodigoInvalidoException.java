package com.appresso.backend.auth;

/**
 * El código de recuperación es incorrecto, ya se usó, venció, agotó sus intentos o el documento no
 * tiene cuenta de cliente. Todos se responden igual, para no revelar qué documentos existen.
 */
public class CodigoInvalidoException extends RuntimeException {

	public CodigoInvalidoException() {
		super("El código no es válido o venció. Solicita uno nuevo.");
	}

}
