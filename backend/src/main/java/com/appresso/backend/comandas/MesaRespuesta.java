package com.appresso.backend.comandas;

/** Una mesa del mapa con la referencia para abrir su comanda activa. */
public record MesaRespuesta(Long id, String nombre, EstadoMesa estado, Long comandaActivaId) {

	public static MesaRespuesta de(Mesa m) {
		return new MesaRespuesta(m.getId(), m.getNombre(), m.getEstado(), null);
	}

}
