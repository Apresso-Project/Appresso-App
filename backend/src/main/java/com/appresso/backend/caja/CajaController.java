package com.appresso.backend.caja;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de saldos (RF-39). Solo el administrador, según {@code PoliticaPermisos}.
 * Contrato en docs/api-pagos.md.
 */
@RestController
@RequestMapping("/api/caja")
public class CajaController {

	private final SaldoCajaService saldos;

	public CajaController(SaldoCajaService saldos) {
		this.saldos = saldos;
	}

	@GetMapping("/saldos")
	public SaldosRespuesta saldos() {
		return saldos.saldos();
	}

}
