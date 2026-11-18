package com.appresso.backend.caja;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

	/**
	 * Suma de un medio en una jornada: pagos e ingresos suman, retiros restan (RF-31, RF-37).
	 * Devuelve {@code null} si no hay movimientos.
	 */
	@Query("""
			select sum(case when m.tipo = com.appresso.backend.caja.TipoMovimientoCaja.RETIRO
			                then -m.valor else m.valor end)
			from MovimientoCaja m
			where m.medio = :medio and m.jornada.id = :jornadaId
			""")
	BigDecimal sumarPorMedioEnJornada(@Param("medio") MedioPago medio, @Param("jornadaId") Long jornadaId);

	/** Igual que la anterior, pero de todo el historial. Devuelve {@code null} si no hay movimientos. */
	@Query("""
			select sum(case when m.tipo = com.appresso.backend.caja.TipoMovimientoCaja.RETIRO
			                then -m.valor else m.valor end)
			from MovimientoCaja m
			where m.medio = :medio
			""")
	BigDecimal sumarPorMedio(@Param("medio") MedioPago medio);

}
