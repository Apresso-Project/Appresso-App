package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Solicitud de retiro de un cajero pendiente de aprobación (RF-34, RF-36).
 */
@Entity
@Table(name = "solicitudes_retiro")
public class SolicitudRetiro {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cajero_id", nullable = false)
	private Usuario cajero;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valor;

	@Column(nullable = false, length = 255)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoSolicitudRetiro estado = EstadoSolicitudRetiro.PENDIENTE;

	@Column(nullable = false, updatable = false)
	private Instant fechaSolicitud = Instant.now();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resuelta_por_id")
	private Usuario resueltaPor;

	private Instant fechaResolucion;

	public Long getId() {
		return id;
	}

	public Usuario getCajero() {
		return cajero;
	}

	public void setCajero(Usuario cajero) {
		this.cajero = cajero;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public EstadoSolicitudRetiro getEstado() {
		return estado;
	}

	public void setEstado(EstadoSolicitudRetiro estado) {
		this.estado = estado;
	}

	public Instant getFechaSolicitud() {
		return fechaSolicitud;
	}

	public void setFechaSolicitud(Instant fechaSolicitud) {
		this.fechaSolicitud = fechaSolicitud;
	}

	public Usuario getResueltaPor() {
		return resueltaPor;
	}

	public void setResueltaPor(Usuario resueltaPor) {
		this.resueltaPor = resueltaPor;
	}

	public Instant getFechaResolucion() {
		return fechaResolucion;
	}

	public void setFechaResolucion(Instant fechaResolucion) {
		this.fechaResolucion = fechaResolucion;
	}

}
