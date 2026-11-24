package com.appresso.backend.fidelizacion;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.AuthService;
import com.appresso.backend.auth.CodigoRecuperacionRepository;
import com.appresso.backend.auth.CredencialesInvalidasException;
import com.appresso.backend.usuarios.DispositivoPushRepository;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Clientes del programa de fidelización (7.1.1) y protección de sus datos personales (8.4.1). Ver
 * {@code docs/seguridad-y-ley-1581.md}.
 *
 * <p>Reglas:</p>
 * <ul>
 * <li>El propio cliente crea su cuenta desde la app (RF-50): una cuenta {@link Usuario} con rol
 * {@code CLIENTE} (correo y contraseña con BCrypt) y su registro {@link Cliente} con 0 puntos, en la
 * misma transacción. Inicia sesión con su documento (RF-01).</li>
 * <li>Exige aceptar el tratamiento de datos personales (Ley 1581 de 2012) y guarda la fecha de la
 * aceptación. La auditoría registra que se otorgó y la versión del texto aceptado, sin copiar datos
 * personales (RNF-09, RNF-12): es inmutable, así que si los guardara sobrevivirían a la supresión.</li>
 * <li>El cliente elimina sus datos desde la app (Ley 1581, art. 15): si no tiene historial comercial
 * se borra su registro; si lo tiene, se anonimiza de forma irreversible. El personal no lo hace.</li>
 * <li>Documento y correo son únicos. El correo no distingue mayúsculas y no puede repetir el de
 * ninguna cuenta, tampoco del personal. Dos registros simultáneos con el mismo dato chocan con la
 * restricción única de la base y responden igual que un duplicado (409).</li>
 * <li>La búsqueda por documento devuelve solo nombre, apellido, documento y puntos (RF-51).</li>
 * </ul>
 */
@Service
public class ClienteService {

	public static final String MENSAJE_REGISTRO_EXITOSO =
			"Registro exitoso. ¡Ya puedes empezar a acumular tus puntos de fidelidad!";
	public static final String MENSAJE_DOCUMENTO_DUPLICADO = "Este documento ya está registrado.";
	public static final String MENSAJE_CORREO_DUPLICADO = "Este correo ya está registrado.";
	public static final String MENSAJE_NO_ENCONTRADO = "Cliente no encontrado";

	public static final String MENSAJE_CONTRASENA_INCORRECTA = "La contraseña no es correcta.";
	static final String MENSAJE_CONTRASENA_REQUERIDA = "Escribe tu contraseña para confirmar.";

	/**
	 * Versión del texto de autorización que muestra la app (strings.xml: cliente_autorizacion_texto).
	 * Si ese texto cambia, se sube esta versión: así la auditoría prueba qué texto aceptó cada cliente.
	 */
	public static final String VERSION_TEXTO_AUTORIZACION = "1581-v1-2026-11";

	static final String OPERACION_CONSENTIMIENTO = "CONSENTIMIENTO_DATOS_1581";
	static final String OPERACION_SUPRESION = "SUPRESION_DATOS_LEY_1581";
	static final String ENTIDAD = "Cliente";

	static final String NOMBRE_SUPRIMIDO = "[DATOS ELIMINADOS]";
	static final String APELLIDO_SUPRIMIDO = "[LEY 1581]";
	static final String CELULAR_SUPRIMIDO = "[ELIMINADO]";
	static final String PREFIJO_DOCUMENTO_SUPRIMIDO = "ELIM-";

	private static final int MAX_BYTES_CONTRASENA = 72;

	/** El documento o el correo ya pertenecen a otra cuenta (409). {@code campo} es null si no se sabe cuál. */
	public static class DatoDuplicadoException extends RuntimeException {
		private final String campo;

		public DatoDuplicadoException(String campo, String mensaje) {
			super(mensaje);
			this.campo = campo;
		}

		public String getCampo() {
			return campo;
		}
	}

	/** Un campo no cumple una regla que solo se comprueba aquí (400). */
	public static class DatoInvalidoException extends RuntimeException {
		private final String campo;

		public DatoInvalidoException(String campo, String mensaje) {
			super(mensaje);
			this.campo = campo;
		}

		public String getCampo() {
			return campo;
		}
	}

	/** No hay cliente con ese documento, o la cuenta no tiene registro de cliente (404). */
	public static class ClienteNoEncontradoException extends RuntimeException {
		public ClienteNoEncontradoException() {
			super(MENSAJE_NO_ENCONTRADO);
		}
	}

	private final ClienteRepository clientes;
	private final UsuarioRepository usuarios;
	private final RegistroAuditoriaRepository auditoria;
	private final CodigoRecuperacionRepository codigos;
	private final DispositivoPushRepository dispositivos;
	private final AuthService autenticacion;
	private final PasswordEncoder codificador;
	private final Clock reloj;

	public ClienteService(ClienteRepository clientes, UsuarioRepository usuarios, RegistroAuditoriaRepository auditoria,
			CodigoRecuperacionRepository codigos, DispositivoPushRepository dispositivos, AuthService autenticacion,
			PasswordEncoder codificador, Clock reloj) {
		this.clientes = clientes;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.codigos = codigos;
		this.dispositivos = dispositivos;
		this.autenticacion = autenticacion;
		this.codificador = codificador;
		this.reloj = reloj;
	}

	@Transactional
	public RegistroClienteRespuesta registrar(RegistroClienteRequest datos) {
		String documento = DocumentoIdentidad.normalizar(datos.documento());
		if (!DocumentoIdentidad.esValido(documento)) {
			throw new DatoInvalidoException("documento", DocumentoIdentidad.MENSAJE_INVALIDO);
		}
		if (datos.contrasena().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_CONTRASENA) {
			throw new DatoInvalidoException("contrasena", RegistroClienteRequest.MENSAJE_CONTRASENA);
		}
		String correo = datos.correo().trim();
		if (clientes.existsByDocumento(documento)) {
			throw new DatoDuplicadoException("documento", MENSAJE_DOCUMENTO_DUPLICADO);
		}
		if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
			throw new DatoDuplicadoException("correo", MENSAJE_CORREO_DUPLICADO);
		}

		Instant ahora = reloj.instant();
		Usuario cuenta = new Usuario();
		cuenta.setNombre(datos.nombre().trim());
		cuenta.setCorreo(correo);
		cuenta.setContrasenaHash(codificador.encode(datos.contrasena()));
		cuenta.setRol(Rol.CLIENTE);
		cuenta.setActivo(true);
		cuenta.setFechaCreacion(ahora);

		Cliente cliente = new Cliente();
		cliente.setNombre(datos.nombre().trim());
		cliente.setApellido(datos.apellido().trim());
		cliente.setDocumento(documento);
		cliente.setCelular(datos.celular().replace(" ", ""));
		cliente.setAceptoTratamientoDatos(true);
		cliente.setFechaAceptacionDatos(ahora);
		cliente.setFechaRegistro(ahora);
		cliente.setPuntos(0);
		cliente.setUsuario(cuenta);

		try {
			usuarios.saveAndFlush(cuenta);
			clientes.saveAndFlush(cliente);
		} catch (DataIntegrityViolationException e) {
			// Otro registro con el mismo documento o correo se guardó entre la comprobación y aquí.
			throw new DatoDuplicadoException(null, "Este documento o correo ya está registrado.");
		}

		auditar(OPERACION_CONSENTIMIENTO, cliente.getId(), cuenta, ahora,
				"El titular aceptó el tratamiento de sus datos personales (Ley 1581 de 2012) al registrarse desde la app. "
						+ "Texto de autorización aceptado: versión " + VERSION_TEXTO_AUTORIZACION + ".");

		return new RegistroClienteRespuesta(MENSAJE_REGISTRO_EXITOSO, cliente.getNombre(), cliente.getPuntos());
	}

	@Transactional(readOnly = true)
	public ClienteRespuesta buscarPorDocumento(String documento) {
		return clientes.findByDocumento(DocumentoIdentidad.normalizar(documento))
				.map(ClienteRespuesta::desde)
				.orElseThrow(ClienteNoEncontradoException::new);
	}

	/** El registro de cliente de la cuenta que inició sesión (RF-55). */
	@Transactional(readOnly = true)
	public ClienteRespuesta miCuenta(Long usuarioId) {
		return clientes.findByUsuarioId(usuarioId)
				.map(ClienteRespuesta::desde)
				.orElseThrow(ClienteNoEncontradoException::new);
	}

	/**
	 * Evidencia del consentimiento y de su revocación (SCRUM-101): los registros de auditoría del
	 * cliente, del más antiguo al más reciente. Sirve aunque el cliente ya haya sido eliminado.
	 */
	@Transactional(readOnly = true)
	public List<ConsentimientoRespuesta> historialConsentimiento(Long clienteId) {
		List<ConsentimientoRespuesta> historial = auditoria
				.findByEntidadAndEntidadIdOrderByFechaAscIdAsc(ENTIDAD, clienteId).stream()
				.filter(r -> OPERACION_CONSENTIMIENTO.equals(r.getTipoOperacion())
						|| OPERACION_SUPRESION.equals(r.getTipoOperacion()))
				.map(ConsentimientoRespuesta::desde)
				.toList();
		if (historial.isEmpty() && !clientes.existsById(clienteId)) {
			throw new ClienteNoEncontradoException();
		}
		return historial;
	}

	/**
	 * El cliente elimina sus datos personales (Ley 1581, art. 15). Es irreversible: confirma con su
	 * contraseña, y los intentos fallidos cuentan como los del inicio de sesión.
	 * <ul>
	 * <li>Siempre borra sus notificaciones, sus dispositivos y sus códigos de recuperación, y
	 * desactiva y anonimiza su cuenta (el token deja de servir de inmediato).</li>
	 * <li>Sin comandas ni canjes: elimina el registro del cliente.</li>
	 * <li>Con comandas o canjes (historial comercial que se debe conservar): anonimiza nombre,
	 * apellido, documento y celular y revoca la autorización.</li>
	 * </ul>
	 * Las dependencias se revisan antes de borrar (no se espera un error de la base de datos): un
	 * error de integridad dentro de la transacción la dejaría marcada para revertirse.
	 */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public ResultadoSupresion eliminarMisDatos(Long usuarioId, String contrasena) {
		if (contrasena == null || contrasena.isBlank()) {
			throw new DatoInvalidoException("contrasena", MENSAJE_CONTRASENA_REQUERIDA);
		}
		autenticacion.confirmarContrasena(usuarioId, contrasena);
		Long clienteId = clientes.findByUsuarioId(usuarioId)
				.orElseThrow(ClienteNoEncontradoException::new).getId();

		// Los borrados directos vacían el contexto de persistencia: el cliente y la cuenta se cargan después.
		clientes.borrarNotificaciones(clienteId);
		dispositivos.borrarDeCuenta(usuarioId);
		codigos.borrarDeCuenta(usuarioId);
		Cliente cliente = clientes.findById(clienteId).orElseThrow(ClienteNoEncontradoException::new);
		Usuario cuenta = cliente.getUsuario();

		cliente.setUsuario(null);
		anonimizar(cuenta);

		ResultadoSupresion resultado;
		if (clientes.tieneComandas(clienteId) || clientes.tieneCanjes(clienteId)) {
			cliente.setNombre(NOMBRE_SUPRIMIDO);
			cliente.setApellido(APELLIDO_SUPRIMIDO);
			cliente.setDocumento(PREFIJO_DOCUMENTO_SUPRIMIDO + clienteId);
			cliente.setCelular(CELULAR_SUPRIMIDO);
			cliente.setAceptoTratamientoDatos(false);
			clientes.save(cliente);
			resultado = ResultadoSupresion.ANONIMIZADO;
		} else {
			clientes.delete(cliente);
			resultado = ResultadoSupresion.ELIMINADO;
		}

		auditar(OPERACION_SUPRESION, clienteId, cuenta, reloj.instant(),
				"Supresión de datos personales solicitada por el titular desde la app (Ley 1581 de 2012, art. 15): "
						+ resultado.descripcion + " Cuenta desactivada y anonimizada.");
		return resultado;
	}

	private void anonimizar(Usuario cuenta) {
		cuenta.setNombre(NOMBRE_SUPRIMIDO);
		cuenta.setCorreo("eliminado-" + cuenta.getId() + "@appresso.invalid");
		// Ya no queda ni el hash de su contraseña: se reemplaza por el de un valor que nadie conoce.
		cuenta.setContrasenaHash(codificador.encode(UUID.randomUUID().toString()));
		cuenta.setActivo(false);
		usuarios.save(cuenta);
	}

	private void auditar(String operacion, Long clienteId, Usuario cuenta, Instant fecha, String detalle) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(cuenta);
		registro.setTipoOperacion(operacion);
		registro.setEntidad(ENTIDAD);
		registro.setEntidadId(clienteId);
		registro.setDetalle(detalle);
		registro.setFecha(fecha);
		auditoria.save(registro);
	}

	/** Qué se hizo con los datos al atender una solicitud de supresión. */
	public enum ResultadoSupresion {
		ELIMINADO("datos eliminados (sin historial comercial)."),
		ANONIMIZADO("datos anonimizados (tiene historial comercial que se conserva).");

		final String descripcion;

		ResultadoSupresion(String descripcion) {
			this.descripcion = descripcion;
		}
	}

}
