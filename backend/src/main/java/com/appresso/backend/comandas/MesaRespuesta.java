package com.appresso.backend.comandas;

/** Una mesa del mapa de mesas: libre ({@code DISPONIBLE}) u {@code OCUPADA}. */
public record MesaRespuesta(Long id, String nombre, EstadoMesa estado) {

	public static MesaRespuesta de(Mesa m) {
		return new MesaRespuesta(m.getId(), m.getNombre(), m.getEstado());
	}

}
