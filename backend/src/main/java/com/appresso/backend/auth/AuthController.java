package com.appresso.backend.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Endpoints públicos de la cuenta: iniciar sesión (RF-01) y recuperar la contraseña del cliente
 * (RF-65). El registro del cliente está en {@code RegistroClienteController}. Contrato en
 * {@code docs/api-auth.md}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final RecuperacionContrasenaService recuperacion;

	public AuthController(AuthService authService, RecuperacionContrasenaService recuperacion) {
		this.authService = authService;
		this.recuperacion = recuperacion;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest solicitud) {
		return authService.iniciarSesion(solicitud.identificador(), solicitud.contrasena());
	}

	/** Siempre responde lo mismo, exista o no el documento. */
	@PostMapping("/recuperar")
	public MensajeRespuesta solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequest solicitud) {
		recuperacion.solicitar(solicitud.documento());
		return new MensajeRespuesta(RecuperacionContrasenaService.MENSAJE_SOLICITUD);
	}

	@PostMapping("/restablecer")
	public MensajeRespuesta restablecer(@Valid @RequestBody RestablecerContrasenaRequest solicitud) {
		recuperacion.restablecer(solicitud.documento(), solicitud.codigo(), solicitud.contrasenaNueva());
		return new MensajeRespuesta(RecuperacionContrasenaService.MENSAJE_RESTABLECIDA);
	}

}
