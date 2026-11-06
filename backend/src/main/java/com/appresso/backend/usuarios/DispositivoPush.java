package com.appresso.backend.usuarios;

import java.time.Instant;

import jakarta.persistence.*;

/**
 * Token FCM de un dispositivo para enviar notificaciones push (RF-35).
 */
@Entity
@Table(name = "dispositivos_push")
public class DispositivoPush {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, length = 255, unique = true)
	private String tokenFcm;

	@Column(nullable = false)
	private Instant fechaRegistro = Instant.now();

	public Long getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public String getTokenFcm() {
		return tokenFcm;
	}

	public void setTokenFcm(String tokenFcm) {
		this.tokenFcm = tokenFcm;
	}

	public Instant getFechaRegistro() {
		return fechaRegistro;
	}

	public void setFechaRegistro(Instant fechaRegistro) {
		this.fechaRegistro = fechaRegistro;
	}

}
