package com.appresso.backend.tiemporeal;

/**
 * Qué ocurrió. Cada tipo viaja siempre por el mismo tópico.
 */
public enum TipoEvento {

	/** Se abrió una comanda (estado PENDIENTE: todavía no se envía a cocina). */
	COMANDA_CREADA(Destinos.COMANDAS),
	/** Se agregaron productos a una comanda ya enviada (RF-20, RF-21). */
	COMANDA_MODIFICADA(Destinos.COMANDAS),
	/** La comanda cambió de estado: enviada a cocina, en preparación, lista o cerrada (RF-18, RF-25, RF-28). */
	COMANDA_ESTADO_CAMBIADO(Destinos.COMANDAS),

	/** Se creó una categoría del catálogo (3.1.1). */
	CATEGORIA_CREADA(Destinos.CATALOGO),
	/**
	 * Una categoría cambió de nombre o de estado (3.1.1). Si quedó desactivada, el cliente debe
	 * ocultar todos sus productos del menú; si se reactivó, volver a mostrarlos.
	 */
	CATEGORIA_ACTUALIZADA(Destinos.CATALOGO),

	/** Se creó un producto. */
	PRODUCTO_CREADO(Destinos.CATALOGO),
	/** Cambió el nombre, la descripción, el precio, la categoría, la imagen, el umbral mínimo o si está activo. */
	PRODUCTO_ACTUALIZADO(Destinos.CATALOGO),
	/** Cambió la cantidad disponible (venta, ajuste manual) (RF-08). */
	EXISTENCIAS_ACTUALIZADAS(Destinos.CATALOGO);

	private final String destino;

	TipoEvento(String destino) {
		this.destino = destino;
	}

	public String destino() {
		return destino;
	}

}
