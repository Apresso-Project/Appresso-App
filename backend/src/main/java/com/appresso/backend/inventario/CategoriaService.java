package com.appresso.backend.inventario;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.tiemporeal.CategoriaEventoDatos;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Ciclo de vida de las categorías (RF-06, entregable 3.1.1): crear, renombrar, desactivar y
 * reactivar. Nunca se borran: los productos y las comandas viejas siguen apuntando a ellas.
 *
 * <ul>
 * <li>El nombre es único sin distinguir mayúsculas.</li>
 * <li>Desactivar una categoría <b>no</b> toca sus productos: solo los saca del menú mientras
 * esté desactivada. Al reactivarla vuelven tal como estaban.</li>
 * <li>Cada cambio queda en la auditoría (RNF-12) y se publica por tiempo real.</li>
 * </ul>
 */
@Service
public class CategoriaService {

	private final CategoriaRepository categorias;
	private final ProductoRepository productos;
	private final RegistroAuditoriaRepository auditoria;
	private final UsuarioRepository usuarios;
	private final PublicadorTiempoReal publicador;

	public CategoriaService(CategoriaRepository categorias, ProductoRepository productos,
			RegistroAuditoriaRepository auditoria, UsuarioRepository usuarios, PublicadorTiempoReal publicador) {
		this.categorias = categorias;
		this.productos = productos;
		this.auditoria = auditoria;
		this.usuarios = usuarios;
		this.publicador = publicador;
	}

	/** Por defecto solo las activas (lo que ve el menú); con {@code incluirInactivas}, todas. */
	@Transactional(readOnly = true)
	public List<CategoriaRespuesta> listar(boolean incluirInactivas) {
		Map<Long, Long> totales = totalesPorCategoria();
		return categorias.findAll().stream()
				.filter(c -> incluirInactivas || c.isActiva())
				.sorted(Comparator.comparing(Categoria::getNombre, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Categoria::getId))
				.map(c -> CategoriaRespuesta.de(c, totales.getOrDefault(c.getId(), 0L)))
				.toList();
	}

	/** Quien no es administrador no ve las desactivadas: para él no existen (404). */
	@Transactional(readOnly = true)
	public CategoriaRespuesta obtener(Long id, boolean incluirInactivas) {
		Categoria c = buscar(id);
		if (!incluirInactivas && !c.isActiva()) {
			throw noExiste(id);
		}
		return respuesta(c);
	}

	@Transactional
	public CategoriaRespuesta crear(CategoriaRequest solicitud, Long actorId) {
		String nombre = solicitud.nombre().trim();
		if (categorias.existsByNombreIgnoreCase(nombre)) {
			throw nombreRepetido();
		}
		Categoria c = new Categoria();
		c.setNombre(nombre);
		categorias.save(c);

		registrar(actorId, "CATEGORIA_CREADA", c, "Nombre: " + nombre);
		publicar(TipoEvento.CATEGORIA_CREADA, c);
		return CategoriaRespuesta.de(c, 0);
	}

	@Transactional
	public CategoriaRespuesta renombrar(Long id, CategoriaRequest solicitud, Long actorId) {
		Categoria c = buscar(id);
		String nombre = solicitud.nombre().trim();
		if (!nombre.equals(c.getNombre())) {
			if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
				throw nombreRepetido();
			}
			String anterior = c.getNombre();
			c.setNombre(nombre);
			registrar(actorId, "CATEGORIA_RENOMBRADA", c, "'" + anterior + "' → '" + nombre + "'");
			publicar(TipoEvento.CATEGORIA_ACTUALIZADA, c);
		}
		return respuesta(c);
	}

	/** Idempotente: si ya estaba desactivada no hace nada (ni auditoría ni evento). */
	@Transactional
	public CategoriaRespuesta desactivar(Long id, Long actorId) {
		return cambiarEstado(id, false, actorId, "CATEGORIA_DESACTIVADA");
	}

	@Transactional
	public CategoriaRespuesta activar(Long id, Long actorId) {
		return cambiarEstado(id, true, actorId, "CATEGORIA_ACTIVADA");
	}

	private CategoriaRespuesta cambiarEstado(Long id, boolean activa, Long actorId, String operacion) {
		Categoria c = buscar(id);
		if (c.isActiva() != activa) {
			c.setActiva(activa);
			registrar(actorId, operacion, c, "Categoría '" + c.getNombre() + "'");
			publicar(TipoEvento.CATEGORIA_ACTUALIZADA, c);
		}
		return respuesta(c);
	}

	// ------------------------------------------------------------------ apoyo

	private Categoria buscar(Long id) {
		return categorias.findById(id).orElseThrow(() -> noExiste(id));
	}

	private CategoriaRespuesta respuesta(Categoria c) {
		return CategoriaRespuesta.de(c, totalesPorCategoria().getOrDefault(c.getId(), 0L));
	}

	private Map<Long, Long> totalesPorCategoria() {
		Map<Long, Long> totales = new HashMap<>();
		for (Object[] fila : productos.contarPorCategoria()) {
			totales.put((Long) fila[0], (Long) fila[1]);
		}
		return totales;
	}

	private void publicar(TipoEvento tipo, Categoria c) {
		publicador.publicarCategoria(tipo, new CategoriaEventoDatos(c.getId(), c.getNombre(), c.isActiva()));
	}

	private void registrar(Long actorId, String operacion, Categoria c, String detalle) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(usuarios.getReferenceById(actorId));
		registro.setTipoOperacion(operacion);
		registro.setEntidad("Categoria");
		registro.setEntidadId(c.getId());
		registro.setDetalle(detalle.length() > 2000 ? detalle.substring(0, 2000) : detalle);
		auditoria.save(registro);
	}

	private static ConflictoCatalogoException nombreRepetido() {
		return new ConflictoCatalogoException("nombre", "Ya existe una categoría con ese nombre");
	}

	private static EntityNotFoundException noExiste(Long id) {
		return new EntityNotFoundException("La categoría " + id + " no existe");
	}

}
