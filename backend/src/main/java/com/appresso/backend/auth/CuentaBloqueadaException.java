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

}
