package com.appresso.backend.caja;

import jakarta.persistence.*;

/**
 * Datos configurables del local, como el QR de pago por transferencia (RF-40). Se usa una sola fila.
 */
@Entity
@Table(name = "configuracion_establecimiento")
public class ConfiguracionEstablecimiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 500)
	private String qrPagoUrl;

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getQrPagoUrl() {
		return qrPagoUrl;
	}

	public void setQrPagoUrl(String qrPagoUrl) {
		this.qrPagoUrl = qrPagoUrl;
	}

}
