package com.appresso.backend.inventario;

/** El movimiento solicitado dejaría existencias negativas. */
public class StockInsuficienteException extends IllegalStateException {

	public StockInsuficienteException(String mensaje) {
		super(mensaje);
	}
}
