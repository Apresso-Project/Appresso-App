package com.appresso.backend.fidelizacion;

import java.time.LocalDate;

import jakarta.persistence.*;

/**
 * Promoción publicada para los clientes con vigencia (RF-60, RF-62).
 */
@Entity
@Table(name = "promociones")
public class Promocion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String titulo;

	@Column(length = 500)
	private String descripcion;

	@Column(length = 500)
	private String condiciones;

	@Column(nullable = false)
	private LocalDate vigenteDesde;

	@Column(nullable = false)
	private LocalDate vigenteHasta;

	@Column(nullable = false)
	private boolean publicada = false;

	@Column(nullable = false)
	private boolean activa = true;

	public Long getId() {
		return id;
	}

	public String getTitulo() {
		return titulo;
	}

	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public String getCondiciones() {
		return condiciones;
	}

	public void setCondiciones(String condiciones) {
		this.condiciones = condiciones;
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

	public boolean isPublicada() {
		return publicada;
	}

	public void setPublicada(boolean publicada) {
		this.publicada = publicada;
	}

	public boolean isActiva() {
		return activa;
	}

	public void setActiva(boolean activa) {
		this.activa = activa;
	}

}
