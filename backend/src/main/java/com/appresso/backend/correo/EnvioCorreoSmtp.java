package com.appresso.backend.correo;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Envía los correos por SMTP con la configuración de {@link CorreoProperties} (STARTTLS).
 * <p>
 * Sin servidor configurado no se envía nada. Con el perfil {@code dev} el código se escribe en la
 * consola del backend, para poder probar la recuperación en local; en cualquier otro perfil solo se
 * registra un aviso, porque el código es un secreto y no debe quedar en los registros.
 */
@Component
public class EnvioCorreoSmtp implements EnvioCorreo {

	private static final Logger log = LoggerFactory.getLogger(EnvioCorreoSmtp.class);

	private final CorreoProperties propiedades;
	private final boolean perfilDev;
	private final JavaMailSenderImpl remitente;

	public EnvioCorreoSmtp(CorreoProperties propiedades, Environment entorno) {
		this.propiedades = propiedades;
		this.perfilDev = entorno.acceptsProfiles(Profiles.of("dev"));
		this.remitente = propiedades.configurado() ? crearRemitente(propiedades) : null;
	}

	@Override
	public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, long minutosVigencia) {
		if (remitente == null) {
			if (perfilDev) {
				log.info("[dev] Sin servidor de correo. Código de recuperación para {}: {}", destinatario, codigo);
			} else {
				log.warn("No hay servidor de correo configurado (CORREO_HOST): no se envió el código de recuperación.");
			}
			return;
		}
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(propiedades.remitente());
		mensaje.setTo(destinatario);
		mensaje.setSubject("Appresso: tu código para cambiar la contraseña");
		mensaje.setText("Hola, " + nombre + ".\n\n"
				+ "Tu código para cambiar la contraseña de Appresso es: " + codigo + "\n\n"
				+ "Vence en " + minutosVigencia + " minutos y solo se puede usar una vez. "
				+ "Si no lo pediste, ignora este correo: tu contraseña no cambia.\n");
		try {
			remitente.send(mensaje);
		} catch (MailException e) {
			// No se propaga: la respuesta al cliente no debe revelar si el envío falló.
			log.error("No se pudo enviar el código de recuperación: {}", e.getMessage());
		}
	}

	private static JavaMailSenderImpl crearRemitente(CorreoProperties p) {
		JavaMailSenderImpl remitente = new JavaMailSenderImpl();
		remitente.setHost(p.host());
		remitente.setPort(p.puerto() != null ? p.puerto() : 587);
		if (p.usuario() != null && !p.usuario().isBlank()) {
			remitente.setUsername(p.usuario());
			remitente.setPassword(p.contrasena());
		}
		remitente.setDefaultEncoding(StandardCharsets.UTF_8.name());
		Properties extra = remitente.getJavaMailProperties();
		extra.put("mail.smtp.auth", String.valueOf(remitente.getUsername() != null));
		extra.put("mail.smtp.starttls.enable", "true");
		extra.put("mail.smtp.connectiontimeout", "10000");
		extra.put("mail.smtp.timeout", "10000");
		extra.put("mail.smtp.writetimeout", "10000");
		return remitente;
	}

}
