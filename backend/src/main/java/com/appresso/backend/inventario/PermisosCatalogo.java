package com.appresso.backend.inventario;

import org.springframework.security.access.AccessDeniedException;

import com.appresso.backend.seguridad.UsuarioAutenticado;
import com.appresso.backend.usuarios.Rol;

/**
 * Regla fina del catálogo dentro de los controladores. La política de 2.3.1 deja leer a
 * administrador y mesero y escribir solo al administrador; lo que el mesero ve además se
 * limita aquí: solo lo que está en el menú, nunca lo desactivado.
 */
final class PermisosCatalogo {

	private PermisosCatalogo() {
	}

	static boolean esAdministrador(UsuarioAutenticado yo) {
		return yo.rol() == Rol.ADMINISTRADOR;
	}

	/** Lanza AccessDeniedException; la seguridad la convierte en el 403 de siempre. */
	static void exigirAdministrador(UsuarioAutenticado yo) {
		if (!esAdministrador(yo)) {
			throw new AccessDeniedException("Solo el administrador puede ver lo desactivado");
		}
	}

}
