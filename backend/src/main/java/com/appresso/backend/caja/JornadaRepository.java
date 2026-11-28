package com.appresso.backend.caja;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JornadaRepository extends JpaRepository<Jornada, Long> {

	/** La jornada abierta más reciente (normalmente hay una sola, RF-49). */
	Optional<Jornada> findFirstByEstadoOrderByFechaAperturaDesc(EstadoJornada estado);

	/** La última jornada, abierta o cerrada. */
	Optional<Jornada> findFirstByOrderByFechaAperturaDesc();

}
