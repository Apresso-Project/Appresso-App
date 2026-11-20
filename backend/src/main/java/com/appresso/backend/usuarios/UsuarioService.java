package com.appresso.backend.usuarios;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;

/**
 * Administración de las cuentas del personal (RF-02). Solo la usa el controlador de usuarios, que
 * ya exige rol de administrador.
 *
 * <p>Reglas de negocio:</p>
 * <ul>
 * <li>Solo cuentas del personal: las de cliente las crea el propio cliente al registrarse (RF-50).
 * No aparecen en la lista, no se pueden consultar, editar, desactivar ni activar (responden 404 como
 * si no existieran) y no se puede crear ni pasar una cuenta al rol {@code CLIENTE}.</li>
 * <li>El correo es único sin importar mayúsculas (RNF-11) y se guarda sin espacios sobrantes.</li>
 * <li>La contraseña se guarda con BCrypt, nunca en texto plano (RNF-05).</li>
 * <li>Desactivar no borra: la cuenta conserva su historial y no puede iniciar sesión (RF-02).</li>
 * <li>Un administrador no puede desactivar su propia cuenta ni cambiarse el rol: así el sistema no se
 * queda sin administradores por accidente. Otro administrador puede hacerlo.</li>
 * <li>Siempre queda al menos un administrador activo, aunque dos administradores se degraden entre
 * sí al mismo tiempo (se serializan bloqueando las filas de los administradores activos).</li>
 * <li>Cambiar una contraseña no cierra las sesiones ya abiertas de esa cuenta: su token sigue
 * sirviendo hasta que venza (12 h). Desactivar la cuenta sí corta el acceso de inmediato.</li>
 * <li>Cambiar la contraseña de una cuenta también la desbloquea (borra los intentos fallidos).</li>
 * <li>Crear, editar, desactivar y activar quedan en la auditoría (RNF-12), sin guardar contraseñas.</li>
 * </ul>
 */
@Service
public class UsuarioService {

	private static final int MAX_BYTES_CONTRASENA = 72;

	/** La cuenta pedida no existe (404). */
	public static class UsuarioNoEncontradoException extends RuntimeException {
		public UsuarioNoEncontradoException(Long id) {
			super("El usuario " + id + " no existe");
		}
	}

	/** El correo ya pertenece a otra cuenta (409). */
	public static class CorreoDuplicadoException extends RuntimeException {
		public CorreoDuplicadoException() {
			super("Ya existe una cuenta con ese correo");
		}
	}

	/** La operación rompe una regla de negocio (409). */
	public static class ReglaUsuarioException extends RuntimeException {
		public ReglaUsuarioException(String mensaje) {
			super(mensaje);
		}
	}

	/** Un campo no cumple una validación que solo se puede comprobar aquí (400). */
	public static class CampoInvalidoException extends RuntimeException {
		private final String campo;

		public CampoInvalidoException(String campo, String mensaje) {
			super(mensaje);
			this.campo = campo;
		}

		public String getCampo() {
			return campo;
		}
	}

	private final UsuarioRepository usuarios;
	private final RegistroAuditoriaRepository auditoria;
	private final PasswordEncoder codificador;

	public UsuarioService(UsuarioRepository usuarios, RegistroAuditoriaRepository auditoria,
			PasswordEncoder codificador) {
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.codificador = codificador;
	}

	@Transactional(readOnly = true)
	public List<UsuarioRespuesta> listar() {
		return usuarios.findByRolNot(Rol.CLIENTE, Sort.by("nombre", "id")).stream().map(UsuarioRespuesta::de).toList();
	}

	@Transactional(readOnly = true)
	public UsuarioRespuesta obtener(Long id) {
		return UsuarioRespuesta.de(buscar(id));
	}

	@Transactional
	public UsuarioRespuesta crear(UsuarioCrearRequest datos, Long actorId) {
		exigirRolDePersonal(datos.rol());
		String correo = datos.correo().trim();
		exigirCorreoLibre(correo, null);
		exigirContrasenaValida(datos.contrasena());

		Usuario nuevo = new Usuario();
		nuevo.setNombre(datos.nombre().trim());
		nuevo.setCorreo(correo);
		nuevo.setContrasenaHash(codificador.encode(datos.contrasena()));
		nuevo.setRol(datos.rol());
		nuevo.setActivo(true);
		usuarios.save(nuevo);

		registrar(actorId, "USUARIO_CREADO", nuevo, "Rol " + nuevo.getRol() + ", correo " + nuevo.getCorreo());
		return UsuarioRespuesta.de(nuevo);
	}

	@Transactional
	public UsuarioRespuesta editar(Long id, UsuarioEditarRequest datos, Long actorId) {
		Usuario usuario = buscar(id);
		exigirRolDePersonal(datos.rol());
		String correo = datos.correo().trim();
		exigirCorreoLibre(correo, id);
		if (Objects.equals(id, actorId) && datos.rol() != usuario.getRol()) {
			throw new ReglaUsuarioException("No puedes cambiar tu propio rol. Pídeselo a otro administrador.");
		}
		if (usuario.getRol() == Rol.ADMINISTRADOR && usuario.isActivo() && datos.rol() != Rol.ADMINISTRADOR) {
			exigirOtroAdministradorActivo(usuario);
		}

		StringBuilder cambios = new StringBuilder();
		if (!usuario.getNombre().equals(datos.nombre().trim())) {
			cambios.append("nombre; ");
			usuario.setNombre(datos.nombre().trim());
		}
		if (!usuario.getCorreo().equals(correo)) {
			cambios.append("correo ").append(usuario.getCorreo()).append(" -> ").append(correo).append("; ");
			usuario.setCorreo(correo);
		}
		if (usuario.getRol() != datos.rol()) {
			cambios.append("rol ").append(usuario.getRol()).append(" -> ").append(datos.rol()).append("; ");
			usuario.setRol(datos.rol());
		}
		if (datos.contrasena() != null) {
			exigirContrasenaValida(datos.contrasena());
			usuario.setContrasenaHash(codificador.encode(datos.contrasena()));
			usuario.setIntentosFallidos(0);
			usuario.setBloqueadoHasta(null);
			cambios.append("contraseña; ");
		}
		usuarios.save(usuario);

		// Si no cambió nada no hay nada que auditar.
		if (!cambios.isEmpty()) {
			registrar(actorId, "USUARIO_EDITADO", usuario, cambios.toString().trim());
		}
		return UsuarioRespuesta.de(usuario);
	}

	/** Desactiva la cuenta. Si ya estaba desactivada no hace nada. */
	@Transactional
	public UsuarioRespuesta desactivar(Long id, Long actorId) {
		Usuario usuario = buscar(id);
		if (Objects.equals(id, actorId)) {
			throw new ReglaUsuarioException("No puedes desactivar tu propia cuenta. Pídeselo a otro administrador.");
		}
		if (usuario.isActivo()) {
			if (usuario.getRol() == Rol.ADMINISTRADOR) {
				exigirOtroAdministradorActivo(usuario);
			}
			usuario.setActivo(false);
			usuarios.save(usuario);
			registrar(actorId, "USUARIO_DESACTIVADO", usuario, "Cuenta " + usuario.getCorreo());
		}
		return UsuarioRespuesta.de(usuario);
	}

	/** Vuelve a activar una cuenta desactivada y la desbloquea. Si ya estaba activa no hace nada. */
	@Transactional
	public UsuarioRespuesta activar(Long id, Long actorId) {
		Usuario usuario = buscar(id);
		if (!usuario.isActivo()) {
			usuario.setActivo(true);
			usuario.setIntentosFallidos(0);
			usuario.setBloqueadoHasta(null);
			usuarios.save(usuario);
			registrar(actorId, "USUARIO_ACTIVADO", usuario, "Cuenta " + usuario.getCorreo());
		}
		return UsuarioRespuesta.de(usuario);
	}

	/** La cuenta del personal con ese id. Una cuenta de cliente cuenta como inexistente. */
	private Usuario buscar(Long id) {
		return usuarios.findById(id)
				.filter(u -> u.getRol() != Rol.CLIENTE)
				.orElseThrow(() -> new UsuarioNoEncontradoException(id));
	}

	private static void exigirRolDePersonal(Rol rol) {
		if (rol == Rol.CLIENTE) {
			throw new CampoInvalidoException("rol", "Las cuentas de cliente se crean desde el registro de la app.");
		}
	}

	/**
	 * Falla si {@code objetivo} es el único administrador activo. Bloquea las filas de los
	 * administradores activos para que dos solicitudes simultáneas no puedan dejar el sistema sin ninguno.
	 */
	private void exigirOtroAdministradorActivo(Usuario objetivo) {
		boolean hayOtro = usuarios.bloquearAdministradoresActivos().stream()
				.anyMatch(admin -> !admin.getId().equals(objetivo.getId()));
		if (!hayOtro) {
			throw new ReglaUsuarioException("Debe quedar al menos un administrador activo.");
		}
	}

	private void exigirCorreoLibre(String correo, Long idPropio) {
		usuarios.findByCorreoIgnoreCase(correo).ifPresent(existente -> {
			if (!existente.getId().equals(idPropio)) {
				throw new CorreoDuplicadoException();
			}
		});
	}

	private static void exigirContrasenaValida(String contrasena) {
		// BCrypt solo admite 72 bytes; una contraseña con tildes o emojis ocupa más bytes que caracteres.
		if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
			throw new CampoInvalidoException("contrasena", "La contraseña es demasiado larga");
		}
	}

	private void registrar(Long actorId, String operacion, Usuario afectado, String detalle) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(usuarios.getReferenceById(actorId));
		registro.setTipoOperacion(operacion);
		registro.setEntidad("Usuario");
		registro.setEntidadId(afectado.getId());
		registro.setDetalle(detalle.length() > 2000 ? detalle.substring(0, 2000) : detalle);
		auditoria.save(registro);
	}

}
