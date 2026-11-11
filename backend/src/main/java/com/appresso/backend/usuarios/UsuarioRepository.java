package com.appresso.backend.usuarios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByCorreoIgnoreCase(String correo);

	/**
	 * Igual que {@link #findByCorreoIgnoreCase}, pero bloquea la fila hasta que termine la
	 * transacción. Se usa al iniciar sesión: dos intentos simultáneos sobre la misma cuenta no
	 * pueden leer el mismo contador de fallos y saltarse el límite de intentos.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where lower(u.correo) = lower(:correo)")
	Optional<Usuario> buscarPorCorreoBloqueandoFila(@Param("correo") String correo);

}
