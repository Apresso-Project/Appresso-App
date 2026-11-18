package com.appresso.backend.caja;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	/** El pago de una comanda: una comanda solo admite uno (RF-28, RF-43). */
	Optional<Pago> findByComandaId(Long comandaId);

}
