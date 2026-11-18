package com.appresso.backend.inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

	/** Todos los productos con su categoría ya cargada (el catálogo es pequeño: se filtra en memoria). */
	@Query("select p from Producto p join fetch p.categoria")
	List<Producto> findAllConCategoria();

	/** Cuántos productos tiene cada categoría: filas {@code [categoriaId, cantidad]}. */
	@Query("select p.categoria.id, count(p) from Producto p group by p.categoria.id")
	List<Object[]> contarPorCategoria();

}
