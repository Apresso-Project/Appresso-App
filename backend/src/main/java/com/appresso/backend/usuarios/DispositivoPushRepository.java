package com.appresso.backend.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

	/** Borra los dispositivos de la cuenta. Vacía el contexto de persistencia: cargar después lo que se vaya a usar. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DispositivoPush d where d.usuario.id = :usuarioId")
	int borrarDeCuenta(@Param("usuarioId") Long usuarioId);

}
