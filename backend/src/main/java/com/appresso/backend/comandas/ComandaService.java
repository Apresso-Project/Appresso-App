package com.appresso.backend.comandas;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.ClienteRepository;
import com.appresso.backend.fidelizacion.Recompensa;
import com.appresso.backend.fidelizacion.RecompensaRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoRepository;
import com.appresso.backend.inventario.ProductoService;
import com.appresso.backend.tiemporeal.ComandaEventoDatos;
import com.appresso.backend.tiemporeal.ComandaEventoDatos.ItemAgregado;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
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
	private final ClienteRepository clientes;
	private final RecompensaRepository recompensas;
	private final HistorialComandaRepository historial;
	private final ProductoService productoService;
	private final PublicadorTiempoReal publicador;

	public ComandaService(ComandaRepository comandas, MesaRepository mesas, ProductoRepository productos,
			UsuarioRepository usuarios, ClienteRepository clientes, RecompensaRepository recompensas,
			HistorialComandaRepository historial, ProductoService productoService, PublicadorTiempoReal publicador) {
		this.comandas = comandas;
		this.mesas = mesas;
		this.productos = productos;
		this.usuarios = usuarios;
		this.clientes = clientes;
		this.recompensas = recompensas;
		this.historial = historial;
		this.productoService = productoService;
		this.publicador = publicador;
	}

	/** Crea una comanda PENDIENTE sin cliente ni descuento. */
	@Transactional
	public Comanda crear(Long mesaId, Long meseroId, List<ItemSolicitado> items) {
		return crear(mesaId, meseroId, null, null, items, null);
	}

	/**
	 * Crea una comanda PENDIENTE, ocupa la mesa y publica COMANDA_CREADA. Calcula subtotales, subtotal,
	 * descuento y total (RF-17). Si se pide un descuento ({@code recompensaId}) y no es autorizado
	 * (recompensa inexistente, inactiva, vencida, que no es descuento, sin cliente o sin puntos mínimos)
	 * se rechaza con 400 y no se crea nada.
	 */
	@Transactional
	public Comanda crear(Long mesaId, Long meseroId, Long clienteId, Long recompensaId, List<ItemSolicitado> items,
			BigDecimal totalEsperado) {
		Mesa mesa = mesas.bloquearPorId(mesaId).orElseThrow(() -> new SolicitudComandaInvalidaException("mesaId",
				"La mesa " + mesaId + " no existe"));
		Usuario mesero = usuarios.findById(meseroId).orElseThrow(() -> noExiste("Usuario", meseroId));
		if (items == null || items.isEmpty()) {
			throw new SolicitudComandaInvalidaException("items", "La comanda debe tener al menos un producto");
		}
		if (comandas.existsByMesaIdAndEstadoNot(mesaId, EstadoComanda.CERRADA)) {
			throw new ConflictoComandaException("mesaId", "La mesa " + mesa.getNombre() + " ya tiene una comanda abierta");
		}
		Cliente cliente = clienteId == null ? null : clientes.findById(clienteId)
				.orElseThrow(() -> new SolicitudComandaInvalidaException("clienteId", "El cliente " + clienteId + " no existe"));
		Recompensa recompensa = null;
		if (recompensaId != null) {
			if (cliente == null) {
				throw new SolicitudComandaInvalidaException("recompensaId", "Para aplicar un descuento indica el cliente");
			}
			recompensa = recompensas.findById(recompensaId).orElseThrow(() -> new SolicitudComandaInvalidaException(
					"recompensaId", "La recompensa " + recompensaId + " no existe"));
			CalculadoraComanda.validarAutorizado(recompensa, cliente.getPuntos(), LocalDate.now());
		}

		Comanda comanda = new Comanda();
		comanda.setMesa(mesa);
		comanda.setMesero(mesero);
		comanda.setCliente(cliente);
		comanda.setRecompensa(recompensa);
		List<ItemAgregado> agregados = new ArrayList<>();
		for (ItemSolicitado item : items) {
			comanda.agregarDetalle(nuevoDetalle(comanda, item, false));
			agregados.add(aItem(item));
		}
		recalcular(comanda);
		if (totalEsperado != null && totalEsperado.compareTo(comanda.getTotal()) != 0) {
			throw new ConflictoComandaException("totalEsperado", "El total cambió. Revisa los precios actualizados.");
		}
		mesa.setEstado(EstadoMesa.OCUPADA);
		comandas.save(comanda);
		registrar(comanda, null, EstadoComanda.PENDIENTE, "Comanda creada", mesero);

		publicador.publicarComanda(TipoEvento.COMANDA_CREADA, datos(comanda, null, agregados));
		return comanda;
	}

	/**
	 * Mueve la comanda al estado siguiente (PENDIENTE → ENVIADA → EN_PREPARACION → LISTA → CERRADA).
	 * Cada transición la ejecuta solo su rol (o el administrador), según {@link PoliticaTransiciones};
	 * una transición inválida lanza {@link TransicionInvalidaException} (400).
	 * Al enviarla se descuenta el inventario (RF-18) y se guarda la hora de envío; al cerrarla se
	 * libera la mesa.
	 */
	@Transactional
	public Comanda cambiarEstado(Long comandaId, EstadoComanda nuevo, Long usuarioId) {
		Comanda comanda = comandas.bloquearPorId(comandaId).orElseThrow(() -> noExiste("Comanda", comandaId));
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> noExiste("Usuario", usuarioId));
		EstadoComanda anterior = comanda.getEstado();
		PoliticaTransiciones.validar(anterior, nuevo, usuario.getRol());

		if (nuevo == EstadoComanda.ENVIADA && comanda.getDetalles().isEmpty()) {
			throw new TransicionInvalidaException("No se puede enviar una comanda vacía");
		}

		comanda.setEstado(nuevo);
		if (nuevo == EstadoComanda.ENVIADA) {
			comanda.setFechaEnvio(Instant.now());
			// Las comandas toman los bloqueos de productos en el mismo orden.
			List<DetalleComanda> detalles = comanda.getDetalles().stream()
					.sorted(Comparator.comparing(d -> d.getProducto().getId()))
					.toList();
			for (DetalleComanda d : detalles) {
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
		Comanda comanda = comandas.bloquearPorId(comandaId).orElseThrow(() -> noExiste("Comanda", comandaId));
		Usuario usuario = usuarios.findById(usuarioId).orElseThrow(() -> noExiste("Usuario", usuarioId));
		if (comanda.getEstado() == EstadoComanda.CERRADA) {
			throw new ConflictoComandaException("La comanda ya está pagada y no se puede modificar");
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
			throw new SolicitudComandaInvalidaException("items", "La cantidad debe ser al menos 1");
		}
		if (item.observaciones() != null && item.observaciones().length() > 100) {
			throw new SolicitudComandaInvalidaException("items", "Las observaciones no pueden superar 100 caracteres");
		}
		Producto producto = (despuesDeEnvio ? productos.bloquearPorId(item.productoId()) : productos.findById(item.productoId()))
				.orElseThrow(() -> new SolicitudComandaInvalidaException("items",
						"El producto " + item.productoId() + " no existe"));
		// Un producto de una categoría desactivada no aparece en el menú (3.1.1) y tampoco se puede pedir.
		if (!producto.isActivo() || !producto.getCategoria().isActiva()) {
			throw new ConflictoComandaException("items", "El producto " + producto.getNombre() + " no está disponible");
		}
		// Mientras la comanda no se envía, el stock aún no se ha descontado: se suma lo ya pedido.
		long yaPedido = despuesDeEnvio ? 0 : comanda.getDetalles().stream()
				.filter(d -> d.getProducto().getId().equals(producto.getId()))
				.mapToLong(DetalleComanda::getCantidad).sum();
		if (yaPedido + item.cantidad() > producto.getStockActual()) {
			throw new ConflictoComandaException("items", "No hay suficiente stock de " + producto.getNombre()
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

	/** Subtotal, descuento y total salen siempre de las líneas y de la recompensa (RF-17). */
	private void recalcular(Comanda comanda) {
		CalculadoraComanda.Importes importes = CalculadoraComanda.calcular(comanda.getDetalles(), comanda.getRecompensa());
		comanda.setSubtotal(importes.subtotal());
		comanda.setDescuento(importes.descuento());
		comanda.setTotal(importes.total());
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
