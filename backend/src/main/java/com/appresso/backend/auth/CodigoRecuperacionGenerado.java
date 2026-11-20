package com.appresso.backend.auth;

/**
 * Se publicó un código de recuperación nuevo y hay que enviarlo. El correo sale después de guardar
 * el código y en otro hilo ({@link EnvioCodigoRecuperacion}), así la respuesta tarda lo mismo exista
 * o no la cuenta.
 */
public record CodigoRecuperacionGenerado(String correo, String nombre, String codigo, long minutosVigencia) {

	@Override
	public String toString() {
		return "CodigoRecuperacionGenerado(codigo=***)";
	}

}
