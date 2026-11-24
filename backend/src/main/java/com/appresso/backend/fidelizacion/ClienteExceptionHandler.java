package com.appresso.backend.fidelizacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.appresso.backend.auth.CredencialesInvalidasException;
import com.appresso.backend.auth.CuentaBloqueadaException;
import com.appresso.backend.auth.ErrorRespuesta;
import com.appresso.backend.fidelizacion.ClienteService.ClienteNoEncontradoException;
import com.appresso.backend.fidelizacion.ClienteService.DatoDuplicadoException;
import com.appresso.backend.fidelizacion.ClienteService.DatoInvalidoException;

/**
 * Traduce los errores del registro y la consulta de clientes a respuestas sin trazas técnicas
 * (RNF-19), con el formato de toda la API: {@code { "mensaje", "campos"? }}. Los mensajes son los
 * de RF-50 y RF-51.
 */
@RestControllerAdvice(assignableTypes = { ClienteController.class, RegistroClienteController.class })
public class ClienteExceptionHandler {

	static final String MENSAJE_INCOMPLETO = "Completa todos los datos y acepta el tratamiento de datos personales.";

	/** Validaciones que significan "falta un dato o no aceptó el tratamiento de datos" (RF-50, criterio 2). */
	private static final Set<String> FALTANTES = Set.of("NotBlank", "NotNull", "AssertTrue");

	/** Orden del formulario: si hay varios errores de formato, el mensaje general es el del primero. */
	private static final List<String> ORDEN = List.of(
			"nombre", "apellido", "documento", "celular", "correo", "contrasena", "aceptoTratamientoDatos");

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> campos = new LinkedHashMap<>();
		boolean falta = false;
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			if (FALTANTES.contains(error.getCode())) {
				falta = true;
				campos.put(error.getField(), error.getDefaultMessage()); // "falta" gana sobre el formato
			} else {
				campos.putIfAbsent(error.getField(), error.getDefaultMessage());
			}
		}
		String mensaje = falta ? MENSAJE_INCOMPLETO
				: ORDEN.stream().filter(campos::containsKey).findFirst().map(campos::get)
						.orElse("Revisa los datos ingresados.");
		return ResponseEntity.badRequest().body(new ErrorRespuesta(mensaje, ordenar(campos)));
	}

	@ExceptionHandler(DatoInvalidoException.class)
	public ResponseEntity<ErrorRespuesta> datoInvalido(DatoInvalidoException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorRespuesta(e.getMessage(), Map.of(e.getCampo(), e.getMessage())));
	}

	@ExceptionHandler(DatoDuplicadoException.class)
	public ResponseEntity<ErrorRespuesta> duplicado(DatoDuplicadoException e) {
		Map<String, String> campos = e.getCampo() == null ? null : Map.of(e.getCampo(), e.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(e.getMessage(), campos));
	}

	@ExceptionHandler(ClienteNoEncontradoException.class)
	public ResponseEntity<ErrorRespuesta> noEncontrado(ClienteNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta(e.getMessage()));
	}

	/**
	 * La contraseña con la que el cliente confirma la eliminación no es la suya. Es 400 y no 401
	 * porque su sesión sigue siendo válida: con 401 la app la cerraría.
	 */
	@ExceptionHandler(CredencialesInvalidasException.class)
	public ResponseEntity<ErrorRespuesta> contrasenaIncorrecta() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta(ClienteService.MENSAJE_CONTRASENA_INCORRECTA,
				Map.of("contrasena", ClienteService.MENSAJE_CONTRASENA_INCORRECTA)));
	}

	@ExceptionHandler(CuentaBloqueadaException.class)
	public ResponseEntity<ErrorRespuesta> cuentaBloqueada(CuentaBloqueadaException e) {
		return ResponseEntity.status(HttpStatus.LOCKED).body(new ErrorRespuesta(e.mensajeVisible()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorRespuesta> cuerpoIlegible() {
		return ResponseEntity.badRequest().body(new ErrorRespuesta("La solicitud no tiene un formato válido."));
	}

	private static Map<String, String> ordenar(Map<String, String> campos) {
		Map<String, String> ordenados = new LinkedHashMap<>();
		ORDEN.stream().filter(campos::containsKey).forEach(c -> ordenados.put(c, campos.get(c)));
		campos.forEach(ordenados::putIfAbsent);
		return ordenados;
	}

}
