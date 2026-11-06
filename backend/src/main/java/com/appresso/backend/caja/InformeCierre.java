package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

/**
 * Informe guardado al cerrar una jornada, separado por efectivo y transferencia (RF-45, RF-48).
 */
@Entity
@Table(name = "informes_cierre")
public class InformeCierre {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "jornada_id", unique = true, nullable = false)
	private Jornada jornada;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal saldoInicialEfectivo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal ingresosEfectivo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal egresosEfectivo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal baseReservada;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal saldoFinalEfectivo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal saldoInicialTransferencias;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal ingresosTransferencias;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal egresosTransferencias;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal saldoFinalTransferencias;

	@Column(nullable = false, updatable = false)
	private Instant fechaGeneracion = Instant.now();

	public Long getId() {
		return id;
	}

	public Jornada getJornada() {
		return jornada;
	}

	public void setJornada(Jornada jornada) {
		this.jornada = jornada;
	}

	public BigDecimal getSaldoInicialEfectivo() {
		return saldoInicialEfectivo;
	}

	public void setSaldoInicialEfectivo(BigDecimal saldoInicialEfectivo) {
		this.saldoInicialEfectivo = saldoInicialEfectivo;
	}

	public BigDecimal getIngresosEfectivo() {
		return ingresosEfectivo;
	}

	public void setIngresosEfectivo(BigDecimal ingresosEfectivo) {
		this.ingresosEfectivo = ingresosEfectivo;
	}

	public BigDecimal getEgresosEfectivo() {
		return egresosEfectivo;
	}

	public void setEgresosEfectivo(BigDecimal egresosEfectivo) {
		this.egresosEfectivo = egresosEfectivo;
	}

	public BigDecimal getBaseReservada() {
		return baseReservada;
	}

	public void setBaseReservada(BigDecimal baseReservada) {
		this.baseReservada = baseReservada;
	}

	public BigDecimal getSaldoFinalEfectivo() {
		return saldoFinalEfectivo;
	}

	public void setSaldoFinalEfectivo(BigDecimal saldoFinalEfectivo) {
		this.saldoFinalEfectivo = saldoFinalEfectivo;
	}

	public BigDecimal getSaldoInicialTransferencias() {
		return saldoInicialTransferencias;
	}

	public void setSaldoInicialTransferencias(BigDecimal saldoInicialTransferencias) {
		this.saldoInicialTransferencias = saldoInicialTransferencias;
	}

	public BigDecimal getIngresosTransferencias() {
		return ingresosTransferencias;
	}

	public void setIngresosTransferencias(BigDecimal ingresosTransferencias) {
		this.ingresosTransferencias = ingresosTransferencias;
	}

	public BigDecimal getEgresosTransferencias() {
		return egresosTransferencias;
	}

	public void setEgresosTransferencias(BigDecimal egresosTransferencias) {
		this.egresosTransferencias = egresosTransferencias;
	}

	public BigDecimal getSaldoFinalTransferencias() {
		return saldoFinalTransferencias;
	}

	public void setSaldoFinalTransferencias(BigDecimal saldoFinalTransferencias) {
		this.saldoFinalTransferencias = saldoFinalTransferencias;
	}

	public Instant getFechaGeneracion() {
		return fechaGeneracion;
	}

	public void setFechaGeneracion(Instant fechaGeneracion) {
		this.fechaGeneracion = fechaGeneracion;
	}

}
