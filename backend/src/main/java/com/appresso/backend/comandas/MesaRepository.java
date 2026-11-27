package com.appresso.backend.comandas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

	/** El nombre es único sin distinguir mayúsculas ni espacios de los extremos (RF-64). */
	boolean existsByNombreNormalizado(String nombreNormalizado);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Mesa m where m.id = :id")
	Optional<Mesa> bloquearPorId(@Param("id") Long id);

	@Query("""
			select new com.appresso.backend.comandas.MesaRespuesta(m.id, m.nombre, m.estado,
				(select max(c.id) from Comanda c where c.mesa = m and c.estado <> :cerrada))
			from Mesa m
			""")
	List<MesaRespuesta> listarConComandaActiva(@Param("cerrada") EstadoComanda cerrada);

}
