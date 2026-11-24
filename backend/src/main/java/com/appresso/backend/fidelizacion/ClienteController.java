package com.appresso.backend.fidelizacion;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

/**
 * Consulta de clientes y eliminación de los datos propios. Quién puede usar cada ruta lo decide
 * {@code PoliticaPermisos}: buscar por documento, el mesero y el administrador (RF-51, RF-57);
 * {@code /yo}, solo el cliente (RF-55 y Ley 1581); el historial de consentimiento, solo el
 * administrador. Contrato en {@code docs/api-clientes.md}.
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService servicio;

	public ClienteController(ClienteService servicio) {
		this.servicio = servicio;
	}

	@GetMapping("/yo")
	public ClienteRespuesta miCuenta(@AuthenticationPrincipal UsuarioAutenticado actor) {
		return servicio.miCuenta(actor.id());
	}

	/** El cliente elimina sus propios datos personales; confirma con su contraseña (Ley 1581). */
	@PostMapping("/yo/eliminacion")
	public ResponseEntity<Void> eliminarMisDatos(@RequestBody EliminarMisDatosRequest solicitud,
			@AuthenticationPrincipal UsuarioAutenticado actor) {
		servicio.eliminarMisDatos(actor.id(), solicitud.contrasena());
		return ResponseEntity.noContent().build();
	}

	/** Trazabilidad del consentimiento (Ley 1581): cuándo se otorgó y cuándo se suprimieron los datos. */
	@GetMapping("/{id}/consentimiento")
	public List<ConsentimientoRespuesta> historialConsentimiento(@PathVariable Long id) {
		return servicio.historialConsentimiento(id);
	}

	@GetMapping("/{documento}")
	public ClienteRespuesta buscarPorDocumento(@PathVariable String documento) {
		return servicio.buscarPorDocumento(documento);
	}

}
