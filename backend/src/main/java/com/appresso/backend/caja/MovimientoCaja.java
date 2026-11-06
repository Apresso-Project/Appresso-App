package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

import com.appresso.backend.usuarios.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Libro de movimientos de dinero. El valor siempre es positivo; el tipo define si suma o resta (RF-31, RF-37). No se edita.
 */
@Entity
@Immutable
@Table(name = "movimientos_caja")
public class MovimientoCaja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "jornada_id", nullable = false)
	private Jornada jornada;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoMovimientoCaja tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MedioPago medio;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal valor;

	@Column(nullable = false, length = 255)
	private String concepto;

	@Column(nullable = false)
	private Instant fecha = Instant.now();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrado_por_id", nullable = false)
	private Usuario registradoPor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "autorizado_por_id")
	private Usuario autorizadoPor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pago_id")
	private Pago pago;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitud_retiro_id")
	private SolicitudRetiro solicitudRetiro;

	public Long getId() {
		return id;
	}

	public Jornada getJornada() {
		return jornada;
	}

	public void setJornada(Jornada jornada) {
		this.jornada = jornada;
	}

	public TipoMovimientoCaja getTipo() {
		return tipo;
	}

	public void setTipo(TipoMovimientoCaja tipo) {
		this.tipo = tipo;
	}

	public MedioPago getMedio() {
		return medio;
	}

	public void setMedio(MedioPago medio) {
		this.medio = medio;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public String getConcepto() {
		return concepto;
	}

	public void setConcepto(String concepto) {
		this.concepto = concepto;
	}

	public Instant getFecha() {
		return fecha;
	}

	public void setFecha(Instant fecha) {
		this.fecha = fecha;
	}

	public Usuario getRegistradoPor() {
		return registradoPor;
	}

	public void setRegistradoPor(Usuario registradoPor) {
		this.registradoPor = registradoPor;
	}

	public Usuario getAutorizadoPor() {
		return autorizadoPor;
	}

	public void setAutorizadoPor(Usuario autorizadoPor) {
		this.autorizadoPor = autorizadoPor;
	}

	public Pago getPago() {
		return pago;
	}

	public void setPago(Pago pago) {
		this.pago = pago;
	}

	public SolicitudRetiro getSolicitudRetiro() {
		return solicitudRetiro;
	}

	public void setSolicitudRetiro(SolicitudRetiro solicitudRetiro) {
		this.solicitudRetiro = solicitudRetiro;
	}

}
