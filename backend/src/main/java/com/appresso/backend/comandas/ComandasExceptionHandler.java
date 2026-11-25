package com.appresso.backend.comandas;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.appresso.backend.auth.ErrorRespuesta;

import jakarta.persistence.EntityNotFoundException;

/**
 * Traduce los errores de mesas y comandas al formato de la API ({@code { "mensaje", "campos"? }}),
 * sin trazas técnicas (RNF-19). Una transición inválida o de un rol que no corresponde es 400
 * (RF-COMANDA-ESTADO). Los 401 y 403 los responde la seguridad antes de llegar aquí.
 */
@RestControllerAdvice(basePackageClasses = ComandaController.class)
public class ComandasExceptionHandler {

	private static final String REVISA = "Revisa los datos ingresados.";

	@ExceptionHandler(TransicionInvalidaException.class)
	public ResponseEntity<ErrorRespuesta> transicionInvalida(TransicionInvalidaException e) {
		return ResponseEntity.badRequest().body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(SolicitudComandaInvalidaException.class)
	public ResponseEntity<ErrorRespuesta> solicitudInvalida(SolicitudComandaInvalidaException e) {
		Map<String, String> campos = e.getCampo() == null ? null : Map.of(e.getCampo(), e.getMessage());
		return ResponseEntity.badRequest().body(new ErrorRespuesta(e.getMessage(), campos));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta(REVISA, campos));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorRespuesta> solicitudIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(EntityNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(ConflictoComandaException.class)
	public ResponseEntity<ErrorRespuesta> conflicto(ConflictoComandaException e) {
		Map<String, String> campos = e.getCampo() == null ? null : Map.of(e.getCampo(), e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(e.getMessage(), campos));
	}

	/** Respaldo ante dos solicitudes simultáneas que chocan con una restricción de la base. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorRespuesta> integridad() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorRespuesta("La operación choca con otros datos. Revisa e intenta de nuevo."));
	}

}
