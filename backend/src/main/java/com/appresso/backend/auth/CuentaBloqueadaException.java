package com.appresso.backend.auth;

public class CuentaBloqueadaException extends RuntimeException {

	private final long minutosRestantes;

	public CuentaBloqueadaException(long minutosRestantes) {
		super("Cuenta bloqueada temporalmente");
		this.minutosRestantes = minutosRestantes;
	}

	public long getMinutosRestantes() {
		return minutosRestantes;
	}

	/** El texto que ve la persona. */
	public String mensajeVisible() {
		return "Cuenta bloqueada temporalmente por demasiados intentos fallidos. "
				+ "Intenta de nuevo en " + minutosRestantes + (minutosRestantes == 1 ? " minuto." : " minutos.");
	}

}
