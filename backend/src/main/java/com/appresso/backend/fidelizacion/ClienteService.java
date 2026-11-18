package com.appresso.backend.fidelizacion;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Gestión de clientes del programa de fidelización y cumplimiento de la Ley 1581 de 2012
 * (Protección de Datos Personales en Colombia).
 *
 * Garantiza:
 * 1. Exigencia de aceptación explícita de tratamiento de datos en el registro.
 * 2. Trazabilidad del consentimiento con marca de tiempo y registro inmutable en auditoría.
 * 3. Mecanismo de supresión y eliminación de datos personales (Habeas Data).
 */
@Service
public class ClienteService {

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

		// Trazabilidad del consentimiento en auditoría inmutable
		RegistroAuditoria reg = new RegistroAuditoria();
		reg.setTipoOperacion("CONSENTIMIENTO_DATOS_1581");
		reg.setEntidad("Cliente");
		reg.setEntidadId(guardado.getId());
		reg.setDetalle("Autorización expresa otorgada para tratamiento de datos personales (Ley 1581/2012). Documento: "
				+ guardado.getDocumento() + ", Titular: " + guardado.getNombre() + " " + guardado.getApellido());
		if (actorId != null) {
			reg.setUsuario(usuarios.getReferenceById(actorId));
		}
		reg.setFecha(ahora);
		auditoria.save(reg);

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

	@Transactional
	public void suprimirDatosPersonales(Long clienteId, String motivo, Long actorId) {
		Cliente c = clientes.findById(clienteId)
				.orElseThrow(() -> new ClienteNoEncontradoException("No se encontró el cliente con id " + clienteId));

		Instant ahora = Instant.now();
		String motivoTexto = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Solicitud del titular (Habeas Data)";

		// Intento 1: eliminación física si no tiene comandas asociadas
		try {
			clientes.delete(c);
			clientes.flush();
		} catch (DataIntegrityViolationException ex) {
			// Si tiene comandas o registros históricos relacionados, se anonimizan sus datos personales
			// protegiendo la identidad y revocando el consentimiento conforme a la ley.
			c.setNombre("[DATOS ELIMINADOS]");
			c.setApellido("[LEY 1581]");
			c.setCelular("[ELIMINADO]");
			c.setAceptoTratamientoDatos(false);
			clientes.save(c);
		}

		// Registro inmutable de la solicitud y ejecución de supresión de datos
		RegistroAuditoria reg = new RegistroAuditoria();
		reg.setTipoOperacion("SUPRESION_DATOS_LEY_1581");
		reg.setEntidad("Cliente");
		reg.setEntidadId(clienteId);
		reg.setDetalle("Supresión de datos personales ejecutada conforme al Art. 15 de la Ley 1581 de 2012. Motivo: "
				+ motivoTexto);
		if (actorId != null) {
			reg.setUsuario(usuarios.getReferenceById(actorId));
		}
		reg.setFecha(ahora);
		auditoria.save(reg);
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
