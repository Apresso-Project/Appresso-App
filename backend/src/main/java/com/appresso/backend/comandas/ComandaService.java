package com.appresso.backend.comandas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoRepository;
import com.appresso.backend.inventario.ProductoService;
import com.appresso.backend.tiemporeal.ComandaEventoDatos;
import com.appresso.backend.tiemporeal.ComandaEventoDatos.ItemAgregado;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Servicio base de comandas. Cada operación guarda el cambio y, cuando la transacción
 * hace commit, avisa a los dispositivos por tiempo real (SCRUM-28).
 *
 * <p>Reglas tomadas de los RF: RF-15 (una comanda activa por mesa), RF-16 y RF-12 (no pedir más de
 * las existencias ni productos desactivados; observaciones de hasta 100 caracteres), RF-17 (total),
 * RF-18 (al enviar se descuenta el inventario), RF-20 (se puede agregar a una comanda enviada y no
 * pagada) y RF-COMANDA-ESTADO (transiciones por rol). Los controladores, y convertir los errores en
 * código 400, los agregan las historias 3.1.1 / 4.1.1.</p>
 */
@Service
public class ComandaService {

	/** Producto pedido al crear o ampliar una comanda. */
	public record ItemSolicitado(Long productoId, int cantidad, String observaciones) {
	}

	private final ComandaRepository comandas;
	private final MesaRepository mesas;
	private final ProductoRepository productos;
	private final UsuarioRepository usuarios;
	private final HistorialComandaRepository historial;
	private final ProductoService productoService;
	private final PublicadorTiempoReal publicador;

	public ComandaService(ComandaRepository comandas, MesaRepository mesas, ProductoRepository productos,
			UsuarioRepository usuarios, HistorialComandaRepository historial, ProductoService productoService,
			PublicadorTiempoReal publicador) {
		this.comandas = comandas;
		this.mesas = mesas;
		this.productos = productos;
		this.usuarios = usuarios;
		this.historial = historial;
		this.productoService = productoService;
		this.publicador = publicador;
	}

	/** Crea una comanda PENDIENTE, ocupa la mesa y publica COMANDA_CREADA. */
	@Transactional
	public Comanda crear(Long mesaId, Long meseroId, List<ItemSolicitado> items) {
		Mesa mesa = mesas.findById(mesaId).orElseThrow(() -> noExiste("Mesa", mesaId));
		Usuario mesero = usuarios.findById(meseroId).orElseThrow(() -> noExiste("Usuario", meseroId));
		if (comandas.existsByMesaIdAndEstadoNot(mesaId, EstadoComanda.CERRADA)) {
			throw new IllegalStateException("La mesa " + mesa.getNombre() + " ya tiene una comanda abierta");
		}
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("La comanda debe tener al menos un producto");
		}

		Comanda comanda = new Comanda();
		comanda.setMesa(mesa);
		comanda.setMesero(mesero);
		List<ItemAgregado> agregados = new ArrayList<>();
		for (ItemSolicitado item : items) {
			comanda.agregarDetalle(nuevoDetalle(comanda, item, false));
			agregados.add(aItem(item));
		}
		recalcular(comanda);
		mesa.setEstado(EstadoMesa.OCUPADA);
		comandas.save(comanda);
		registrar(comanda, null, EstadoComanda.PENDIENTE, "Comanda creada", mesero);

		publicador.publicarComanda(TipoEvento.COMANDA_CREADA, datos(comanda, null, agregados));
		return comanda;
	}

	/**
	 * Mueve la comanda al estado siguiente (PENDIENTE → ENVIADA → EN_PREPARACION → LISTA → CERRADA).
	 * Cada transición la ejecuta solo su rol: MESERO envía, COCINERO prepara y termina, CAJERO cierra.
	 * Al enviarla se descuenta el inventario (RF-18) y se guarda la hora de envío; al cerrarla se
	 * libera la mesa.
	 */
	@Transactional
	public Comanda cambiarEstado(Long comandaId, EstadoComanda nuevo, Long usuarioId) {
		Comanda comanda = comandas.findById(comandaId).orElseThrow(() -> noExiste("Comanda", comandaId));
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> noExiste("Usuario", usuarioId));
		EstadoComanda anterior = comanda.getEstado();
		if (nuevo.ordinal() != anterior.ordinal() + 1) {
			throw new IllegalStateException("Transición no permitida: " + anterior + " → " + nuevo);
		}
		Rol requerido = switch (nuevo) {
			case ENVIADA -> Rol.MESERO;
			case EN_PREPARACION, LISTA -> Rol.COCINERO;
			default -> Rol.CAJERO;
		};
		if (usuario.getRol() != requerido) {
			throw new IllegalStateException("Solo el rol " + requerido + " puede pasar la comanda a " + nuevo);
		}

		comanda.setEstado(nuevo);
		if (nuevo == EstadoComanda.ENVIADA) {
			if (comanda.getDetalles().isEmpty()) {
				throw new IllegalStateException("No se puede enviar una comanda vacía");
			}
			comanda.setFechaEnvio(Instant.now());
			for (DetalleComanda d : comanda.getDetalles()) {
				productoService.descontarPorVenta(d.getProducto().getId(), d.getCantidad(), comanda, usuario);
			}
		}
		if (nuevo == EstadoComanda.CERRADA) {
			comanda.getMesa().setEstado(EstadoMesa.DISPONIBLE);
		}
		registrar(comanda, anterior, nuevo, "Cambio de estado", usuario);

		publicador.publicarComanda(TipoEvento.COMANDA_ESTADO_CAMBIADO, datos(comanda, anterior, List.of()));
		return comanda;
	}

	/**
	 * Agrega un producto a una comanda que no esté pagada (RF-20). Si ya fue enviada, el detalle queda
	 * marcado como agregado después del envío (RF-21) y se descuenta del inventario de inmediato.
	 * Publica COMANDA_MODIFICADA.
	 */
	@Transactional
	public Comanda agregarProducto(Long comandaId, ItemSolicitado item, Long usuarioId) {
		Comanda comanda = comandas.findById(comandaId).orElseThrow(() -> noExiste("Comanda", comandaId));
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> noExiste("Usuario", usuarioId));
		if (comanda.getEstado() == EstadoComanda.CERRADA) {
			throw new IllegalStateException("La comanda ya está pagada y no se puede modificar");
		}
		boolean despuesDeEnvio = comanda.getEstado() != EstadoComanda.PENDIENTE;
		comanda.agregarDetalle(nuevoDetalle(comanda, item, despuesDeEnvio));
		if (despuesDeEnvio) {
			productoService.descontarPorVenta(item.productoId(), item.cantidad(), comanda, usuario);
		}
		recalcular(comanda);

		publicador.publicarComanda(TipoEvento.COMANDA_MODIFICADA,
				datos(comanda, null, List.of(aItem(item))));
		return comanda;
	}

	private DetalleComanda nuevoDetalle(Comanda comanda, ItemSolicitado item, boolean despuesDeEnvio) {
		if (item.cantidad() < 1) {
			throw new IllegalArgumentException("La cantidad debe ser al menos 1");
		}
		if (item.observaciones() != null && item.observaciones().length() > 100) {
			throw new IllegalArgumentException("Las observaciones no pueden superar 100 caracteres");
		}
		Producto producto = productos.findById(item.productoId())
				.orElseThrow(() -> noExiste("Producto", item.productoId()));
		if (!producto.isActivo()) {
			throw new IllegalStateException("El producto " + producto.getNombre() + " no está disponible");
		}
		// Mientras la comanda no se envía, el stock aún no se ha descontado: se suma lo ya pedido.
		int yaPedido = despuesDeEnvio ? 0 : comanda.getDetalles().stream()
				.filter(d -> d.getProducto().getId().equals(producto.getId()))
				.mapToInt(DetalleComanda::getCantidad).sum();
		if (yaPedido + item.cantidad() > producto.getStockActual()) {
			throw new IllegalStateException("No hay suficiente stock de " + producto.getNombre()
					+ ": quedan " + producto.getStockActual());
		}
		DetalleComanda detalle = new DetalleComanda();
		detalle.setProducto(producto);
		detalle.setCantidad(item.cantidad());
		detalle.setPrecioUnitario(producto.getPrecio());
		detalle.setObservaciones(item.observaciones());
		detalle.setAgregadoDespuesDeEnvio(despuesDeEnvio);
		return detalle;
	}

	private void recalcular(Comanda comanda) {
		BigDecimal subtotal = BigDecimal.ZERO;
		for (DetalleComanda d : comanda.getDetalles()) {
			subtotal = subtotal.add(d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())));
		}
		comanda.setSubtotal(subtotal);
		comanda.setTotal(subtotal.subtract(comanda.getDescuento()));
	}

	private void registrar(Comanda comanda, EstadoComanda anterior, EstadoComanda nuevo, String descripcion,
			Usuario usuario) {
		HistorialComanda h = new HistorialComanda();
		h.setComanda(comanda);
		h.setEstadoAnterior(anterior);
		h.setEstadoNuevo(nuevo);
		h.setDescripcion(descripcion);
		h.setUsuario(usuario);
		historial.save(h);
	}

	private ComandaEventoDatos datos(Comanda c, EstadoComanda anterior, List<ItemAgregado> items) {
		return new ComandaEventoDatos(c.getId(), c.getMesa().getId(), c.getEstado().name(),
				anterior == null ? null : anterior.name(), c.getTotal(), items);
	}

	private ItemAgregado aItem(ItemSolicitado i) {
		return new ItemAgregado(i.productoId(), i.cantidad(), i.observaciones());
	}

	private static EntityNotFoundException noExiste(String que, Long id) {
		return new EntityNotFoundException(que + " " + id + " no existe");
	}

}
