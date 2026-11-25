package com.appresso.backend.comandas;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * Mesas (RF-64). Listar: administrador y mesero. Crear: solo administrador (403 a los demás, 401
 * sin sesión), lo decide {@code PoliticaPermisos}. Contrato en docs/api-comandas.md.
 */
@RestController
@RequestMapping("/api/mesas")
public class MesaController {

	private final MesaService servicio;

	public MesaController(MesaService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<MesaRespuesta> listar() {
		return servicio.listar();
	}

	@PostMapping
	public ResponseEntity<MesaRespuesta> crear(@Valid @RequestBody MesaRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		MesaRespuesta creada = servicio.crear(solicitud, yo.id());
		return ResponseEntity.created(URI.create("/api/mesas/" + creada.id())).body(creada);
	}

}
