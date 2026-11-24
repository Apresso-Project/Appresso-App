package com.appresso.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.fidelizacion.DocumentoIdentidad;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Recuperación de la contraseña del cliente con un código enviado a su correo (RF-65). Las cuentas
 * del personal no se recuperan por aquí: sus contraseñas las gestiona el administrador (RF-02).
 *
 * <p>Reglas:</p>
 * <ul>
 * <li>El código tiene 6 dígitos, vence a los {@value #MINUTOS_VIGENCIA} minutos, sirve una sola vez
 * y se guarda como hash (BCrypt).</li>
 * <li>Pedir un código nuevo invalida los anteriores. Si se pidió uno hace menos de
 * {@value #SEGUNDOS_ENTRE_ENVIOS} segundos, no se envía otro (evita llenar el buzón del cliente).</li>
 * <li>Tras {@value #MAX_INTENTOS} intentos con un código incorrecto, ese código deja de servir.</li>
 * <li>La respuesta a la solicitud es la misma exista o no el documento, y el correo se envía en otro
 * hilo, para no revelar qué documentos están registrados.</li>
 * <li>Al cambiar la contraseña la cuenta se desbloquea (como cuando el administrador cambia la de un
 * integrante del personal). Las sesiones ya abiertas siguen hasta que venza su token.</li>
 * </ul>
 */
@Service
public class RecuperacionContrasenaService {

	public static final String MENSAJE_SOLICITUD =
			"Si el documento está registrado, enviamos un código al correo asociado.";
	public static final String MENSAJE_RESTABLECIDA = "Tu contraseña se actualizó. Ya puedes iniciar sesión.";

	static final long MINUTOS_VIGENCIA = 15;
	static final int MAX_INTENTOS = 5;
	static final long SEGUNDOS_ENTRE_ENVIOS = 60;
	private static final int MAX_BYTES_CONTRASENA = 72;

	private final UsuarioRepository usuarios;
	private final CodigoRecuperacionRepository codigos;
	private final RegistroAuditoriaRepository auditoria;
	private final PasswordEncoder codificador;
	private final ApplicationEventPublisher eventos;
	private final Clock reloj;
	private final SecureRandom aleatorio = new SecureRandom();

	/** Para que un documento sin cuenta tarde lo mismo que uno con cuenta (como en {@link AuthService}). */
	private final String hashFicticio;

	public RecuperacionContrasenaService(UsuarioRepository usuarios, CodigoRecuperacionRepository codigos,
			RegistroAuditoriaRepository auditoria, PasswordEncoder codificador, ApplicationEventPublisher eventos,
			Clock reloj) {
		this.usuarios = usuarios;
		this.codigos = codigos;
		this.auditoria = auditoria;
		this.codificador = codificador;
		this.eventos = eventos;
		this.reloj = reloj;
		this.hashFicticio = codificador.encode("codigo-ficticio");
	}

	/** Genera y envía un código si el documento es de un cliente con cuenta activa. Si no, no hace nada. */
	@Transactional
	public void solicitar(String documento) {
		Optional<Usuario> cuenta = cuentaDeCliente(documento);
		if (cuenta.isEmpty()) {
			codificador.encode("codigo-ficticio"); // mismo tiempo que generar un código
			return;
		}
		Usuario usuario = cuenta.get();
		Instant ahora = reloj.instant();

		boolean reciente = codigos.findFirstByUsuarioIdOrderByFechaCreacionDescIdDesc(usuario.getId())
				.map(ultimo -> ultimo.getFechaCreacion().isAfter(ahora.minusSeconds(SEGUNDOS_ENTRE_ENVIOS)))
				.orElse(false);
		if (reciente) {
			codificador.encode("codigo-ficticio");
			return;
		}

		codigos.invalidarSinUsar(usuario.getId());
		String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
		CodigoRecuperacion nuevo = new CodigoRecuperacion();
		nuevo.setUsuario(usuario);
		nuevo.setCodigoHash(codificador.encode(codigo));
		nuevo.setFechaCreacion(ahora);
		nuevo.setExpira(ahora.plus(Duration.ofMinutes(MINUTOS_VIGENCIA)));
		codigos.save(nuevo);

		auditar(usuario, "CODIGO_RECUPERACION_SOLICITADO", "Código de recuperación de contraseña enviado al correo de la cuenta", ahora);
		eventos.publishEvent(new CodigoRecuperacionGenerado(usuario.getCorreo(), usuario.getNombre(), codigo, MINUTOS_VIGENCIA));
	}

	/**
	 * Cambia la contraseña si el código es el vigente de esa cuenta. Los intentos fallidos se guardan
	 * aunque la operación termine en error: por eso la transacción no se revierte con
	 * {@link CodigoInvalidoException}.
	 */
	@Transactional(noRollbackFor = CodigoInvalidoException.class)
	public void restablecer(String documento, String codigo, String contrasenaNueva) {
		if (contrasenaNueva.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
			throw new ContrasenaInvalidaException();
		}
		Usuario usuario = cuentaDeCliente(documento).orElse(null);
		if (usuario == null) {
			codificador.matches(codigo, hashFicticio); // mismo tiempo que un código incorrecto
			throw new CodigoInvalidoException();
		}
		Instant ahora = reloj.instant();

		List<CodigoRecuperacion> sinUsar = codigos.bloquearSinUsar(usuario.getId());
		if (sinUsar.isEmpty()) {
			throw new CodigoInvalidoException();
		}
		CodigoRecuperacion vigente = sinUsar.get(0);
		if (!vigente.getExpira().isAfter(ahora) || vigente.getIntentos() >= MAX_INTENTOS) {
			vigente.setUsado(true);
			throw new CodigoInvalidoException();
		}
		if (!codificador.matches(codigo, vigente.getCodigoHash())) {
			vigente.setIntentos(vigente.getIntentos() + 1);
			if (vigente.getIntentos() >= MAX_INTENTOS) {
				vigente.setUsado(true);
			}
			throw new CodigoInvalidoException();
		}

		vigente.setUsado(true);
		usuario.setContrasenaHash(codificador.encode(contrasenaNueva));
		usuario.setIntentosFallidos(0);
		usuario.setBloqueadoHasta(null);
		auditar(usuario, "CONTRASENA_RESTABLECIDA", "Contraseña restablecida con código de recuperación", ahora);
	}

	/** La cuenta activa de cliente con ese documento, con su fila bloqueada. */
	private Optional<Usuario> cuentaDeCliente(String documento) {
		return usuarios.buscarIdDeCuentaPorDocumento(DocumentoIdentidad.normalizar(documento))
				.flatMap(usuarios::buscarPorIdBloqueandoFila)
				.filter(u -> u.getRol() == Rol.CLIENTE && u.isActivo());
	}

	private void auditar(Usuario usuario, String operacion, String detalle, Instant cuando) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(usuario);
		registro.setTipoOperacion(operacion);
		registro.setEntidad("Usuario");
		registro.setEntidadId(usuario.getId());
		registro.setDetalle(detalle);
		registro.setFecha(cuando);
		auditoria.save(registro);
	}

}
