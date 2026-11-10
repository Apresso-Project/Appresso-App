package com.appresso.backend.fidelizacion;

import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;

/**
 * Cliente registrado en el programa de fidelización (RF-50 a RF-52).
 */
@Entity
@Table(name = "clientes")
public class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 80)
	private String apellido;

	@Column(nullable = false, length = 20, unique = true)
	private String documento;

	@Column(nullable = false, length = 20)
	private String celular;

	@Column(nullable = false)
	private boolean aceptoTratamientoDatos;

	private Instant fechaAceptacionDatos;

	/** Saldo de puntos de fidelidad: +1 por pago confirmado (RF-52), se descuenta al canjear (RF-59). No vencen. */
	@Column(nullable = false)
	@Min(0)
	private int puntos = 0;

	@Column(nullable = false, updatable = false)
	private Instant fechaRegistro = Instant.now();

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "usuario_id", unique = true)
	private Usuario usuario;

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getApellido() {
		return apellido;
	}

	public void setApellido(String apellido) {
		this.apellido = apellido;
	}

	public String getDocumento() {
		return documento;
	}

	public void setDocumento(String documento) {
		this.documento = documento;
	}

	public String getCelular() {
		return celular;
	}

	public void setCelular(String celular) {
		this.celular = celular;
	}

	public boolean isAceptoTratamientoDatos() {
		return aceptoTratamientoDatos;
	}

	public void setAceptoTratamientoDatos(boolean aceptoTratamientoDatos) {
		this.aceptoTratamientoDatos = aceptoTratamientoDatos;
	}

	public Instant getFechaAceptacionDatos() {
		return fechaAceptacionDatos;
	}

	public void setFechaAceptacionDatos(Instant fechaAceptacionDatos) {
		this.fechaAceptacionDatos = fechaAceptacionDatos;
	}

	public int getPuntos() {
		return puntos;
	}

	public void setPuntos(int puntos) {
		this.puntos = puntos;
	}

	public Instant getFechaRegistro() {
		return fechaRegistro;
	}

	public void setFechaRegistro(Instant fechaRegistro) {
		this.fechaRegistro = fechaRegistro;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}
