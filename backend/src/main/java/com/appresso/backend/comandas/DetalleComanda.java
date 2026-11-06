package com.appresso.backend.comandas;

import java.math.BigDecimal;

import com.appresso.backend.inventario.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Línea de una comanda: producto, cantidad y observaciones (RF-16, RF-20, RF-21).
 */
@Entity
@Table(name = "detalles_comanda")
public class DetalleComanda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private Comanda comanda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Column(nullable = false)
	@Min(1)
	private int cantidad;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal precioUnitario;

	@Column(length = 100)
	@Size(max = 100)
	private String observaciones;

	@Column(nullable = false)
	private boolean agregadoDespuesDeEnvio = false;

	public Long getId() {
		return id;
	}

	public Comanda getComanda() {
		return comanda;
	}

	public void setComanda(Comanda comanda) {
		this.comanda = comanda;
	}

	public Producto getProducto() {
		return producto;
	}

	public void setProducto(Producto producto) {
		this.producto = producto;
	}

	public int getCantidad() {
		return cantidad;
	}

	public void setCantidad(int cantidad) {
		this.cantidad = cantidad;
	}

	public BigDecimal getPrecioUnitario() {
		return precioUnitario;
	}

	public void setPrecioUnitario(BigDecimal precioUnitario) {
		this.precioUnitario = precioUnitario;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public void setObservaciones(String observaciones) {
		this.observaciones = observaciones;
	}

	public boolean isAgregadoDespuesDeEnvio() {
		return agregadoDespuesDeEnvio;
	}

	public void setAgregadoDespuesDeEnvio(boolean agregadoDespuesDeEnvio) {
		this.agregadoDespuesDeEnvio = agregadoDespuesDeEnvio;
	}

}
