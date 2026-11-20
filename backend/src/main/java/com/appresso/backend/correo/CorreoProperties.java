package com.appresso.backend.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servidor SMTP para los correos de la app ({@code appresso.correo.*} en application.properties).
 * Los valores reales van en {@code backend/.env} o en las variables del servidor. Con {@code host}
 * vacío no se envía ningún correo (ver {@link EnvioCorreoSmtp}).
 */
@ConfigurationProperties(prefix = "appresso.correo")
public record CorreoProperties(String host, Integer puerto, String usuario, String contrasena, String remitente) {

	public boolean configurado() {
		return host != null && !host.isBlank();
	}

}
