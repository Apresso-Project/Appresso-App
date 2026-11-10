package com.appresso.backend.fidelizacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.appresso.backend.inventario.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;

/**
 * Recompensa que el cliente puede canjear; se canjea con puntos: puntosMinimos es el umbral para acceder y costoPuntos lo que se descuenta (RF-54, RF-60).
 */
@Entity
@Table(name = "recompensas")
public class Recompensa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(length = 255)
	private String descripcion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoRecompensa tipo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "producto_gratis_id")
	private Producto productoGratis;

	/** Puntos mínimos que el cliente debe tener para acceder (RF-54). Único solo entre recompensas activas: se valida en el servicio. */
	@Column(nullable = false)
	@Min(0)
	private int puntosMinimos;

	/** Puntos que se descuentan al canjearla (RF-54, RF-59). */
	@Column(nullable = false)
	@Min(0)
	private int costoPuntos;

	@Column(nullable = false)
	private boolean usoUnico = false;

	@Column(nullable = false)
	private boolean activa = true;

	private LocalDate vigenteDesde;

	private LocalDate vigenteHasta;

	public Long getId() {
		return id;
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

	public TipoRecompensa getTipo() {
		return tipo;
	}

	public void setTipo(TipoRecompensa tipo) {
		this.tipo = tipo;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public Producto getProductoGratis() {
		return productoGratis;
	}

	public void setProductoGratis(Producto productoGratis) {
		this.productoGratis = productoGratis;
	}

	public int getPuntosMinimos() {
		return puntosMinimos;
	}

	public void setPuntosMinimos(int puntosMinimos) {
		this.puntosMinimos = puntosMinimos;
	}

	public int getCostoPuntos() {
		return costoPuntos;
	}

	public void setCostoPuntos(int costoPuntos) {
		this.costoPuntos = costoPuntos;
	}

	public boolean isUsoUnico() {
		return usoUnico;
	}

	public void setUsoUnico(boolean usoUnico) {
		this.usoUnico = usoUnico;
	}

	public boolean isActiva() {
		return activa;
	}

	public void setActiva(boolean activa) {
		this.activa = activa;
	}

	public LocalDate getVigenteDesde() {
		return vigenteDesde;
	}

	public void setVigenteDesde(LocalDate vigenteDesde) {
		this.vigenteDesde = vigenteDesde;
	}

	public LocalDate getVigenteHasta() {
		return vigenteHasta;
	}

	public void setVigenteHasta(LocalDate vigenteHasta) {
		this.vigenteHasta = vigenteHasta;
	}

}
