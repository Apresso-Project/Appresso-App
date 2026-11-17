package com.appresso.backend.inventario;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.tiemporeal.ProductoEventoDatos;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Servicio base de productos e inventario. Publica por tiempo real los cambios del catálogo y
 * de las existencias (SCRUM-28). Controladores y roles los agregan las historias de inventario.
 */
@Service
public class ProductoService {

	private final ProductoRepository productos;
	private final CategoriaRepository categorias;
	private final MovimientoInventarioRepository movimientos;
	private final UsuarioRepository usuarios;
	private final PublicadorTiempoReal publicador;

	public ProductoService(ProductoRepository productos, CategoriaRepository categorias,
			MovimientoInventarioRepository movimientos, UsuarioRepository usuarios,
			PublicadorTiempoReal publicador) {
		this.productos = productos;
		this.categorias = categorias;
		this.movimientos = movimientos;
		this.usuarios = usuarios;
		this.publicador = publicador;
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
		Producto p = buscar(productoId);
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
		if (tipo == TipoMovimientoInventario.VENTA) {
			throw new IllegalArgumentException("Las ventas se descuentan al enviar la comanda, no a mano");
		}
		if (justificacion == null || justificacion.isBlank()) {
			throw new IllegalArgumentException("El movimiento necesita una justificación (RF-07)");
		}
		Producto p = buscar(productoId);
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new EntityNotFoundException("Usuario " + usuarioId + " no existe"));
		return mover(p, tipo, cantidad, justificacion, usuario, null);
	}

	/**
	 * Descuenta del inventario lo que se vendió en una comanda (RF-18, RF-20): guarda un movimiento
	 * de tipo VENTA ligado a la comanda y publica EXISTENCIAS_ACTUALIZADAS.
	 */
	@Transactional
	public Producto descontarPorVenta(Long productoId, int cantidad, Comanda comanda, Usuario usuario) {
		return mover(buscar(productoId), TipoMovimientoInventario.VENTA, cantidad,
				"Venta en comanda " + comanda.getId(), usuario, comanda);
	}

	private Producto mover(Producto p, TipoMovimientoInventario tipo, int cantidad, String justificacion,
			Usuario usuario, Comanda comanda) {
		int delta = tipo == TipoMovimientoInventario.ENTRADA_MANUAL ? cantidad : -cantidad;
		int resultante = p.getStockActual() + delta;
		if (resultante < 0) {
			throw new IllegalStateException("Existencias insuficientes de " + p.getNombre()
					+ ": hay " + p.getStockActual() + " y se piden " + cantidad);
		}
		p.setStockActual(resultante);

		MovimientoInventario m = new MovimientoInventario();
		m.setProducto(p);
		m.setTipo(tipo);
		m.setCantidad(cantidad);
		m.setStockResultante(resultante);
		m.setJustificacion(justificacion);
		m.setUsuario(usuario);
		m.setComanda(comanda);
		movimientos.save(m);

		publicador.publicarProducto(TipoEvento.EXISTENCIAS_ACTUALIZADAS, datos(p));
		return p;
	}

	private Producto buscar(Long id) {
		return productos.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Producto " + id + " no existe"));
	}

	private ProductoEventoDatos datos(Producto p) {
		return new ProductoEventoDatos(p.getId(), p.getCategoria().getId(), p.getNombre(), p.getPrecio(),
				p.getStockActual(), p.getUmbralMinimo(), p.isActivo());
	}

}
