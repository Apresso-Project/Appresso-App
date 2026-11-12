package com.appresso.backend.tiemporeal;

/**
 * Sobre común de todos los mensajes que el servidor envía por STOMP.
 *
 * @param id        identificador único del evento (para ignorar repetidos)
 * @param secuencia número creciente de 1 en 1 mientras el servidor no se reinicie;
 *                  si el cliente nota un salto, o un número menor al último,
 *                  debe volver a consultar el estado por la API REST
 * @param tipo      qué ocurrió
 * @param fecha     momento en que el servidor envió el evento (ISO-8601, UTC)
 * @param datos     contenido: {@link ComandaEventoDatos} o {@link ProductoEventoDatos}
 */
public record EventoTiempoReal(
		String id,
		long secuencia,
		TipoEvento tipo,
		String fecha,
		Object datos) {

}
