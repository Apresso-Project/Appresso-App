package com.appresso.backend.comandas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Pedido de una mesa (RF-15 a RF-22). El pago está en la entidad Pago.
 */
@Entity
@Table(name = "comandas")
public class Comanda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mesa_id", nullable = false)
	private Mesa mesa;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "mesero_id", nullable = false)
	private Usuario mesero;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoComanda estado = EstadoComanda.PENDIENTE;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal descuento = BigDecimal.ZERO;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(nullable = false, unique = true)
	private UUID identificadorOperacion = UUID.randomUUID();

	@Column(nullable = false, updatable = false)
	private Instant fechaCreacion = Instant.now();

	private Instant fechaEnvio;

	@OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL)
	private List<DetalleComanda> detalles = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public Mesa getMesa() {
		return mesa;
	}

	public void setMesa(Mesa mesa) {
		this.mesa = mesa;
	}

	public Usuario getMesero() {
		return mesero;
	}

	public void setMesero(Usuario mesero) {
		this.mesero = mesero;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}

	public EstadoComanda getEstado() {
		return estado;
	}

	public void setEstado(EstadoComanda estado) {
		this.estado = estado;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public void setSubtotal(BigDecimal subtotal) {
		this.subtotal = subtotal;
	}

	public BigDecimal getDescuento() {
		return descuento;
	}

	public void setDescuento(BigDecimal descuento) {
		this.descuento = descuento;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public UUID getIdentificadorOperacion() {
		return identificadorOperacion;
	}

	public void setIdentificadorOperacion(UUID identificadorOperacion) {
		this.identificadorOperacion = identificadorOperacion;
	}

	public Instant getFechaCreacion() {
		return fechaCreacion;
	}

	public void setFechaCreacion(Instant fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

	public Instant getFechaEnvio() {
		return fechaEnvio;
	}

	public void setFechaEnvio(Instant fechaEnvio) {
		this.fechaEnvio = fechaEnvio;
	}

	public List<DetalleComanda> getDetalles() {
		return detalles;
	}

	public void agregarDetalle(DetalleComanda detalle) {
		detalle.setComanda(this);
		detalles.add(detalle);
	}

}
