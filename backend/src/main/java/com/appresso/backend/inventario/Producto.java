package com.appresso.backend.inventario;

import java.math.BigDecimal;

import jakarta.persistence.*;

/**
 * Producto del catálogo con su existencia y umbral mínimo (RF-06, RF-08, RF-09).
 */
@Entity
@Table(name = "productos")
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "categoria_id", nullable = false)
	private Categoria categoria;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 255)
	private String descripcion;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal precio;

	@Column(nullable = false)
	private int stockActual = 0;

	@Column(nullable = false)
	private int umbralMinimo = 0;

	@Column(nullable = false)
	private boolean activo = true;

	public Long getId() {
		return id;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public BigDecimal getPrecio() {
		return precio;
	}

	public void setPrecio(BigDecimal precio) {
		this.precio = precio;
	}

	public int getStockActual() {
		return stockActual;
	}

	public void setStockActual(int stockActual) {
		this.stockActual = stockActual;
	}

	public int getUmbralMinimo() {
		return umbralMinimo;
	}

	public void setUmbralMinimo(int umbralMinimo) {
		this.umbralMinimo = umbralMinimo;
	}

	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}

}
