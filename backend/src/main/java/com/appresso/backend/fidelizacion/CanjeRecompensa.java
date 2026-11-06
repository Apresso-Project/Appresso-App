package com.appresso.backend.fidelizacion;

import java.math.BigDecimal;
import java.time.Instant;

import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Canje confirmado de una recompensa en una comanda (RF-59).
 */
@Entity
@Table(name = "canjes_recompensa")
public class CanjeRecompensa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recompensa_id", nullable = false)
	private Recompensa recompensa;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private Comanda comanda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valorDescontado;

	@Column(nullable = false, updatable = false)
	private Instant fecha = Instant.now();

	public Long getId() {
		return id;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}

	public Recompensa getRecompensa() {
		return recompensa;
	}

	public void setRecompensa(Recompensa recompensa) {
		this.recompensa = recompensa;
	}

	public Comanda getComanda() {
		return comanda;
	}

	public void setComanda(Comanda comanda) {
		this.comanda = comanda;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

	public BigDecimal getValorDescontado() {
		return valorDescontado;
	}

	public void setValorDescontado(BigDecimal valorDescontado) {
		this.valorDescontado = valorDescontado;
	}

	public Instant getFecha() {
		return fecha;
	}

	public void setFecha(Instant fecha) {
		this.fecha = fecha;
	}

}
