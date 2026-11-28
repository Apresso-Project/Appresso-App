package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pago confirmado tal como lo ve el cliente de la API (RF-28). */
public record PagoRespuesta(
		Long id,
		Long comandaId,
		String mesa,
		BigDecimal valor,
		MedioPago medio,
		Instant fecha,
		Long confirmadoPorId,
		String confirmadoPorNombre,
		UUID identificadorOperacion,
		Long jornadaId,
		String comandaEstado) {

	public static PagoRespuesta de(Pago p) {
		return new PagoRespuesta(p.getId(), p.getComanda().getId(), p.getComanda().getMesa().getNombre(),
				p.getValor(), p.getMedio(), p.getFecha(), p.getConfirmadoPor().getId(),
				p.getConfirmadoPor().getNombre(), p.getIdentificadorOperacion(), p.getJornada().getId(),
				p.getComanda().getEstado().name());
	}

}
