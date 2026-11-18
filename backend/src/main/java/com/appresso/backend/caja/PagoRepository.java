package com.appresso.backend.caja;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	/** El pago de una comanda: una comanda solo admite uno (RF-28, RF-43). */
	Optional<Pago> findByComandaId(Long comandaId);

	/** ¿Ya hay un pago con este identificador de operación (RNF-11)? */
	boolean existsByIdentificadorOperacion(UUID identificadorOperacion);

}
