package com.appresso.backend.tiemporeal;

import com.appresso.backend.usuarios.Rol;

/**
 * Direcciones de la capa de tiempo real. Los clientes (Android) usan estas
 * mismas cadenas; si cambian aquí, hay que avisar al equipo Android.
 */
public final class Destinos {

	/** Punto de conexión. SockJS en /ws; WebSocket puro (para Android) en /ws/websocket. */
	public static final String ENDPOINT = "/ws";

	/** Prefijo de los tópicos a los que se suscriben los clientes. */
	public static final String PREFIJO_TOPICOS = "/topic";

	/** Eventos de comandas: creación, modificación y cambios de estado. */
	public static final String COMANDAS = PREFIJO_TOPICOS + "/comandas";

	/** Eventos del catálogo: productos y existencias. */
	public static final String CATALOGO = PREFIJO_TOPICOS + "/catalogo";

	/** Permisos de lectura de menú y comandas, sin destinos con comodines. */
	public static boolean puedeSuscribirse(String destino, Rol rol) {
		return switch (rol) {
			case ADMINISTRADOR, MESERO -> COMANDAS.equals(destino) || CATALOGO.equals(destino);
			case CAJERO, COCINERO -> COMANDAS.equals(destino);
			case CLIENTE -> CATALOGO.equals(destino);
		};
	}

	private Destinos() {
	}

}
