package com.appresso.backend.auditoria;

import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Registro de auditoría de operaciones críticas (RNF-12). No se edita ni se elimina.
 */
@Entity
@Immutable
@Table(name = "registros_auditoria")
public class RegistroAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	@Column(nullable = false, updatable = false)
	private Instant fecha = Instant.now();

	@Column(nullable = false, length = 60)
	private String tipoOperacion;

	@Column(nullable = false, length = 60)
	private String entidad;

	private Long entidadId;

	@Column(length = 2000)
	private String detalle;

	public Long getId() {
		return id;
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

	public String getTipoOperacion() {
		return tipoOperacion;
	}

	public void setTipoOperacion(String tipoOperacion) {
		this.tipoOperacion = tipoOperacion;
	}

	public String getEntidad() {
		return entidad;
	}

	public void setEntidad(String entidad) {
		this.entidad = entidad;
	}

	public Long getEntidadId() {
		return entidadId;
	}

	public void setEntidadId(Long entidadId) {
		this.entidadId = entidadId;
	}

	public String getDetalle() {
		return detalle;
	}

	public void setDetalle(String detalle) {
		this.detalle = detalle;
	}

}
