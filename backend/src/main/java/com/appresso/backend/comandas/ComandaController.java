package com.appresso.backend.comandas;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * Comandas (HU 4.1.1). Quién entra a cada ruta lo decide {@code PoliticaPermisos}; el rol que puede
 * ejecutar cada transición lo decide {@link PoliticaTransiciones} (si no corresponde, 400).
 * Contrato en docs/api-comandas.md.
 */
@RestController
@RequestMapping("/api/comandas")
public class ComandaController {

	private final ComandaApiService servicio;

	public ComandaController(ComandaApiService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/{id}")
	public ComandaRespuesta obtener(@PathVariable Long id) {
		return servicio.obtener(id);
	}

	/** Avanza la comanda un estado. Transición inválida o rol que no corresponde: 400. */
	@PatchMapping("/{id}/estado")
	public ComandaRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		return servicio.cambiarEstado(id, solicitud.estado(), yo.id());
	}

}
