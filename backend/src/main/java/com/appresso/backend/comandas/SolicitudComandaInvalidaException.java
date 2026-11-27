package com.appresso.backend.comandas;

/** Un dato de la solicitud no es válido (sin productos, cantidad < 1, descuento no autorizado...). Responde 400. */
public class SolicitudComandaInvalidaException extends IllegalArgumentException {

	private final String campo;

	public SolicitudComandaInvalidaException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
