package com.appresso.backend.comandas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

/**
 * Capa entre los controladores y {@link ComandaService}: abre la transacción en la que se arma la
 * respuesta (el proyecto no usa open-in-view, así que las líneas se leen aquí).
 */
@Service
public class ComandaApiService {

	private final ComandaService comandas;
	private final ComandaRepository repositorio;

	public ComandaApiService(ComandaService comandas, ComandaRepository repositorio) {
		this.comandas = comandas;
		this.repositorio = repositorio;
	}

	@Transactional
	public ComandaRespuesta crear(ComandaCrearRequest solicitud, Long meseroId) {
		return ComandaRespuesta.de(comandas.crear(solicitud.mesaId(), meseroId, solicitud.clienteId(),
				solicitud.recompensaId(), solicitud.aItemsSolicitados(), solicitud.totalEsperado()));
	}

	@Transactional
	public ComandaRespuesta cambiarEstado(Long comandaId, EstadoComanda nuevo, Long usuarioId) {
		return ComandaRespuesta.de(comandas.cambiarEstado(comandaId, nuevo, usuarioId));
	}

	@Transactional(readOnly = true)
	public ComandaRespuesta obtener(Long id) {
		return ComandaRespuesta.de(repositorio.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Comanda " + id + " no existe")));
	}

}
