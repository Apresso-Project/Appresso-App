package com.appresso.backend.comandas;

/**
 * La operación choca con el estado actual del local: mesa ya ocupada, producto no disponible, stock
 * insuficiente, comanda ya pagada. Responde 409. {@link #getCampo()} señala el campo si aplica.
 */
public class ConflictoComandaException extends IllegalStateException {

	private final String campo;

	public ConflictoComandaException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public ConflictoComandaException(String mensaje) {
		this(null, mensaje);
	}

	public String getCampo() {
		return campo;
	}

}
