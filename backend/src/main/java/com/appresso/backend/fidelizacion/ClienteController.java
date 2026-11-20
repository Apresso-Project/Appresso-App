package com.appresso.backend.fidelizacion;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

/**
 * Consulta de clientes. Quién puede usar cada ruta lo decide {@code PoliticaPermisos}: buscar por
 * documento, el mesero y el administrador (RF-51, RF-57); {@code /yo}, solo el cliente (RF-55).
 * Contrato en {@code docs/api-clientes.md}.
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

	@GetMapping("/{documento}")
	public ClienteRespuesta buscarPorDocumento(@PathVariable String documento) {
		return servicio.buscarPorDocumento(documento);
	}

}
