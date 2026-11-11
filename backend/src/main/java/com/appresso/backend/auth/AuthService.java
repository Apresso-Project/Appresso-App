package com.appresso.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Inicio de sesión (RF-01) con bloqueo temporal tras demasiados intentos fallidos (RNF-26):
 * a los 5 fallos consecutivos la cuenta queda bloqueada 15 minutos, y el sexto intento ya es
 * rechazado aunque la contraseña sea correcta.
 */
@Service
public class AuthService {

	static final int MAX_INTENTOS_FALLIDOS = 5;
	static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

	private final UsuarioRepository usuarios;
	private final PasswordEncoder codificador;
	private final JwtService jwt;
	private final Clock reloj;

	/**
	 * Se compara contra este hash cuando el correo no existe o la cuenta está inactiva, para que
	 * esas respuestas tarden lo mismo que una contraseña incorrecta y no delaten qué correos existen.
	 */
	private final String hashFicticio;

	public AuthService(UsuarioRepository usuarios, PasswordEncoder codificador, JwtService jwt, Clock reloj) {
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.jwt = jwt;
		this.reloj = reloj;
		this.hashFicticio = codificador.encode("contrasena-ficticia");
	}

	/**
	 * Los fallos de autenticación son excepciones, pero el contador de intentos debe guardarse
	 * igual: por eso la transacción no se revierte con {@link CredencialesInvalidasException}.
	 */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public LoginResponse iniciarSesion(String correo, String contrasena) {
		Instant ahora = reloj.instant();

		Usuario usuario = usuarios.buscarPorCorreoBloqueandoFila(correo.trim()).orElse(null);
		if (usuario == null || !usuario.isActivo()) {
			coincide(contrasena, hashFicticio);
			throw new CredencialesInvalidasException();
		}

		Instant bloqueadoHasta = usuario.getBloqueadoHasta();
		if (bloqueadoHasta != null) {
			if (bloqueadoHasta.isAfter(ahora)) {
				throw new CuentaBloqueadaException(minutosRestantes(ahora, bloqueadoHasta));
			}
			// El bloqueo ya venció: se empieza de cero.
			usuario.setBloqueadoHasta(null);
			usuario.setIntentosFallidos(0);
		}

		if (!coincide(contrasena, usuario.getContrasenaHash())) {
			int intentos = usuario.getIntentosFallidos() + 1;
			usuario.setIntentosFallidos(intentos);
			if (intentos >= MAX_INTENTOS_FALLIDOS) {
				usuario.setBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
			}
			usuarios.save(usuario);
			throw new CredencialesInvalidasException();
		}

		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);
		return new LoginResponse(jwt.generar(usuario), usuario.getRol(), usuario.getNombre());
	}

	private boolean coincide(String contrasena, String hash) {
		try {
			return codificador.matches(contrasena, hash);
		} catch (IllegalArgumentException e) {
			// BCrypt rechaza contraseñas de más de 72 bytes: ninguna cuenta puede tenerlas.
			return false;
		}
	}

	private static long minutosRestantes(Instant ahora, Instant hasta) {
		long segundos = Duration.between(ahora, hasta).getSeconds();
		return Math.max(1, (segundos + 59) / 60);
	}

}
