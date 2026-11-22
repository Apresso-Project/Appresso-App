package com.appresso.backend.inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

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

	/** Cuántos productos tiene cada categoría: filas {@code [categoriaId, cantidad]}. */
	@Query("select p.categoria.id, count(p) from Producto p group by p.categoria.id")
	List<Object[]> contarPorCategoria();

}
