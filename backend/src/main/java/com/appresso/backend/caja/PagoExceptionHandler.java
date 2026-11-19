package com.appresso.backend.caja;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.appresso.backend.auth.ErrorRespuesta;

import jakarta.persistence.EntityNotFoundException;

/**
 * Errores del módulo de pagos con el formato de toda la API: {@code { "mensaje", "campos"? }}.
 * Los 401 y 403 los responde la seguridad antes de llegar aquí.
 */
@RestControllerAdvice(assignableTypes = PagoController.class)
public class PagoExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta("Revisa los datos ingresados.", campos));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorRespuesta> solicitudIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(EntityNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(ConflictoPagoException.class)
	public ResponseEntity<ErrorRespuesta> conflicto(ConflictoPagoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(e.getMessage()));
	}

	/** Respaldo ante dos solicitudes simultáneas que chocan con una restricción de la base. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorRespuesta> integridad() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta("Esta comanda ya fue pagada."));
	}

}
