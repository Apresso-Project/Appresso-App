package com.appresso.backend.fidelizacion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.appresso.backend.auth.ErrorRespuesta;
import com.appresso.backend.fidelizacion.ClienteService.ClienteNoEncontradoException;
import com.appresso.backend.fidelizacion.ClienteService.ConsentimientoRequeridoException;
import com.appresso.backend.fidelizacion.ClienteService.DocumentoDuplicadoException;

@RestControllerAdvice(assignableTypes = ClienteController.class)
public class ClienteExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta("Revisa los datos ingresados.", campos));
	}

	@ExceptionHandler(ConsentimientoRequeridoException.class)
	public ResponseEntity<ErrorRespuesta> consentimientoRequerido(ConsentimientoRequeridoException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorRespuesta(e.getMessage(), Map.of("aceptoTratamientoDatos", e.getMessage())));
	}

	@ExceptionHandler(DocumentoDuplicadoException.class)
	public ResponseEntity<ErrorRespuesta> documentoDuplicado(DocumentoDuplicadoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorRespuesta(e.getMessage(), Map.of("documento", e.getMessage())));
	}

	@ExceptionHandler(ClienteNoEncontradoException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(ClienteNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

}
