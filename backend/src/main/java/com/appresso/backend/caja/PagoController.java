package com.appresso.backend.caja;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appresso.backend.seguridad.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * Confirmación de pagos (RF-28 a RF-30, RF-41, RF-43). Quién puede usar cada ruta lo define
 * {@code PoliticaPermisos} (efectivo: cajero y administrador; transferencia: también mesero) y
 * {@link PagoService} lo vuelve a validar. Contrato en docs/api-pagos.md.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

	private final PagoService servicio;

	public PagoController(PagoService servicio) {
		this.servicio = servicio;
	}

	@PostMapping("/efectivo")
	public ResponseEntity<PagoRespuesta> efectivo(@Valid @RequestBody PagoSolicitud solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		return responder(servicio.registrar(solicitud.comandaId(), MedioPago.EFECTIVO,
				solicitud.identificadorOperacion(), yo.id()));
	}

	@PostMapping("/transferencia")
	public ResponseEntity<PagoRespuesta> transferencia(@Valid @RequestBody PagoSolicitud solicitud,
			@AuthenticationPrincipal UsuarioAutenticado yo) {
		return responder(servicio.registrar(solicitud.comandaId(), MedioPago.TRANSFERENCIA,
				solicitud.identificadorOperacion(), yo.id()));
	}

	/** 201 si se registró; 200 si era el reintento de un pago ya registrado (no se suma otra vez). */
	private static ResponseEntity<PagoRespuesta> responder(PagoService.Resultado resultado) {
		return resultado.repetido() ? ResponseEntity.ok(resultado.pago())
				: ResponseEntity.status(201).body(resultado.pago());
	}

}
