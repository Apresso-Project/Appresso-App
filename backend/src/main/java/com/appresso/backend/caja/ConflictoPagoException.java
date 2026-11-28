package com.appresso.backend.caja;

/**
 * El pago no se puede registrar por el estado de la comanda o del pago (responde 409): ya está
 * pagada, fue cancelada o todavía no está lista.
 */
public class ConflictoPagoException extends RuntimeException {

	public ConflictoPagoException(String mensaje) {
		super(mensaje);
	}

}
