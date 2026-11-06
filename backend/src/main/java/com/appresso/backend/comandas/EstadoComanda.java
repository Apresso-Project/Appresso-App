package com.appresso.backend.comandas;

/**
 * Ciclo de vida de una comanda: BORRADOR (aún no enviada), PENDIENTE, EN_PREPARACION y LISTO
 * son los estados de cocina (RF-25). El pago se modela aparte (entidad Pago).
 */
public enum EstadoComanda {

	BORRADOR,
	PENDIENTE,
	EN_PREPARACION,
	LISTO,
	CANCELADA

}
