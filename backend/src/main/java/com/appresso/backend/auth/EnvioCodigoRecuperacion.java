package com.appresso.backend.auth;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.appresso.backend.correo.EnvioCorreo;

/** Envía el código de recuperación cuando ya quedó guardado (después del commit), en otro hilo. */
@Component
public class EnvioCodigoRecuperacion {

	private final EnvioCorreo correo;

	public EnvioCodigoRecuperacion(EnvioCorreo correo) {
		this.correo = correo;
	}

	@Async
	@TransactionalEventListener
	public void enviar(CodigoRecuperacionGenerado evento) {
		correo.enviarCodigoRecuperacion(evento.correo(), evento.nombre(), evento.codigo(), evento.minutosVigencia());
	}

}
