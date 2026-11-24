package com.appresso.backend.fidelizacion;

import java.time.Instant;

import com.appresso.backend.auditoria.RegistroAuditoria;

/**
 * Un evento del historial de consentimiento de un cliente (Ley 1581, trazabilidad): el
 * otorgamiento de la autorización o la supresión de sus datos. No contiene datos personales.
 *
 * @param operacion {@code CONSENTIMIENTO_DATOS_1581} o {@code SUPRESION_DATOS_LEY_1581}
 * @param usuarioId la cuenta del titular que hizo la operación (anónima si ya se suprimió)
 */
public record ConsentimientoRespuesta(String operacion, Instant fecha, Long usuarioId, String detalle) {

	static ConsentimientoRespuesta desde(RegistroAuditoria r) {
		return new ConsentimientoRespuesta(r.getTipoOperacion(), r.getFecha(),
				r.getUsuario() != null ? r.getUsuario().getId() : null, r.getDetalle());
	}

}
