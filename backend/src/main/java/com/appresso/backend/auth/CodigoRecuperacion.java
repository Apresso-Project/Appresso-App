package com.appresso.backend.auth;

import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;

import jakarta.persistence.*;

/**
 * Código de un solo uso para que un cliente restablezca su contraseña (RF-65). Se guarda solo el
 * hash del código. Deja de servir al usarse, al vencer, al agotar los intentos o cuando se pide uno
 * nuevo para la misma cuenta.
 */
@Entity
@Table(name = "codigos_recuperacion")
public class CodigoRecuperacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, length = 100)
	private String codigoHash;

	@Column(nullable = false, updatable = false)
	private Instant fechaCreacion;

	@Column(nullable = false)
	private Instant expira;

	@Column(nullable = false)
	private int intentos = 0;

	@Column(nullable = false)
	private boolean usado = false;

	public Long getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public void setCodigoHash(String codigoHash) {
		this.codigoHash = codigoHash;
	}

	public Instant getFechaCreacion() {
		return fechaCreacion;
	}

	public void setFechaCreacion(Instant fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

	public Instant getExpira() {
		return expira;
	}

	public void setExpira(Instant expira) {
		this.expira = expira;
	}

	public int getIntentos() {
		return intentos;
	}

	public void setIntentos(int intentos) {
		this.intentos = intentos;
	}

	public boolean isUsado() {
		return usado;
	}

	public void setUsado(boolean usado) {
		this.usado = usado;
	}

}
