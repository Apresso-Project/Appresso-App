package com.appresso.backend.fidelizacion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Registro del cliente desde la app (RF-50). Es público: el cliente todavía no tiene sesión. Está
 * bajo {@code /api/auth} junto al inicio de sesión y la recuperación de contraseña. Contrato en
 * {@code docs/api-clientes.md}.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistroClienteController {

	private final ClienteService servicio;

	public RegistroClienteController(ClienteService servicio) {
		this.servicio = servicio;
	}

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public RegistroClienteRespuesta registrar(@Valid @RequestBody RegistroClienteRequest solicitud) {
		return servicio.registrar(solicitud);
	}

}
