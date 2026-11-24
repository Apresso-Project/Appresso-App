package com.appresso.backend.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	/** El último código emitido para la cuenta, usado o no. Sirve para no reenviar en ráfaga. */
	Optional<CodigoRecuperacion> findFirstByUsuarioIdOrderByFechaCreacionDescIdDesc(Long usuarioId);

	/** Invalida los códigos sin usar de la cuenta: solo sirve el último que se pidió. */
	@Modifying
	@Query("update CodigoRecuperacion c set c.usado = true where c.usuario.id = :usuarioId and c.usado = false")
	int invalidarSinUsar(@Param("usuarioId") Long usuarioId);

	/**
	 * Borra todos los códigos de la cuenta, al suprimir los datos del cliente (Ley 1581). Vacía el
	 * contexto de persistencia: cargar después lo que se vaya a usar.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CodigoRecuperacion c where c.usuario.id = :usuarioId")
	int borrarDeCuenta(@Param("usuarioId") Long usuarioId);

	/**
	 * Los códigos sin usar de la cuenta, el más reciente primero, con sus filas bloqueadas hasta que
	 * termine la transacción: dos intentos simultáneos no pueden saltarse el límite de intentos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CodigoRecuperacion c where c.usuario.id = :usuarioId and c.usado = false "
			+ "order by c.fechaCreacion desc, c.id desc")
	List<CodigoRecuperacion> bloquearSinUsar(@Param("usuarioId") Long usuarioId);

}
