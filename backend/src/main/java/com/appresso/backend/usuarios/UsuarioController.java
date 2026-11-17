package com.appresso.backend.usuarios;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * CRUD de cuentas de usuario (RF-02). Todo el controlador es del administrador: la regla
 * {@code /api/usuarios/**} de {@code PoliticaPermisos} responde 403 a cualquier otro rol y 401 a
 * quien no tiene sesión válida (2.3.1). Contrato en docs/api-usuarios.md.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService servicio;

	public UsuarioController(UsuarioService servicio) {
		this.servicio = servicio;
	}

	@GetMapping
	public List<UsuarioRespuesta> listar() {
		return servicio.listar();
	}

	@GetMapping("/{id}")
	public UsuarioRespuesta obtener(@PathVariable Long id) {
		return servicio.obtener(id);
	}

	@PostMapping
	public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody UsuarioCrearRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		UsuarioRespuesta creado = servicio.crear(solicitud, actor.id());
		return ResponseEntity.created(URI.create("/api/usuarios/" + creado.id())).body(creado);
	}

	@PutMapping("/{id}")
	public UsuarioRespuesta editar(@PathVariable Long id, @Valid @RequestBody UsuarioEditarRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return servicio.editar(id, solicitud, actor.id());
	}

	@PostMapping("/{id}/desactivar")
	public UsuarioRespuesta desactivar(@PathVariable Long id,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return servicio.desactivar(id, actor.id());
	}

	@PostMapping("/{id}/activar")
	public UsuarioRespuesta activar(@PathVariable Long id,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		return servicio.activar(id, actor.id());
	}

}
