package com.appresso.backend.comandas;

import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Registro de cada cambio de estado o modificación de una comanda (RF-27). No se edita.
 */
@Entity
@Immutable
@Table(name = "historial_comanda")
public class HistorialComanda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private Comanda comanda;

	@Enumerated(EnumType.STRING)
	private EstadoComanda estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoComanda estadoNuevo;

	@Column(length = 255)
	private String descripcion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false)
	private Instant fecha = Instant.now();

	public Long getId() {
		return id;
	}

	public Comanda getComanda() {
		return comanda;
	}

	public void setComanda(Comanda comanda) {
		this.comanda = comanda;
	}

	public EstadoComanda getEstadoAnterior() {
		return estadoAnterior;
	}

	public void setEstadoAnterior(EstadoComanda estadoAnterior) {
		this.estadoAnterior = estadoAnterior;
	}

	public EstadoComanda getEstadoNuevo() {
		return estadoNuevo;
	}

	public void setEstadoNuevo(EstadoComanda estadoNuevo) {
		this.estadoNuevo = estadoNuevo;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public Instant getFecha() {
		return fecha;
	}

	public void setFecha(Instant fecha) {
		this.fecha = fecha;
	}

}
