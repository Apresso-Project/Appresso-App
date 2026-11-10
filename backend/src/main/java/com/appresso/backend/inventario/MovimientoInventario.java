package com.appresso.backend.inventario;

import java.time.Instant;

import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Entrada, salida o ajuste de inventario con su responsable (RF-07, RF-14). Un registro no se modifica.
 */
@Entity
@Immutable
@Table(name = "movimientos_inventario")
public class MovimientoInventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoMovimientoInventario tipo;

	@Column(nullable = false)
	private int cantidad;

	@Column(nullable = false)
	private int stockResultante;

	@Column(length = 255)
	private String justificacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "comanda_id")
	private Comanda comanda;

	@Column(nullable = false)
	private Instant fecha = Instant.now();

	public Long getId() {
		return id;
	}

	public Producto getProducto() {
		return producto;
	}

	public void setProducto(Producto producto) {
		this.producto = producto;
	}

	public TipoMovimientoInventario getTipo() {
		return tipo;
	}

	public void setTipo(TipoMovimientoInventario tipo) {
		this.tipo = tipo;
	}

	public int getCantidad() {
		return cantidad;
	}

	public void setCantidad(int cantidad) {
		this.cantidad = cantidad;
	}

	public int getStockResultante() {
		return stockResultante;
	}

	public void setStockResultante(int stockResultante) {
		this.stockResultante = stockResultante;
	}

	public String getJustificacion() {
		return justificacion;
	}

	public void setJustificacion(String justificacion) {
		this.justificacion = justificacion;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public Comanda getComanda() {
		return comanda;
	}

	public void setComanda(Comanda comanda) {
		this.comanda = comanda;
	}

	public Instant getFecha() {
		return fecha;
	}

	public void setFecha(Instant fecha) {
		this.fecha = fecha;
	}

}
