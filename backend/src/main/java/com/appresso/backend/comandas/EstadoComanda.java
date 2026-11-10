package com.appresso.backend.comandas;

/**
 * Ciclo de vida de una comanda, en este orden (RF-COMANDA-ESTADO):
 * PENDIENTE (creada, aún no enviada a cocina) → ENVIADA (el mesero la despacha) →
 * EN_PREPARACION (el cocinero la inicia, RF-25) → LISTA (el cocinero termina, RF-25) →
 * CERRADA (el cajero confirma el pago, RF-28).
 * En la pantalla de cocina solo se ven tres, con estos nombres (RF-18, RF-19): ENVIADA se muestra
 * como "Pendiente", EN_PREPARACION como "En preparación" y LISTA como "Lista". PENDIENTE (aún no
 * enviada) y CERRADA no aparecen en cocina.
 * Quién puede ejecutar cada transición se valida en el servicio, no aquí.
 */
public enum EstadoComanda {

	PENDIENTE,
	ENVIADA,
	EN_PREPARACION,
	LISTA,
	CERRADA

}
