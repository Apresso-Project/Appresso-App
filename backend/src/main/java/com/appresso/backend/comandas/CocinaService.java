package com.appresso.backend.comandas;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de la pantalla de cocina (RF-23, RF-24): las comandas confirmadas que aún no se cierran,
 * de la más antigua a la más reciente según su hora de envío. Una comanda sin enviar (PENDIENTE) no
 * aparece; una cerrada, tampoco.
 *
 * <p>La pantalla se mantiene al día con los eventos {@code COMANDA_*} de {@code /topic/comandas}
 * (RF-26): al recibir cualquiera vuelve a pedir esta lista. Los eventos los publica
 * {@link ComandaService} al hacer commit; ver docs/api-menu-cocina.md.</p>
 */
@Service
public class CocinaService {

	/** Los tres estados que cocina muestra (RF-COMANDA-ESTADO). */
	static final Set<EstadoComanda> ESTADOS_EN_COCINA =
			EnumSet.of(EstadoComanda.ENVIADA, EstadoComanda.EN_PREPARACION, EstadoComanda.LISTA);

	private final ComandaRepository comandas;

	public CocinaService(ComandaRepository comandas) {
		this.comandas = comandas;
	}

	@Transactional(readOnly = true)
	public List<ComandaCocinaRespuesta> comandasActivas() {
		return comandas.findParaCocina(ESTADOS_EN_COCINA).stream()
				.map(ComandaCocinaRespuesta::de)
				.toList();
	}

}
