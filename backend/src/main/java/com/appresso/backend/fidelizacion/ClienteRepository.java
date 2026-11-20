package com.appresso.backend.fidelizacion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByDocumento(String documento);

	boolean existsByDocumento(String documento);

	Optional<Cliente> findByUsuarioId(Long usuarioId);

}
