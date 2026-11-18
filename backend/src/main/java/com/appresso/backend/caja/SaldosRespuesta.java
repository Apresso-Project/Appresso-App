package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

/** Saldos actuales, siempre por separado: efectivo y transferencias nunca se mezclan (RF-31). */
public record SaldosRespuesta(BigDecimal efectivo, BigDecimal transferencias, Long jornadaId, Instant calculadoEn) {
}
