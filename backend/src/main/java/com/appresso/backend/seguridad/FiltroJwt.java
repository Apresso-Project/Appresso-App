package com.appresso.backend.seguridad;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.nimbusds.jwt.JWTClaimsSet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee el token de cada solicitud (SCRUM-43).
 * <p>
 * Si llega {@code Authorization: Bearer <token>} con un token válido de un usuario que existe y
 * está activo, marca la solicitud como autenticada con el rol de ese usuario. Si no hay token o no
 * es válido, no hace nada: la solicitud sigue como anónima y {@link SeguridadConfig} decide si se
 * permite (endpoints públicos) o responde 401.
 * <p>
 * El rol se toma de la base de datos y no del token: si el administrador desactiva una cuenta o le
 * cambia el rol (2.2.1), el cambio aplica en la siguiente solicitud, sin esperar a que el token
 * venza (dura 12 horas).
 * <p>
 * No es un {@code @Component} a propósito: Spring Boot registraría también el filtro fuera de la
 * cadena de seguridad. Lo crea {@link SeguridadConfig}.
 */
public class FiltroJwt extends OncePerRequestFilter {

	private static final String PREFIJO = "Bearer ";

	private final JwtService jwt;
	private final UsuarioRepository usuarios;

	public FiltroJwt(JwtService jwt, UsuarioRepository usuarios) {
		this.jwt = jwt;
		this.usuarios = usuarios;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		String encabezado = solicitud.getHeader(HttpHeaders.AUTHORIZATION);
		if (encabezado != null && encabezado.startsWith(PREFIJO)) {
			jwt.leer(encabezado.substring(PREFIJO.length()).trim())
					.flatMap(this::usuarioActivo)
					.ifPresent(this::autenticar);
		}
		cadena.doFilter(solicitud, respuesta);
	}

	private Optional<Usuario> usuarioActivo(JWTClaimsSet claims) {
		try {
			return usuarios.findById(Long.valueOf(claims.getSubject())).filter(Usuario::isActivo);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private void autenticar(Usuario usuario) {
		var principal = new UsuarioAutenticado(usuario.getId(), usuario.getRol());
		var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, permisos));
		SecurityContextHolder.setContext(contexto);
	}

}
