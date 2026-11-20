package com.appresso.backend.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del inicio de sesión y de la recuperación de contraseña a respuestas sin
 * trazas técnicas (RNF-19). Aplica solo a {@link AuthController}.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

	@ExceptionHandler(CredencialesInvalidasException.class)
	public ResponseEntity<ErrorRespuesta> credencialesInvalidas(CredencialesInvalidasException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorRespuesta(e.getMessage()));
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<ErrorRespuesta> cuentaBloqueada(CuentaBloqueadaException e) {
		long minutos = e.getMinutosRestantes();
		String mensaje = "Cuenta bloqueada temporalmente por demasiados intentos fallidos. "
				+ "Intenta de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos.");
		return ResponseEntity.status(HttpStatus.LOCKED).body(new ErrorRespuesta(mensaje));
	}

	@ExceptionHandler(CodigoInvalidoException.class)
	public ResponseEntity<ErrorRespuesta> codigoInvalido(CodigoInvalidoException e) {
		return ResponseEntity.badRequest().body(new ErrorRespuesta(e.getMessage(), Map.of("codigo", e.getMessage())));
	}

	@ExceptionHandler(ContrasenaInvalidaException.class)
	public ResponseEntity<ErrorRespuesta> contrasenaInvalida(ContrasenaInvalidaException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorRespuesta(e.getMessage(), Map.of("contrasenaNueva", e.getMessage())));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorRespuesta("Revisa los datos ingresados.", campos));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorRespuesta> cuerpoIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

}
