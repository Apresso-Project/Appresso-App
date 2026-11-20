package com.appresso.backend.correo;

/**
 * Envío de los correos de la app. Es una interfaz para que las pruebas puedan reemplazarla y ver
 * qué se habría enviado sin un servidor de correo.
 */
public interface EnvioCorreo {

	/** Envía el código de recuperación de contraseña (RF-65). No lanza excepciones: si falla, lo registra. */
	void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, long minutosVigencia);

}
