package com.appresso.backend.fidelizacion;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * Endpoints para el registro de clientes, consulta y solicitud de eliminación de datos personales
 * conforme a la Ley 1581 de 2012.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService servicio;

	public ClienteController(ClienteService servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	public ResponseEntity<ClienteRespuesta> registrar(
			@Valid @RequestBody ClienteCrearRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		Long actorId = actor != null ? actor.id() : null;
		ClienteRespuesta creado = servicio.registrar(solicitud, actorId);
		return ResponseEntity.created(URI.create("/api/clientes/" + creado.documento())).body(creado);
	}

	@GetMapping
	public List<ClienteRespuesta> listar() {
		return servicio.listar();
	}

	/** Trazabilidad del consentimiento (Ley 1581): cuándo se otorgó, quién lo atendió y si se suprimió. */
	@GetMapping("/{id}/consentimiento")
	public List<ConsentimientoRespuesta> historialConsentimiento(@PathVariable Long id) {
		return servicio.historialConsentimiento(id);
	}

	@GetMapping("/{documento}")
	public ClienteRespuesta buscarPorDocumento(@PathVariable String documento) {
		return servicio.buscarPorDocumento(documento);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> eliminar(
			@PathVariable Long id,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		Long actorId = actor != null ? actor.id() : null;
		servicio.suprimirDatosPersonales(id, "Eliminación solicitada por administración", actorId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/solicitar-eliminacion")
	public ResponseEntity<Void> solicitarEliminacion(
			@PathVariable Long id,
			@Valid @RequestBody(required = false) SolicitudEliminacionRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		Long actorId = actor != null ? actor.id() : null;
		String motivo = solicitud != null ? solicitud.motivo() : null;
		servicio.suprimirDatosPersonales(id, motivo, actorId);
		return ResponseEntity.noContent().build();
	}

}
