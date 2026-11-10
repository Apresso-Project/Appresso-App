package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;

/**
 * Pago confirmado de una comanda; una comanda solo admite un pago (RF-28, RF-30, RF-43). Un pago solo se registra al confirmarlo: mostrar el QR no crea ningún registro.
 */
@Entity
@Table(name = "pagos")
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", unique = true, nullable = false)
	private Comanda comanda;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "jornada_id", nullable = false)
	private Jornada jornada;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MedioPago medio;

	@Column(nullable = false, unique = true)
	private UUID identificadorOperacion = UUID.randomUUID();

	/** Fecha y hora en que se confirmó el pago (RF-28). */
	@Column(nullable = false, updatable = false)
	private Instant fecha = Instant.now();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "confirmado_por_id", nullable = false)
	private Usuario confirmadoPor;

	public Long getId() {
		return id;
	}

	public Comanda getComanda() {
		return comanda;
	}

	public void setComanda(Comanda comanda) {
		this.comanda = comanda;
	}

	public Jornada getJornada() {
		return jornada;
	}

	public void setJornada(Jornada jornada) {
		this.jornada = jornada;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public MedioPago getMedio() {
		return medio;
	}

	public void setMedio(MedioPago medio) {
		this.medio = medio;
	}

	public UUID getIdentificadorOperacion() {
		return identificadorOperacion;
	}

	public void setIdentificadorOperacion(UUID identificadorOperacion) {
		this.identificadorOperacion = identificadorOperacion;
	}

	public Instant getFecha() {
		return fecha;
	}

	public void setFecha(Instant fecha) {
		this.fecha = fecha;
	}

	public Usuario getConfirmadoPor() {
		return confirmadoPor;
	}

	public void setConfirmadoPor(Usuario confirmadoPor) {
		this.confirmadoPor = confirmadoPor;
	}

}
