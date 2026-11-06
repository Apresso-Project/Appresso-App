package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Jornada de caja: se abre con una base y se cierra con un informe (RF-44, RF-47, RF-49).
 */
@Entity
@Table(name = "jornadas")
public class Jornada {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoJornada estado = EstadoJornada.ABIERTA;

	@Column(nullable = false)
	private Instant fechaApertura = Instant.now();

	private Instant fechaCierre;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal saldoInicialEfectivo = BigDecimal.ZERO;

	@Column(precision = 14, scale = 2)
	private BigDecimal baseSiguienteJornada;

	@Column(precision = 14, scale = 2)
	private BigDecimal retiroExcedente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "abierta_por_id", nullable = false)
	private Usuario abiertaPor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cerrada_por_id")
	private Usuario cerradaPor;

	public Long getId() {
		return id;
	}

	public EstadoJornada getEstado() {
		return estado;
	}

	public void setEstado(EstadoJornada estado) {
		this.estado = estado;
	}

	public Instant getFechaApertura() {
		return fechaApertura;
	}

	public void setFechaApertura(Instant fechaApertura) {
		this.fechaApertura = fechaApertura;
	}

	public Instant getFechaCierre() {
		return fechaCierre;
	}

	public void setFechaCierre(Instant fechaCierre) {
		this.fechaCierre = fechaCierre;
	}

	public BigDecimal getSaldoInicialEfectivo() {
		return saldoInicialEfectivo;
	}

	public void setSaldoInicialEfectivo(BigDecimal saldoInicialEfectivo) {
		this.saldoInicialEfectivo = saldoInicialEfectivo;
	}

	public BigDecimal getBaseSiguienteJornada() {
		return baseSiguienteJornada;
	}

	public void setBaseSiguienteJornada(BigDecimal baseSiguienteJornada) {
		this.baseSiguienteJornada = baseSiguienteJornada;
	}

	public BigDecimal getRetiroExcedente() {
		return retiroExcedente;
	}

	public void setRetiroExcedente(BigDecimal retiroExcedente) {
		this.retiroExcedente = retiroExcedente;
	}

	public Usuario getAbiertaPor() {
		return abiertaPor;
	}

	public void setAbiertaPor(Usuario abiertaPor) {
		this.abiertaPor = abiertaPor;
	}

	public Usuario getCerradaPor() {
		return cerradaPor;
	}

	public void setCerradaPor(Usuario cerradaPor) {
		this.cerradaPor = cerradaPor;
	}

}
