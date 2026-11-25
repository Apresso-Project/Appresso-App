package com.appresso.backend.comandas;

/**
 * La comanda no puede pasar a ese estado: se salta un paso, retrocede, la hace un rol que no
 * corresponde o no cumple la condición (p. ej. enviarla vacía). Responde 400 (RF-COMANDA-ESTADO).
 */
public class TransicionInvalidaException extends IllegalStateException {

	public TransicionInvalidaException(String mensaje) {
		super(mensaje);
	}

}
