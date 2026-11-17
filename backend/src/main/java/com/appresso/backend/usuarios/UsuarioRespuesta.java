package com.appresso.backend.usuarios;

import java.time.Instant;

/** Lo que el servidor devuelve de una cuenta. Nunca incluye la contraseña ni su hash. */
public record UsuarioRespuesta(Long id, String nombre, String correo, Rol rol, boolean activo, Instant fechaCreacion) {

	public static UsuarioRespuesta de(Usuario u) {
		return new UsuarioRespuesta(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), u.isActivo(),
				u.getFechaCreacion());
	}

}
