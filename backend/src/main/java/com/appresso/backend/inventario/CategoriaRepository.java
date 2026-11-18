package com.appresso.backend.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

	/** El nombre de una categoría es único sin distinguir mayúsculas (la base solo lo exige exacto). */
	boolean existsByNombreIgnoreCase(String nombre);

	boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

}
