package com.appresso.backend.seguridad;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Control de acceso por rol (entregable 2.3.1, SCRUM-44).
 * <p>
 * Reglas, en orden (gana la primera que coincide):
 * <ol>
 * <li>Públicos, sin token: iniciar sesión, {@code /api/health} y {@code /actuator/health}.</li>
 * <li>Por rol: cada módulo, según {@link PoliticaPermisos} (SCRUM-47).</li>
 * <li>Todo lo demás: basta con haber iniciado sesión.</li>
 * </ol>
 * Sin token válido responde 401; con token válido pero sin el rol necesario, 403. Ambos con el
 * mismo cuerpo que el resto de la API: {@code {"mensaje": "..."}}.
 * <p>
 * Para reglas más finas dentro de un controlador se puede usar
 * {@code @PreAuthorize("hasRole('MESERO')")} sobre el método (lo activa {@link EnableMethodSecurity}).
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

	static final String MENSAJE_401 = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
	static final String MENSAJE_403 = "No tienes permiso para realizar esta acción.";

	@Bean
	public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, JwtService jwt, UsuarioRepository usuarios)
			throws Exception {
		http
				// API sin sesiones ni formularios: cada solicitud trae su token.
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(reglas -> {
					// 1. Públicos
					reglas.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
							.requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health", "/actuator/health/**")
							.permitAll()
							.requestMatchers("/error").permitAll();

					// 2. Por rol: la política de permisos (SCRUM-47), en su orden.
					for (PoliticaPermisos.Regla regla : PoliticaPermisos.REGLAS) {
						var ruta = regla.metodo() == null
								? reglas.requestMatchers(regla.patron())
								: reglas.requestMatchers(regla.metodo(), regla.patron());
						if (regla.roles().isEmpty()) {
							ruta.denyAll();
						} else {
							ruta.hasAnyRole(regla.nombresDeRoles());
						}
					}

					// 3. El resto: cualquier usuario con sesión.
					reglas.anyRequest().authenticated();
				})

				.exceptionHandling(e -> e
						.authenticationEntryPoint((solicitud, respuesta, ex) ->
								escribirError(respuesta, HttpServletResponse.SC_UNAUTHORIZED, MENSAJE_401))
						.accessDeniedHandler((solicitud, respuesta, ex) ->
								escribirError(respuesta, HttpServletResponse.SC_FORBIDDEN, MENSAJE_403)))

				.addFilterBefore(new FiltroJwt(jwt, usuarios), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	/**
	 * Evita que Spring Boot cree un usuario en memoria con contraseña generada (y la imprima en la
	 * consola al arrancar). Aquí no hay inicio de sesión con usuario y contraseña de Spring: los
	 * usuarios están en la base y entran por {@code /api/auth/login}.
	 */
	@Bean
	public UserDetailsService sinUsuariosEnMemoria() {
		return nombre -> {
			throw new UsernameNotFoundException("No se usa inicio de sesión de Spring Security");
		};
	}

	private static void escribirError(HttpServletResponse respuesta, int estado, String mensaje) throws IOException {
		respuesta.setStatus(estado);
		respuesta.setContentType("application/json");
		respuesta.setCharacterEncoding("UTF-8");
		respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
	}

}
