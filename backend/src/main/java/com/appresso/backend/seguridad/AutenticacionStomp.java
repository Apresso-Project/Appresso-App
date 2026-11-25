package com.appresso.backend.seguridad;

import java.util.List;
import java.util.Optional;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.tiemporeal.Destinos;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Autentica el WebSocket de tiempo real (2.3.1; antes era el TODO de {@code WebSocketConfig}).
 * <p>
 * El apretón de manos HTTP de {@code /ws/**} es público (el navegador y SockJS no pueden poner
 * cabeceras), así que la autenticación se hace en el marco {@code CONNECT} de STOMP: el cliente
 * manda {@code Authorization: Bearer <token>}, el mismo token del login. Si falta o no es válido
 * (alterado, vencido o de una cuenta desactivada), el servidor responde un marco {@code ERROR} y
 * cierra la conexión.
 * <p>
 * Se valida una sola vez, al conectar: una conexión ya abierta sigue viva aunque después se
 * desactive la cuenta o venza el token (dura 12 horas). Al reconectar se vuelve a validar.
 * Cada suscripción se autoriza según el rol de la sesión STOMP y el destino exacto.
 */
@Component
public class AutenticacionStomp implements ChannelInterceptor {

	static final String ENCABEZADO = "Authorization";
	static final String PREFIJO = "Bearer ";
	static final String MENSAJE_RECHAZO = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";

	private final JwtService jwt;
	private final UsuarioRepository usuarios;

	public AutenticacionStomp(JwtService jwt, UsuarioRepository usuarios) {
		this.jwt = jwt;
		this.usuarios = usuarios;
	}

	@Override
	public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
		StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
		if (cabeceras == null) {
			return mensaje;
		}
		if (StompCommand.SUBSCRIBE.equals(cabeceras.getCommand())) {
			if (!(cabeceras.getUser() instanceof Authentication autenticacion)
					|| !(autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario)
					|| !Destinos.puedeSuscribirse(cabeceras.getDestination(), usuario.rol())) {
				throw new MessageDeliveryException(mensaje, "Tu rol no permite suscribirte a este tópico.");
			}
			return mensaje;
		}
		if (!StompCommand.CONNECT.equals(cabeceras.getCommand())) {
			return mensaje;
		}
		Usuario usuario = usuarioDe(cabeceras.getFirstNativeHeader(ENCABEZADO))
				.orElseThrow(() -> new MessageDeliveryException(mensaje, MENSAJE_RECHAZO));

		var principal = new UsuarioAutenticado(usuario.getId(), usuario.getRol());
		var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
		cabeceras.setUser(UsernamePasswordAuthenticationToken.authenticated(principal, null, permisos));
		return mensaje;
	}

	private Optional<Usuario> usuarioDe(String encabezado) {
		if (encabezado == null || !encabezado.startsWith(PREFIJO)) {
			return Optional.empty();
		}
		return jwt.leer(encabezado.substring(PREFIJO.length()).trim())
				.map(JWTClaimsSet::getSubject)
				.flatMap(this::buscarActivo);
	}

	private Optional<Usuario> buscarActivo(String id) {
		try {
			return usuarios.findById(Long.valueOf(id)).filter(Usuario::isActivo);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

}
