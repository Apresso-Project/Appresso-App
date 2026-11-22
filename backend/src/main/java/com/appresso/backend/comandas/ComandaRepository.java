package com.appresso.backend.comandas;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

	/** ¿La mesa tiene una comanda que aún no se cierra? */
	boolean existsByMesaIdAndEstadoNot(Long mesaId, EstadoComanda estado);

	/**
	 * Comandas en los estados dados con su mesa, sus productos y el nombre de cada producto en una sola
	 * consulta (sin una por comanda). De la más antigua a la más reciente por hora de envío; el id
	 * desempata las que se enviaron en el mismo instante.
	 */
	@Query("""
			select distinct c from Comanda c
			join fetch c.mesa
			left join fetch c.detalles d
			left join fetch d.producto
			where c.estado in :estados
			order by c.fechaEnvio asc, c.id asc
			""")
	List<Comanda> findParaCocina(@Param("estados") Collection<EstadoComanda> estados);
}
