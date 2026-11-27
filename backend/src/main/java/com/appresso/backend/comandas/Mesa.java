package com.appresso.backend.comandas;

import java.util.Locale;

import jakarta.persistence.*;

/**
 * Mesa del local (RF-15).
 */
@Entity
@Table(name = "mesas")
public class Mesa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30, unique = true)
	private String nombre;

	@Column(name = "nombre_normalizado", nullable = false, length = 90, unique = true)
	private String nombreNormalizado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoMesa estado = EstadoMesa.DISPONIBLE;

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
		this.nombreNormalizado = normalizarNombre(nombre);
	}

	static String normalizarNombre(String nombre) {
		return nombre.strip().toLowerCase(Locale.ROOT);
	}

	public EstadoMesa getEstado() {
		return estado;
	}

	public void setEstado(EstadoMesa estado) {
		this.estado = estado;
	}

}
