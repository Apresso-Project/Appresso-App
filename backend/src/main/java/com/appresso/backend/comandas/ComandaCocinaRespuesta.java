package com.appresso.backend.comandas;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Una comanda activa tal como la ve la pantalla de cocina (RF-23, RF-24). {@code estado} es el valor
 * interno y {@code estadoCocina} el nombre que se muestra: ENVIADA → «Pendiente», EN_PREPARACION →
 * «En preparación», LISTA → «Lista».
 */
public record ComandaCocinaRespuesta(
		Long id,
		Long mesaId,
		String mesa,
		EstadoComanda estado,
		String estadoCocina,
		Instant fechaEnvio,
		List<ItemCocinaRespuesta> items) {

	static ComandaCocinaRespuesta de(Comanda c) {
		List<ItemCocinaRespuesta> items = c.getDetalles().stream()
				.sorted(Comparator.comparing(DetalleComanda::getId))
				.map(ItemCocinaRespuesta::de)
				.toList();
		return new ComandaCocinaRespuesta(c.getId(), c.getMesa().getId(), c.getMesa().getNombre(), c.getEstado(),
				nombreEnCocina(c.getEstado()), c.getFechaEnvio(), items);
	}

	static String nombreEnCocina(EstadoComanda estado) {
		return switch (estado) {
			case ENVIADA -> "Pendiente";
			case EN_PREPARACION -> "En preparación";
			case LISTA -> "Lista";
			default -> throw new IllegalArgumentException("El estado " + estado + " no se muestra en cocina");
		};
	}

}
