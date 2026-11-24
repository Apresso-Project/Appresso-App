package com.appresso.backend.inventario;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

	@Query("""
			select m from MovimientoInventario m join fetch m.producto p join fetch m.usuario
			where m.fecha >= coalesce(:desde, m.fecha)
			  and m.fecha <= coalesce(:hasta, m.fecha)
			  and locate(lower(:producto), lower(p.nombre)) > 0
			order by m.fecha desc, m.id desc
			""")
	List<MovimientoInventario> consultar(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
			@Param("producto") String producto);
}
