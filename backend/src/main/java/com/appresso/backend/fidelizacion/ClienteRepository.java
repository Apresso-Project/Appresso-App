package com.appresso.backend.fidelizacion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByDocumento(String documento);

	boolean existsByDocumento(String documento);

	/** Comandas del cliente: son registros comerciales que se conservan (Ley 1581, art. 15). */
	@Query("select case when count(c) > 0 then true else false end from Comanda c where c.cliente.id = :id")
	boolean tieneComandas(@Param("id") Long clienteId);

	/** Canjes de recompensas del cliente: también son parte del historial comercial. */
	@Query("select case when count(c) > 0 then true else false end from CanjeRecompensa c where c.cliente.id = :id")
	boolean tieneCanjes(@Param("id") Long clienteId);

	/**
	 * Las notificaciones son solo mensajes para el cliente: se borran al suprimir sus datos.
	 * Es un borrado directo en la base: por eso vacía el contexto de persistencia al terminar
	 * ({@code clearAutomatically}), para que no queden en memoria notificaciones ya borradas que
	 * apunten al cliente. Quien la usa debe cargar el cliente <b>después</b> de llamarla.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Notificacion n where n.cliente.id = :id")
	int borrarNotificaciones(@Param("id") Long clienteId);

}
