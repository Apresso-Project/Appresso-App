package com.appresso.backend.inventario;

/** Un dato enviado no es válido o apunta a algo que no existe. Responde 400 con el campo señalado. */
public class CampoInvalidoCatalogoException extends RuntimeException {

	private final String campo;

	public CampoInvalidoCatalogoException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
