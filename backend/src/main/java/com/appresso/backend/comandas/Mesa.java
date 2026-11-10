package com.appresso.backend.comandas;

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
	}

	public EstadoMesa getEstado() {
		return estado;
	}

	public void setEstado(EstadoMesa estado) {
		this.estado = estado;
	}

}
