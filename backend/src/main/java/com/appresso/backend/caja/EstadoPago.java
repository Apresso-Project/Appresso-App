package com.appresso.backend.caja;

/**
 * Un pago por transferencia nace PENDIENTE y pasa a CONFIRMADO al verificar el comprobante (RF-30).
 */
public enum EstadoPago {

	PENDIENTE,
	CONFIRMADO

}
