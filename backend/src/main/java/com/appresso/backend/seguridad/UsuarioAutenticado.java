package com.appresso.backend.seguridad;

import com.appresso.backend.usuarios.Rol;

/**
 * Quién hace la solicitud, ya verificado por {@link FiltroJwt}. Es el "principal" de Spring
 * Security: un controlador lo recibe con {@code @AuthenticationPrincipal UsuarioAutenticado yo}.
 */
public record UsuarioAutenticado(Long id, Rol rol) {
}
