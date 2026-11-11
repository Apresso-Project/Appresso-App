package com.appresso.backend.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del token de sesión ({@code appresso.jwt.*}).
 *
 * @param secret            clave con la que se firma el token (HS256); mínimo 32 bytes. Viene de la
 *                          variable de entorno {@code JWT_SECRET} y nunca se escribe en el repositorio.
 * @param expiracionMinutos minutos de vigencia del token desde que se emite
 */
@ConfigurationProperties(prefix = "appresso.jwt")
public record JwtProperties(String secret, @DefaultValue("720") long expiracionMinutos) {

	static final int LONGITUD_MINIMA_CLAVE = 32;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_CLAVE) {
			throw new IllegalStateException("Falta JWT_SECRET o es demasiado corta (mínimo "
					+ LONGITUD_MINIMA_CLAVE + " caracteres). Defínela en backend/.env; ver README, sección 2.");
		}
		if (expiracionMinutos <= 0) {
			throw new IllegalStateException("appresso.jwt.expiracion-minutos debe ser mayor que 0.");
		}
	}

}
