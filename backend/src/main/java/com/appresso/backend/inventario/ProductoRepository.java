package com.appresso.backend.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Producto p where p.id = :id")
	Optional<Producto> bloquearPorId(@Param("id") Long id);

	/** Todos los productos con su categoría ya cargada (el catálogo es pequeño: se filtra en memoria). */
	@Query("select p from Producto p join fetch p.categoria")
	List<Producto> findAllConCategoria();

	/**
	 * Lo que se puede pedir ahora (RF-11, RF-13): producto activo, de categoría activa y con al menos una
	 * unidad. Ya sale ordenado por categoría y luego por producto, sin distinguir mayúsculas.
	 */
	@Query("""
			select p from Producto p join fetch p.categoria c
			where p.activo = true and c.activa = true and p.stockActual > 0
			order by lower(c.nombre), c.id, lower(p.nombre), p.id
			""")
	List<Producto> findDelMenu();

	@Query("""
			select p from Producto p join fetch p.categoria
			where p.activo = true and p.stockActual <= p.umbralMinimo
			order by lower(p.nombre), p.id
			""")
	List<Producto> findConStockBajo();

	/** Cuántos productos tiene cada categoría: filas {@code [categoriaId, cantidad]}. */
	@Query("select p.categoria.id, count(p) from Producto p group by p.categoria.id")
	List<Object[]> contarPorCategoria();

}
