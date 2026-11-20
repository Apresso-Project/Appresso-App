package com.appresso.backend.fidelizacion;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Gestión de clientes del programa de fidelización y cumplimiento de la Ley 1581 de 2012
 * (Protección de Datos Personales en Colombia). Ver docs/seguridad-y-ley-1581.md.
 * <p>
 * Garantiza:
 * <ol>
 * <li>Exigencia de aceptación explícita de tratamiento de datos en el registro.</li>
 * <li>Trazabilidad del consentimiento: fecha de aceptación en el cliente y registro inmutable en
 * auditoría, con la versión del texto de autorización aceptado (SCRUM-101).</li>
 * <li>Supresión de datos personales (Habeas Data, art. 15): eliminación física si el cliente no
 * tiene historial comercial; si lo tiene, anonimización irreversible (SCRUM-101).</li>
 * </ol>
 * Los registros de auditoría <b>no guardan datos personales</b> (nombre, documento, celular): son
 * inmutables, así que si los guardaran sobrevivirían a la supresión. Se identifican por el id.
 */
@Service
public class ClienteService {

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
	private static final int MAX_MOTIVO = 500;

	private final ClienteRepository clientes;
	private final RegistroAuditoriaRepository auditoria;
	private final UsuarioRepository usuarios;

	public ClienteService(ClienteRepository clientes, RegistroAuditoriaRepository auditoria, UsuarioRepository usuarios) {
		this.clientes = clientes;
		this.auditoria = auditoria;
		this.usuarios = usuarios;
	}

	@Transactional
	public ClienteRespuesta registrar(ClienteCrearRequest req, Long actorId) {
		if (req.aceptoTratamientoDatos() == null || !req.aceptoTratamientoDatos()) {
			throw new ConsentimientoRequeridoException(
					"El registro de clientes exige la aceptación explícita de la política de tratamiento de datos personales conforme a la Ley 1581 de 2012.");
		}

		String documentoLimpio = req.documento().trim();
		if (clientes.existsByDocumento(documentoLimpio)) {
			throw new DocumentoDuplicadoException("Ya existe un cliente registrado con el documento " + documentoLimpio);
		}

		Instant ahora = Instant.now();
		Cliente c = new Cliente();
		c.setNombre(req.nombre().trim());
		c.setApellido(req.apellido().trim());
		c.setDocumento(documentoLimpio);
		c.setCelular(req.celular().trim());
		c.setAceptoTratamientoDatos(true);
		c.setFechaAceptacionDatos(ahora);
		c.setFechaRegistro(ahora);
		c.setPuntos(0);

		Cliente guardado = clientes.save(c);

		auditar(OPERACION_CONSENTIMIENTO, guardado.getId(), actorId, ahora,
				"Autorización previa, expresa e informada para el tratamiento de datos personales (Ley 1581 de 2012). "
						+ "Cliente #" + guardado.getId() + ". Texto de autorización aceptado: versión "
						+ VERSION_TEXTO_AUTORIZACION + ".");

		return ClienteRespuesta.desde(guardado);
	}

	@Transactional(readOnly = true)
	public ClienteRespuesta buscarPorDocumento(String documento) {
		return clientes.findByDocumento(documento.trim())
				.map(ClienteRespuesta::desde)
				.orElseThrow(() -> new ClienteNoEncontradoException(
						"No se encontró ningún cliente con el documento " + documento.trim()));
	}

	@Transactional(readOnly = true)
	public List<ClienteRespuesta> listar() {
		return clientes.findAll().stream()
				.map(ClienteRespuesta::desde)
				.toList();
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
			throw new ClienteNoEncontradoException("No se encontró el cliente con id " + clienteId);
		}
		return historial;
	}

	/**
	 * Suprime los datos personales del cliente (Ley 1581, art. 15):
	 * <ul>
	 * <li>Siempre borra sus notificaciones y, si tiene cuenta para entrar a la app, la desactiva y
	 * anonimiza.</li>
	 * <li>Sin comandas ni canjes: elimina el registro del cliente.</li>
	 * <li>Con comandas o canjes (historial comercial que se debe conservar): anonimiza nombre,
	 * apellido, documento y celular y revoca la autorización. Es irreversible.</li>
	 * </ul>
	 * Las dependencias se revisan antes de borrar (no se espera un error de la base de datos): un
	 * error de integridad dentro de la transacción la dejaría marcada para revertirse.
	 */
	@Transactional
	public ResultadoSupresion suprimirDatosPersonales(Long clienteId, String motivo, Long actorId) {
		// Primero las notificaciones: el borrado directo vacía el contexto de persistencia, así que el
		// cliente se carga después. Si el cliente no existe, la excepción revierte la transacción.
		clientes.borrarNotificaciones(clienteId);
		Cliente c = clientes.findById(clienteId)
				.orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con id " + clienteId));

		Instant ahora = Instant.now();
		String motivoTexto = limpiarMotivo(motivo);

		Usuario cuenta = c.getUsuario();
		if (cuenta != null) {
			c.setUsuario(null);
			anonimizarCuenta(cuenta);
		}

		ResultadoSupresion resultado;
		if (clientes.tieneComandas(clienteId) || clientes.tieneCanjes(clienteId)) {
			c.setNombre(NOMBRE_SUPRIMIDO);
			c.setApellido(APELLIDO_SUPRIMIDO);
			c.setDocumento(PREFIJO_DOCUMENTO_SUPRIMIDO + clienteId);
			c.setCelular(CELULAR_SUPRIMIDO);
			c.setAceptoTratamientoDatos(false);
			clientes.save(c);
			resultado = ResultadoSupresion.ANONIMIZADO;
		} else {
			clientes.delete(c);
			resultado = ResultadoSupresion.ELIMINADO;
		}

		auditar(OPERACION_SUPRESION, clienteId, actorId, ahora,
				"Supresión de datos personales conforme al art. 15 de la Ley 1581 de 2012. Cliente #" + clienteId
						+ ": " + resultado.descripcion + (cuenta != null ? " Cuenta de usuario desactivada." : "")
						+ " Motivo: " + motivoTexto);

		return resultado;
	}

	private void anonimizarCuenta(Usuario cuenta) {
		cuenta.setNombre(NOMBRE_SUPRIMIDO);
		cuenta.setCorreo("eliminado-" + cuenta.getId() + "@appresso.invalid");
		cuenta.setActivo(false);
		usuarios.save(cuenta);
	}

	private static String limpiarMotivo(String motivo) {
		if (motivo == null || motivo.isBlank()) {
			return "Solicitud del titular (Habeas Data)";
		}
		String limpio = motivo.trim();
		return limpio.length() > MAX_MOTIVO ? limpio.substring(0, MAX_MOTIVO) : limpio;
	}

	private void auditar(String operacion, Long clienteId, Long actorId, Instant fecha, String detalle) {
		RegistroAuditoria reg = new RegistroAuditoria();
		reg.setTipoOperacion(operacion);
		reg.setEntidad(ENTIDAD);
		reg.setEntidadId(clienteId);
		reg.setDetalle(detalle);
		if (actorId != null) {
			reg.setUsuario(usuarios.getReferenceById(actorId));
		}
		reg.setFecha(fecha);
		auditoria.save(reg);
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

	public static class ConsentimientoRequeridoException extends RuntimeException {
		public ConsentimientoRequeridoException(String mensaje) {
			super(mensaje);
		}
	}

	public static class DocumentoDuplicadoException extends RuntimeException {
		public DocumentoDuplicadoException(String mensaje) {
			super(mensaje);
		}
	}

	public static class ClienteNoEncontradoException extends RuntimeException {
		public ClienteNoEncontradoException(String mensaje) {
			super(mensaje);
		}
	}

}
