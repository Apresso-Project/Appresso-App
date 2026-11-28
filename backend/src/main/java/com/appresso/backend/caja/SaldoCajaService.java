package com.appresso.backend.caja;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saldos de efectivo y de transferencias (RF-31, RF-42). No se guardan: se calculan sumando el
 * libro de {@link MovimientoCaja}, así no pueden descuadrarse. Cada medio se suma solo con sus
 * propios movimientos.
 *
 * <ul>
 * <li><b>Efectivo</b> = saldo inicial de la jornada actual + pagos e ingresos − retiros de esa
 * jornada (la base de una jornada es el saldo inicial de la siguiente, RF-49).</li>
 * <li><b>Transferencias</b> = pagos confirmados e ingresos − retiros de todo el historial: no se
 * reinician al cerrar una jornada.</li>
 * </ul>
 * La jornada actual es la abierta; si no hay ninguna abierta, la última que existió.
 */
@Service
public class SaldoCajaService {

	private final MovimientoCajaRepository movimientos;
	private final JornadaRepository jornadas;

	public SaldoCajaService(MovimientoCajaRepository movimientos, JornadaRepository jornadas) {
		this.movimientos = movimientos;
		this.jornadas = jornadas;
	}

	@Transactional(readOnly = true)
	public SaldosRespuesta saldos() {
		Jornada jornada = jornadas.findFirstByEstadoOrderByFechaAperturaDesc(EstadoJornada.ABIERTA)
				.or(jornadas::findFirstByOrderByFechaAperturaDesc).orElse(null);
		BigDecimal efectivo = BigDecimal.ZERO;
		if (jornada != null) {
			efectivo = jornada.getSaldoInicialEfectivo()
					.add(cero(movimientos.sumarPorMedioEnJornada(MedioPago.EFECTIVO, jornada.getId())));
		}
		BigDecimal transferencias = cero(movimientos.sumarPorMedio(MedioPago.TRANSFERENCIA));
		return new SaldosRespuesta(efectivo, transferencias, jornada == null ? null : jornada.getId(),
				Instant.now());
	}

	private static BigDecimal cero(BigDecimal valor) {
		return valor == null ? BigDecimal.ZERO : valor;
	}

}
