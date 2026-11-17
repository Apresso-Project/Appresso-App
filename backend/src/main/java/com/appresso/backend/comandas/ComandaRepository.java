package com.appresso.backend.comandas;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

	/** ¿La mesa tiene una comanda que aún no se cierra? */
	boolean existsByMesaIdAndEstadoNot(Long mesaId, EstadoComanda estado);
}
