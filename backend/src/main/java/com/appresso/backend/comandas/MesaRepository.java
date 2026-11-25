package com.appresso.backend.comandas;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

	/** El nombre es único sin distinguir mayúsculas ni espacios de los extremos (RF-64). */
	boolean existsByNombreIgnoreCase(String nombre);

}
