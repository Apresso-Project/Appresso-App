package com.appresso.backend.comandas;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comandas activas para la pantalla de cocina (RF-23). Quién entra lo decide {@code PoliticaPermisos}
 * ({@code GET /api/comandas/**}: administrador, mesero, cajero y cocinero; 403 al cliente, 401 sin
 * sesión). Contrato en docs/api-menu-cocina.md.
 */
@RestController
@RequestMapping("/api/comandas")
public class CocinaController {

	private final CocinaService servicio;

	public CocinaController(CocinaService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/cocina")
	public List<ComandaCocinaRespuesta> comandasActivas() {
		return servicio.comandasActivas();
	}

}
