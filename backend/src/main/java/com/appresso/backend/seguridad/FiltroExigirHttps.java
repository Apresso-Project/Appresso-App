package com.appresso.backend.seguridad;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTPS en todas las comunicaciones (entregable 8.4.1, SCRUM-100).
 * <p>
 * Con {@code appresso.seguridad.exigir-https=true} (variable {@code EXIGIR_HTTPS}, se activa en
 * producción) rechaza cualquier solicitud que no llegue cifrada, incluido el apretón de manos del
 * WebSocket ({@code ws://} en vez de {@code wss://}), con {@code 426 Upgrade Required}. No redirige:
 * una redirección de HTTP a HTTPS ya habría enviado el token y la contraseña en claro.
 * <p>
 * Detrás de un proxy o balanceador que termina TLS, el backend recibe HTTP interno: ahí
 * {@code server.forward-headers-strategy=native} hace que la solicitud cuente como segura cuando el
 * proxy indica {@code X-Forwarded-Proto: https} (solo se confía en proxies de la red interna).
 * <p>
 * Excepción: {@code /actuator/health}, porque los balanceadores revisan la salud del servicio por
 * HTTP interno. No expone datos.
 * <p>
 * No es un {@code @Component}: lo crea {@link SeguridadConfig} dentro de la cadena de seguridad.
 */
public class FiltroExigirHttps extends OncePerRequestFilter {

	static final String MENSAJE = "Esta API solo acepta conexiones cifradas (HTTPS).";
	static final int UPGRADE_REQUIRED = 426;

	private final boolean exigir;

	public FiltroExigirHttps(boolean exigir) {
		this.exigir = exigir;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest solicitud) {
		String ruta = solicitud.getRequestURI();
		return !exigir || ruta.equals("/actuator/health") || ruta.startsWith("/actuator/health/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		if (solicitud.isSecure()) {
			cadena.doFilter(solicitud, respuesta);
			return;
		}
		respuesta.setStatus(UPGRADE_REQUIRED);
		respuesta.setHeader("Upgrade", "TLS/1.2, HTTP/1.1");
		respuesta.setHeader("Connection", "Upgrade");
		respuesta.setContentType("application/json");
		respuesta.setCharacterEncoding("UTF-8");
		respuesta.getWriter().write("{\"mensaje\":\"" + MENSAJE + "\"}");
	}

}
