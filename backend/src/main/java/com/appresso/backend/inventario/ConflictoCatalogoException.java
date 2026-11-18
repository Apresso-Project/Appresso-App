package com.appresso.backend.inventario;

/**
 * La operación choca con el estado actual del catálogo (nombre repetido, categoría desactivada...).
 * Responde 409. Si el choque es de un campo concreto, {@link #getCampo()} lo indica.
 */
public class ConflictoCatalogoException extends RuntimeException {

	private final String campo;

	public ConflictoCatalogoException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public ConflictoCatalogoException(String mensaje) {
		this(null, mensaje);
	}

	/** El campo del formulario al que se refiere el problema, o null si es general. */
	public String getCampo() {
		return campo;
	}

}
