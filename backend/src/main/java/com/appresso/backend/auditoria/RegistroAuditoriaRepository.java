package com.appresso.backend.auditoria;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

	/** Historial de una entidad, del más antiguo al más reciente (p. ej. consentimiento de un cliente). */
	List<RegistroAuditoria> findByEntidadAndEntidadIdOrderByFechaAscIdAsc(String entidad, Long entidadId);

}
