package com.appresso.backend.usuarios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	/** Las cuentas de todos los roles menos uno. La administración de usuarios excluye las de cliente (RF-02). */
	List<Usuario> findByRolNot(Rol rol, Sort orden);

	/**
	 * El id de la cuenta del cliente con ese documento, ya normalizado (ver
	 * {@code DocumentoIdentidad}). Vacío si no hay cliente con ese documento o si no tiene cuenta.
	 */
	@Query("select c.usuario.id from Cliente c where c.documento = :documento")
	Optional<Long> buscarIdDeCuentaPorDocumento(@Param("documento") String documento);

	/** La cuenta con ese id, con su fila bloqueada hasta que termine la transacción (como al buscar por correo). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.id = :id")
	Optional<Usuario> buscarPorIdBloqueandoFila(@Param("id") Long id);

	/**
	 * Igual que {@link #findByCorreoIgnoreCase}, pero bloquea la fila hasta que termine la
	 * transacción. Se usa al iniciar sesión: dos intentos simultáneos sobre la misma cuenta no
	 * pueden leer el mismo contador de fallos y saltarse el límite de intentos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where lower(u.correo) = lower(:correo)")
	Optional<Usuario> buscarPorCorreoBloqueandoFila(@Param("correo") String correo);

	/**
	 * Los administradores activos, con sus filas bloqueadas hasta que termine la transacción. Se usa
	 * antes de quitarle a alguien su condición de administrador activo: dos solicitudes simultáneas
	 * (cada administrador degradando al otro) se atienden una tras otra y la segunda ya ve el
	 * resultado de la primera.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.rol = com.appresso.backend.usuarios.Rol.ADMINISTRADOR and u.activo = true order by u.id")
	List<Usuario> bloquearAdministradoresActivos();

}
