package com.appresso.backend.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Verifica en el servidor quién hace cada solicitud antes de ejecutar operaciones restringidas
 * (RNF-07). Solo actúa sobre los controladores o métodos marcados con {@link RequiereRol}.
 *
 * <ul>
 * <li><b>401</b> si falta el encabezado {@code Authorization: Bearer <token>}, el token es
 * inválido o venció, o la cuenta ya no existe o está desactivada.</li>
 * <li><b>403</b> si la sesión es válida pero el rol no está autorizado.</li>
 * </ul>
 *
 * <p>El rol que se compara es el que tiene la cuenta <b>en la base de datos en este momento</b>, no
 * el que trae el token. Así, cuando el administrador cambia un rol o desactiva una cuenta (RF-02,
 * RF-03), el efecto es inmediato y no hay que esperar a que venza el token.</p>
 *
 * <p>Entregable 2.2.1. El entregable 2.3.1 (control de acceso por rol) puede reutilizar esta pieza
 * marcando el resto de controladores con {@link RequiereRol}.</p>
 */
@Component
public class ControlAccesoInterceptor implements HandlerInterceptor {

	/** Atributo de la solicitud con el id del usuario que la hace (un {@code Long}). */
	public static final String ATRIBUTO_USUARIO_ID = "appresso.usuarioId";

	static final String MENSAJE_SIN_SESION = "Tu sesión no es válida o venció. Inicia sesión de nuevo.";
	static final String MENSAJE_SIN_PERMISO = "Acceso no autorizado";

	private static final String PREFIJO = "Bearer ";

	private final JwtService jwt;
	private final UsuarioRepository usuarios;

	public ControlAccesoInterceptor(JwtService jwt, UsuarioRepository usuarios) {
		this.jwt = jwt;
		this.usuarios = usuarios;
	}

	@Override
	public boolean preHandle(HttpServletRequest solicitud, HttpServletResponse respuesta, Object manejador)
			throws IOException {
		if (!(manejador instanceof HandlerMethod metodo) || "OPTIONS".equalsIgnoreCase(solicitud.getMethod())) {
			return true;
		}
		RequiereRol requisito = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), RequiereRol.class);
		if (requisito == null) {
			requisito = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), RequiereRol.class);
		}
		if (requisito == null) {
			return true; // endpoint público o aún sin proteger
		}

		Optional<Usuario> usuario = autenticar(solicitud);
		if (usuario.isEmpty()) {
			responder(respuesta, HttpServletResponse.SC_UNAUTHORIZED, MENSAJE_SIN_SESION);
			return false;
		}
		if (!Arrays.asList(requisito.value()).contains(usuario.get().getRol())) {
			responder(respuesta, HttpServletResponse.SC_FORBIDDEN, MENSAJE_SIN_PERMISO);
			return false;
		}
		solicitud.setAttribute(ATRIBUTO_USUARIO_ID, usuario.get().getId());
		return true;
	}

	private Optional<Usuario> autenticar(HttpServletRequest solicitud) {
		String encabezado = solicitud.getHeader("Authorization");
		if (encabezado == null || !encabezado.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
			return Optional.empty();
		}
		String token = encabezado.substring(PREFIJO.length()).trim();
		return jwt.leer(token).flatMap(claims -> {
			try {
				return usuarios.findById(Long.parseLong(claims.getSubject()));
			}
			catch (NumberFormatException e) {
				return Optional.empty();
			}
		}).filter(Usuario::isActivo);
	}

	private static void responder(HttpServletResponse respuesta, int codigo, String mensaje) throws IOException {
		respuesta.setStatus(codigo);
		respuesta.setContentType("application/json;charset=UTF-8");
		// Los mensajes son constantes sin comillas ni saltos de línea: no hace falta escapar.
		respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
	}

}
