package com.appresso.backend.fidelizacion;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Clientes del programa de fidelización (7.1.1).
 *
 * <p>Reglas:</p>
 * <ul>
 * <li>El propio cliente crea su cuenta desde la app (RF-50): una cuenta {@link Usuario} con rol
 * {@code CLIENTE} (correo y contraseña con BCrypt) y su registro {@link Cliente} con 0 puntos, en la
 * misma transacción. Inicia sesión con su documento (RF-01).</li>
 * <li>Exige aceptar el tratamiento de datos personales (Ley 1581 de 2012) y guarda la fecha de la
 * aceptación. La auditoría registra que se otorgó, sin copiar datos personales (RNF-09, RNF-12).</li>
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
	private final PasswordEncoder codificador;
	private final Clock reloj;

	public ClienteService(ClienteRepository clientes, UsuarioRepository usuarios, RegistroAuditoriaRepository auditoria,
			PasswordEncoder codificador, Clock reloj) {
		this.clientes = clientes;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
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

		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(cuenta);
		registro.setTipoOperacion("CONSENTIMIENTO_DATOS_1581");
		registro.setEntidad("Cliente");
		registro.setEntidadId(cliente.getId());
		registro.setDetalle("El titular aceptó el tratamiento de sus datos personales (Ley 1581 de 2012) al registrarse desde la app.");
		registro.setFecha(ahora);
		auditoria.save(registro);

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

}
