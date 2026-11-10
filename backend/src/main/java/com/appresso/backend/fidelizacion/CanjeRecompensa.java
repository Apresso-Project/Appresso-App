package com.appresso.backend.fidelizacion;

import java.math.BigDecimal;
import java.time.Instant;

import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Canje confirmado, en una comanda, de una recompensa o de una promoción (exactamente una de las dos) pagado con puntos (RF-59). Es un historial: guarda los puntos cobrados en ese momento.
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recompensa_id")
	private Recompensa recompensa;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "promocion_id")
	private Promocion promocion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private Comanda comanda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	/** Puntos cobrados al cliente; se guardan porque el costo de la oferta puede cambiar después. */
	@Column(nullable = false)
	private int puntosDescontados;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valorDescontado;

	@Column(nullable = false, updatable = false)
	private Instant fecha = Instant.now();

	@PrePersist
	void validarOrigen() {
		if ((recompensa == null) == (promocion == null)) {
			throw new IllegalStateException("Un canje debe tener una recompensa o una promoción, no ambas ni ninguna");
		}
	}

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

	public Promocion getPromocion() {
		return promocion;
	}

	public void setPromocion(Promocion promocion) {
		this.promocion = promocion;
	}

	public int getPuntosDescontados() {
		return puntosDescontados;
	}

	public void setPuntosDescontados(int puntosDescontados) {
		this.puntosDescontados = puntosDescontados;
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
