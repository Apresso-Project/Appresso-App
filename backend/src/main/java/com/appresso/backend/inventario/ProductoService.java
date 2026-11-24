package com.appresso.backend.inventario;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.tiemporeal.ProductoEventoDatos;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Productos e inventario. Publica por tiempo real los cambios del catálogo y de las existencias
 * (SCRUM-28).
 *
 * <p>Ciclo de vida del catálogo para el administrador (RF-06, entregable 3.1.1): {@link #registrar},
 * {@link #modificar}, {@link #desactivar} y {@link #activar}, con validaciones de integridad
 * referencial, auditoría (RNF-12) y tiempo real. Los productos nunca se borran: las comandas y
 * los movimientos de inventario viejos siguen apuntando a ellos.</p>
 *
 * <p>Un producto está <b>en el menú</b> solo si él está activo y su categoría también.</p>
 */
@Service
public class ProductoService {

	private final ProductoRepository productos;
	private final CategoriaRepository categorias;
	private final MovimientoInventarioRepository movimientos;
	private final UsuarioRepository usuarios;
	private final PublicadorTiempoReal publicador;
	private final RegistroAuditoriaRepository auditoria;

	public ProductoService(ProductoRepository productos, CategoriaRepository categorias,
			MovimientoInventarioRepository movimientos, UsuarioRepository usuarios,
			PublicadorTiempoReal publicador, RegistroAuditoriaRepository auditoria) {
		this.productos = productos;
		this.categorias = categorias;
		this.movimientos = movimientos;
		this.usuarios = usuarios;
		this.publicador = publicador;
		this.auditoria = auditoria;
	}

	/** Crea un producto activo y publica PRODUCTO_CREADO. */
	@Transactional
	public Producto crear(Long categoriaId, String nombre, String descripcion, BigDecimal precio,
			int stockInicial, int umbralMinimo) {
		Categoria categoria = categorias.findById(categoriaId)
				.orElseThrow(() -> new EntityNotFoundException("Categoría " + categoriaId + " no existe"));
		Producto p = new Producto();
		p.setCategoria(categoria);
		p.setNombre(nombre);
		p.setDescripcion(descripcion);
		p.setPrecio(precio);
		p.setStockActual(stockInicial);
		p.setUmbralMinimo(umbralMinimo);
		productos.save(p);
		publicador.publicarProducto(TipoEvento.PRODUCTO_CREADO, datos(p));
		return p;
	}

	/** Cambia datos del producto (nombre, precio, umbral, activo) y publica PRODUCTO_ACTUALIZADO. */
	@Transactional
	public Producto actualizar(Long productoId, String nombre, String descripcion, BigDecimal precio,
			int umbralMinimo, boolean activo) {
		Producto p = bloquear(productoId);
		p.setNombre(nombre);
		p.setDescripcion(descripcion);
		p.setPrecio(precio);
		p.setUmbralMinimo(umbralMinimo);
		p.setActivo(activo);
		publicador.publicarProducto(TipoEvento.PRODUCTO_ACTUALIZADO, datos(p));
		return p;
	}

	/**
	 * Movimiento manual de existencias (RF-07): ENTRADA_MANUAL suma; SALIDA_MANUAL y AJUSTE_MERMA restan.
	 * Exige justificación y no deja el stock en negativo.
	 * Guarda el movimiento y publica EXISTENCIAS_ACTUALIZADAS. La cantidad siempre es positiva.
	 */
	@Transactional
	public Producto ajustarExistencias(Long productoId, TipoMovimientoInventario tipo, int cantidad,
			String justificacion, Long usuarioId) {
		if (cantidad < 1) {
			throw new IllegalArgumentException("La cantidad debe ser al menos 1");
		}
		if (tipo == null || tipo == TipoMovimientoInventario.VENTA) {
			throw new IllegalArgumentException("El tipo debe ser una entrada, salida o merma manual");
		}
		if (justificacion == null || justificacion.isBlank()) {
			throw new IllegalArgumentException("El movimiento necesita una justificación (RF-07)");
		}
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new EntityNotFoundException("Usuario " + usuarioId + " no existe"));
		if (usuario.getRol() != Rol.ADMINISTRADOR) {
			throw new AccessDeniedException("Solo el administrador puede ajustar el inventario");
		}
		Producto p = bloquear(productoId);
		return mover(p, tipo, cantidad, justificacion.strip(), usuario, null);
	}

	@Transactional
	public ProductoRespuesta ajustar(Long productoId, AjusteExistenciasRequest solicitud, Long actorId) {
		if (solicitud.tipo() == TipoMovimientoInventario.VENTA) {
			throw new CampoInvalidoCatalogoException("tipo", "Las ventas se descuentan al confirmar comandas");
		}
		return ProductoRespuesta.de(ajustarExistencias(productoId, solicitud.tipo(), solicitud.cantidad().intValueExact(),
				solicitud.concepto(), actorId));
	}

	@Transactional(readOnly = true)
	public List<ProductoRespuesta> listarStockBajo() {
		return productos.findConStockBajo().stream().map(ProductoRespuesta::de).toList();
	}

	/**
	 * Descuenta del inventario lo que se vendió en una comanda (RF-18, RF-20): guarda un movimiento
	 * de tipo VENTA ligado a la comanda y publica EXISTENCIAS_ACTUALIZADAS.
	 */
	@Transactional
	public Producto descontarPorVenta(Long productoId, int cantidad, Comanda comanda, Usuario usuario) {
		Producto producto = bloquear(productoId);
		if (!enElMenu(producto)) {
			throw new IllegalStateException("El producto " + producto.getNombre() + " no está disponible");
		}
		return mover(producto, TipoMovimientoInventario.VENTA, cantidad,
				"Venta en comanda " + comanda.getId(), usuario, comanda);
	}

	private Producto mover(Producto p, TipoMovimientoInventario tipo, int cantidad, String justificacion,
			Usuario usuario, Comanda comanda) {
		if (cantidad < 1) {
			throw new IllegalArgumentException("La cantidad debe ser al menos 1");
		}
		long delta = tipo == TipoMovimientoInventario.ENTRADA_MANUAL ? cantidad : -cantidad;
		long resultante = p.getStockActual() + delta;
		if (resultante < 0) {
			throw new StockInsuficienteException("Existencias insuficientes de " + p.getNombre()
					+ ": hay " + p.getStockActual() + " y se piden " + cantidad);
		}
		if (resultante > Integer.MAX_VALUE) {
			throw new CampoInvalidoCatalogoException("cantidad", "El ajuste supera el máximo de existencias");
		}
		p.setStockActual((int) resultante);

		MovimientoInventario m = new MovimientoInventario();
		m.setProducto(p);
		m.setTipo(tipo);
		m.setCantidad(cantidad);
		m.setStockResultante(p.getStockActual());
		m.setJustificacion(justificacion);
		m.setUsuario(usuario);
		m.setComanda(comanda);
		movimientos.save(m);

		publicador.publicarProducto(TipoEvento.EXISTENCIAS_ACTUALIZADAS, datos(p));
		return p;
	}

	// ================================================================== catálogo (3.1.1)

	/**
	 * Lista productos. Sin {@code incluirFueraDelMenu} solo salen los que están en el menú (activos y de
	 * categoría activa); con él, todos. {@code categoriaId} filtra por categoría (null = todas).
	 */
	@Transactional(readOnly = true)
	public List<ProductoRespuesta> listar(Long categoriaId, boolean incluirFueraDelMenu) {
		return productos.findAllConCategoria().stream()
				.filter(p -> incluirFueraDelMenu || enElMenu(p))
				.filter(p -> categoriaId == null || p.getCategoria().getId().equals(categoriaId))
				.sorted(Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Producto::getId))
				.map(ProductoRespuesta::de)
				.toList();
	}

	/** Quien no puede ver lo que está fuera del menú recibe 404 como si no existiera. */
	@Transactional(readOnly = true)
	public ProductoRespuesta obtener(Long id, boolean incluirFueraDelMenu) {
		Producto p = buscar(id);
		if (!incluirFueraDelMenu && !enElMenu(p)) {
			throw new EntityNotFoundException("Producto " + id + " no existe");
		}
		return ProductoRespuesta.de(p);
	}

	/**
	 * Crea un producto activo. La categoría debe existir (400) y estar activa (409). Si trae
	 * existencias iniciales, queda un movimiento de entrada en el historial (RF-14) para que la
	 * cuenta del inventario cuadre desde el principio.
	 */
	@Transactional
	public ProductoRespuesta registrar(ProductoCrearRequest solicitud, Long actorId) {
		Categoria categoria = categoriaParaAsignar(solicitud.categoriaId());
		int existencias = solicitud.existenciasIniciales() == null ? 0 : solicitud.existenciasIniciales();

		Producto p = new Producto();
		p.setCategoria(categoria);
		p.setNombre(solicitud.nombre().trim());
		p.setDescripcion(textoOpcional(solicitud.descripcion()));
		p.setImagenUrl(textoOpcional(solicitud.imagenUrl()));
		p.setPrecio(solicitud.precio().setScale(2));
		p.setStockActual(existencias);
		p.setUmbralMinimo(solicitud.umbralMinimo() == null ? 0 : solicitud.umbralMinimo());
		productos.save(p);

		if (existencias > 0) {
			MovimientoInventario m = new MovimientoInventario();
			m.setProducto(p);
			m.setTipo(TipoMovimientoInventario.ENTRADA_MANUAL);
			m.setCantidad(existencias);
			m.setStockResultante(existencias);
			m.setJustificacion("Existencias iniciales");
			m.setUsuario(usuarios.getReferenceById(actorId));
			movimientos.save(m);
		}

		registrar(actorId, "PRODUCTO_CREADO", p, "Nombre '" + p.getNombre() + "', precio " + p.getPrecio()
				+ ", categoría '" + categoria.getNombre() + "', existencias " + existencias);
		publicador.publicarProducto(TipoEvento.PRODUCTO_CREADO, datos(p));
		return ProductoRespuesta.de(p);
	}

	/**
	 * Edita nombre, descripción, precio, categoría, imagen y umbral. Cambiar a otra categoría exige que
	 * exista y esté activa; si el producto ya estaba en una categoría desactivada y esta no cambia,
	 * se puede seguir editando lo demás. No toca las existencias ni las comandas ya hechas (cada
	 * línea de comanda guarda el precio del momento). Si no cambia nada, no hace nada.
	 */
	@Transactional
	public ProductoRespuesta modificar(Long id, ProductoEditarRequest solicitud, Long actorId) {
		Producto p = bloquear(id);
		// Se valida todo antes de tocar el producto, para no dejar cambios a medias si algo falla.
		Categoria nueva = solicitud.categoriaId().equals(p.getCategoria().getId())
				? null
				: categoriaParaAsignar(solicitud.categoriaId());
		List<String> cambios = new ArrayList<>();

		String nombre = solicitud.nombre().trim();
		if (!nombre.equals(p.getNombre())) {
			cambios.add("nombre '" + p.getNombre() + "' → '" + nombre + "'");
			p.setNombre(nombre);
		}
		String descripcion = textoOpcional(solicitud.descripcion());
		if (!Objects.equals(descripcion, p.getDescripcion())) {
			cambios.add("descripción");
			p.setDescripcion(descripcion);
		}
		BigDecimal precio = solicitud.precio().setScale(2);
		if (precio.compareTo(p.getPrecio()) != 0) {
			cambios.add("precio " + p.getPrecio() + " → " + precio);
			p.setPrecio(precio);
		}
		if (nueva != null) {
			cambios.add("categoría '" + p.getCategoria().getNombre() + "' → '" + nueva.getNombre() + "'");
			p.setCategoria(nueva);
		}
		String imagen = textoOpcional(solicitud.imagenUrl());
		if (!Objects.equals(imagen, p.getImagenUrl())) {
			cambios.add("imagen");
			p.setImagenUrl(imagen);
		}
		if (solicitud.umbralMinimo() != null && solicitud.umbralMinimo() != p.getUmbralMinimo()) {
			cambios.add("umbral mínimo " + p.getUmbralMinimo() + " → " + solicitud.umbralMinimo());
			p.setUmbralMinimo(solicitud.umbralMinimo());
		}

		if (!cambios.isEmpty()) {
			registrar(actorId, "PRODUCTO_EDITADO", p, String.join("; ", cambios));
			publicador.publicarProducto(TipoEvento.PRODUCTO_ACTUALIZADO, datos(p));
		}
		return ProductoRespuesta.de(p);
	}

	/** Idempotente: si ya estaba desactivado no hace nada (ni auditoría ni evento). */
	@Transactional
	public ProductoRespuesta desactivar(Long id, Long actorId) {
		return cambiarEstado(id, false, actorId, "PRODUCTO_DESACTIVADO");
	}

	/** Reactiva el producto; si su categoría sigue desactivada, seguirá fuera del menú. */
	@Transactional
	public ProductoRespuesta activar(Long id, Long actorId) {
		return cambiarEstado(id, true, actorId, "PRODUCTO_ACTIVADO");
	}

	private ProductoRespuesta cambiarEstado(Long id, boolean activo, Long actorId, String operacion) {
		Producto p = bloquear(id);
		if (p.isActivo() != activo) {
			p.setActivo(activo);
			registrar(actorId, operacion, p, "Producto '" + p.getNombre() + "'");
			publicador.publicarProducto(TipoEvento.PRODUCTO_ACTUALIZADO, datos(p));
		}
		return ProductoRespuesta.de(p);
	}

	/** La categoría a la que se asigna un producto debe existir (400) y estar activa (409). */
	private Categoria categoriaParaAsignar(Long categoriaId) {
		Categoria c = categorias.findById(categoriaId)
				.orElseThrow(() -> new CampoInvalidoCatalogoException("categoriaId", "La categoría no existe"));
		if (!c.isActiva()) {
			throw new ConflictoCatalogoException("categoriaId", "La categoría está desactivada");
		}
		return c;
	}

	private static boolean enElMenu(Producto p) {
		return p.isActivo() && p.getCategoria().isActiva();
	}

	/** Texto sin espacios sobrantes; vacío equivale a no tener. */
	private static String textoOpcional(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.trim();
		return limpio.isEmpty() ? null : limpio;
	}

	private void registrar(Long actorId, String operacion, Producto p, String detalle) {
		RegistroAuditoria registro = new RegistroAuditoria();
		registro.setUsuario(usuarios.getReferenceById(actorId));
		registro.setTipoOperacion(operacion);
		registro.setEntidad("Producto");
		registro.setEntidadId(p.getId());
		registro.setDetalle(detalle.length() > 2000 ? detalle.substring(0, 2000) : detalle);
		auditoria.save(registro);
	}

	private Producto buscar(Long id) {
		return productos.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Producto " + id + " no existe"));
	}

	private Producto bloquear(Long id) {
		return productos.bloquearPorId(id)
				.orElseThrow(() -> new EntityNotFoundException("Producto " + id + " no existe"));
	}

	private ProductoEventoDatos datos(Producto p) {
		return new ProductoEventoDatos(p.getId(), p.getCategoria().getId(), p.getNombre(), p.getPrecio(),
				p.getStockActual(), p.getUmbralMinimo(), p.isActivo());
	}

}
