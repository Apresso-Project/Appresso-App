package com.appresso.backend.usuarios;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * Persona que inicia sesión en la app (RF-01, RF-02, RF-03). Tiene un único rol.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, length = 150, unique = true)
	private String correo;

	@Column(nullable = false, length = 100)
	private String contrasenaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Rol rol;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(nullable = false)
	private int intentosFallidos = 0;

	private Instant bloqueadoHasta;

	@Column(nullable = false, updatable = false)
	private Instant fechaCreacion = Instant.now();

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getCorreo() {
		return correo;
	}

	public void setCorreo(String correo) {
		this.correo = correo;
	}

	public String getContrasenaHash() {
		return contrasenaHash;
	}

	public void setContrasenaHash(String contrasenaHash) {
		this.contrasenaHash = contrasenaHash;
	}

	public Rol getRol() {
		return rol;
	}

	public void setRol(Rol rol) {
		this.rol = rol;
	}

	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}

	public int getIntentosFallidos() {
		return intentosFallidos;
	}

	public void setIntentosFallidos(int intentosFallidos) {
		this.intentosFallidos = intentosFallidos;
	}

	public Instant getBloqueadoHasta() {
		return bloqueadoHasta;
	}

	public void setBloqueadoHasta(Instant bloqueadoHasta) {
		this.bloqueadoHasta = bloqueadoHasta;
	}

	public Instant getFechaCreacion() {
		return fechaCreacion;
	}

	public void setFechaCreacion(Instant fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

}
