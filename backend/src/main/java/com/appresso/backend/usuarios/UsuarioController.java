package com.appresso.backend.usuarios;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.auth.ControlAccesoInterceptor;
import com.appresso.backend.auth.RequiereRol;

import jakarta.validation.Valid;

/**
 * CRUD de cuentas de usuario (RF-02). Todo el controlador es exclusivo del administrador:
 * cualquier otro rol recibe 403 y quien no tiene sesión válida recibe 401. Contrato en
 * docs/api-usuarios.md.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiereRol(Rol.ADMINISTRADOR)
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
			@RequestAttribute(ControlAccesoInterceptor.ATRIBUTO_USUARIO_ID) Long actorId) {
		UsuarioRespuesta creado = servicio.crear(solicitud, actorId);
		return ResponseEntity.created(URI.create("/api/usuarios/" + creado.id())).body(creado);
	}

	@PutMapping("/{id}")
	public UsuarioRespuesta editar(@PathVariable Long id, @Valid @RequestBody UsuarioEditarRequest solicitud,
			@RequestAttribute(ControlAccesoInterceptor.ATRIBUTO_USUARIO_ID) Long actorId) {
		return servicio.editar(id, solicitud, actorId);
	}

	@PostMapping("/{id}/desactivar")
	public UsuarioRespuesta desactivar(@PathVariable Long id,
			@RequestAttribute(ControlAccesoInterceptor.ATRIBUTO_USUARIO_ID) Long actorId) {
		return servicio.desactivar(id, actorId);
	}

	@PostMapping("/{id}/activar")
	public UsuarioRespuesta activar(@PathVariable Long id,
			@RequestAttribute(ControlAccesoInterceptor.ATRIBUTO_USUARIO_ID) Long actorId) {
		return servicio.activar(id, actorId);
	}

}
