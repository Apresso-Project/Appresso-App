package com.appresso.backend.inventario;

/**
 * Estado de las existencias de un producto que sí está en el menú (RNF-17). Se muestra con texto,
 * no solo con color. Los agotados no aparecen en el menú (RF-13), por eso no hay un valor para ellos.
 */
public enum DisponibilidadProducto {

	/** Las existencias superan el umbral mínimo. */
	DISPONIBLE,

	/** Las existencias son iguales o menores al umbral mínimo, pero hay al menos una unidad. */
	POCAS_UNIDADES;

	public static DisponibilidadProducto de(int existencias, int umbralMinimo) {
		return existencias <= umbralMinimo ? POCAS_UNIDADES : DISPONIBLE;
	}

}
