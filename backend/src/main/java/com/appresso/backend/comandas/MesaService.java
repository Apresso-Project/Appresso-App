package com.appresso.backend.comandas;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Mesas del local (RF-64): el administrador las crea con un nombre único y quedan
 * {@code DISPONIBLES}; meseros y administrador las ven en el mapa de mesas. El estado cambia solo con
 * las comandas (se ocupa al crear una, se libera al cerrarla). Crear queda en la auditoría (RNF-12).
 */
@Service
public class MesaService {

	private final MesaRepository mesas;
	private final RegistroAuditoriaRepository auditoria;
	private final UsuarioRepository usuarios;

	public MesaService(MesaRepository mesas, RegistroAuditoriaRepository auditoria, UsuarioRepository usuarios) {
		this.mesas = mesas;
		this.auditoria = auditoria;
		this.usuarios = usuarios;
	}

	/** Todas las mesas, ordenadas por nombre (sin distinguir mayúsculas). */
	@Transactional(readOnly = true)
	public List<MesaRespuesta> listar() {
		return mesas.findAll().stream()
				.sorted(Comparator.comparing(Mesa::getNombre, String.CASE_INSENSITIVE_ORDER).thenComparing(Mesa::getId))
				.map(MesaRespuesta::de)
				.toList();
	}

	/**
	 * Crea una mesa DISPONIBLE.
	 *
	 * @throws ConflictoComandaException si ya existe una mesa con ese nombre (409)
	 */
	@Transactional
	public MesaRespuesta crear(MesaRequest solicitud, Long actorId) {
		String nombre = solicitud.nombre().trim();
		if (nombre.isEmpty()) {
			throw new SolicitudComandaInvalidaException("nombre", "El nombre es obligatorio");
		}
		if (mesas.existsByNombreIgnoreCase(nombre)) {
			throw new ConflictoComandaException("nombre", "Ya existe una mesa con ese nombre");
		}
		Mesa mesa = new Mesa();
		mesa.setNombre(nombre);
		mesas.saveAndFlush(mesa);

		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(usuarios.getReferenceById(actorId));
		registro.setTipoOperacion("MESA_CREADA");
		registro.setEntidad("Mesa");
		registro.setEntidadId(mesa.getId());
		registro.setDetalle("Nombre: " + nombre);
		auditoria.save(registro);
		return MesaRespuesta.de(mesa);
	}

}
