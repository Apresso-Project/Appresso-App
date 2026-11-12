package com.appresso.backend.usuarios;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.appresso.backend.auth.ErrorRespuesta;
import com.appresso.backend.usuarios.UsuarioService.CampoInvalidoException;
import com.appresso.backend.usuarios.UsuarioService.CorreoDuplicadoException;
import com.appresso.backend.usuarios.UsuarioService.ReglaUsuarioException;
import com.appresso.backend.usuarios.UsuarioService.UsuarioNoEncontradoException;

/**
 * Traduce los errores de la administración de usuarios a respuestas sin trazas técnicas (RNF-19),
 * con el mismo formato que el inicio de sesión: {@code { "mensaje", "campos"? }}.
 * Los 401 y 403 los responde {@code ControlAccesoInterceptor} antes de llegar aquí.
 */
@RestControllerAdvice(assignableTypes = UsuarioController.class)
public class UsuarioExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta("Revisa los datos ingresados.", campos));
	}

	@ExceptionHandler(CampoInvalidoException.class)
	public ResponseEntity<ErrorRespuesta> campoInvalido(CampoInvalidoException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorRespuesta("Revisa los datos ingresados.", Map.of(e.getCampo(), e.getMessage())));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorRespuesta> solicitudIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

	@ExceptionHandler(UsuarioNoEncontradoException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(UsuarioNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(CorreoDuplicadoException.class)
	public ResponseEntity<ErrorRespuesta> correoDuplicado(CorreoDuplicadoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorRespuesta(e.getMessage(), Map.of("correo", e.getMessage())));
	}

	@ExceptionHandler(ReglaUsuarioException.class)
	public ResponseEntity<ErrorRespuesta> reglaDeNegocio(ReglaUsuarioException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(e.getMessage()));
	}

}
