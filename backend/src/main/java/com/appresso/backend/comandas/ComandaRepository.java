package com.appresso.backend.comandas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comanda c where c.id = :id")
	Optional<Comanda> bloquearPorId(@Param("id") Long id);

	/** ¿La mesa tiene una comanda que aún no se cierra? */
	boolean existsByMesaIdAndEstadoNot(Long mesaId, EstadoComanda estado);
}
