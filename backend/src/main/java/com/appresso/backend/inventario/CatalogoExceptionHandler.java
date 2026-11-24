package com.appresso.backend.inventario;

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
 * Traduce los errores del catálogo a respuestas sin trazas técnicas (RNF-19), con el mismo formato
 * del resto de la API: {@code { "mensaje", "campos"? }}. Los 401 y 403 los responde la seguridad
 * antes de llegar aquí.
 */
@RestControllerAdvice(assignableTypes = { CategoriaController.class, ProductoController.class, InventarioController.class })
public class CatalogoExceptionHandler {

	private static final String REVISA = "Revisa los datos ingresados.";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta(REVISA, campos));
	}

	@ExceptionHandler(CampoInvalidoCatalogoException.class)
	public ResponseEntity<ErrorRespuesta> campoInvalido(CampoInvalidoCatalogoException e) {
		return ResponseEntity.badRequest().body(new ErrorRespuesta(REVISA, Map.of(e.getCampo(), e.getMessage())));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorRespuesta> solicitudIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(EntityNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(ConflictoCatalogoException.class)
	public ResponseEntity<ErrorRespuesta> conflicto(ConflictoCatalogoException e) {
		Map<String, String> campos = e.getCampo() == null ? null : Map.of(e.getCampo(), e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(e.getMessage(), campos));
	}

	@ExceptionHandler(StockInsuficienteException.class)
	public ResponseEntity<ErrorRespuesta> stockInsuficiente(StockInsuficienteException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorRespuesta(e.getMessage(), Map.of("cantidad", e.getMessage())));
	}

	/** Respaldo ante dos solicitudes simultáneas que chocan con una restricción de la base. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorRespuesta> integridad() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorRespuesta("La operación choca con otros datos del catálogo. Revisa e intenta de nuevo."));
	}

}
