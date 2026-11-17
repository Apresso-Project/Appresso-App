package com.appresso.backend.tiemporeal;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puerta única para que los servicios avisen a los dispositivos conectados.
 *
 * <p>Uso desde un servicio, dentro de su método transaccional:</p>
 * <pre>
 * publicador.publicarComanda(TipoEvento.COMANDA_CREADA, datos);
 * </pre>
 *
 * <p>Si hay una transacción en curso, el mensaje se envía <b>solo después de que
 * haga commit</b>: así nunca se avisa de un cambio que luego se revirtió (RNF-10).
 * Si no hay transacción, se envía de inmediato.</p>
 */
@Component
public class PublicadorTiempoReal {

	/** Evento interno: se guarda hasta el commit y luego se envía. */
	record EventoPendiente(TipoEvento tipo, Object datos) {
	}

	private final ApplicationEventPublisher eventos;
	private final SimpMessagingTemplate mensajeria;
	private final AtomicLong secuencia = new AtomicLong();

	public PublicadorTiempoReal(ApplicationEventPublisher eventos, SimpMessagingTemplate mensajeria) {
		this.eventos = eventos;
		this.mensajeria = mensajeria;
	}

	public void publicarComanda(TipoEvento tipo, ComandaEventoDatos datos) {
		exigirDestino(tipo, Destinos.COMANDAS);
		eventos.publishEvent(new EventoPendiente(tipo, datos));
	}

	public void publicarProducto(TipoEvento tipo, ProductoEventoDatos datos) {
		exigirDestino(tipo, Destinos.CATALOGO);
		eventos.publishEvent(new EventoPendiente(tipo, datos));
	}

	/**
	 * Envía el evento. Es {@code synchronized} para que el número de secuencia
	 * coincida con el orden real de envío.
	 */
	@TransactionalEventListener(fallbackExecution = true)
	synchronized void enviar(EventoPendiente pendiente) {
		EventoTiempoReal evento = new EventoTiempoReal(
				UUID.randomUUID().toString(),
				secuencia.incrementAndGet(),
				pendiente.tipo(),
				Instant.now().toString(),
				pendiente.datos());
		mensajeria.convertAndSend(pendiente.tipo().destino(), evento);
	}

	private static void exigirDestino(TipoEvento tipo, String esperado) {
		if (!tipo.destino().equals(esperado)) {
			throw new IllegalArgumentException(
					"El evento " + tipo + " no se publica en " + esperado + " sino en " + tipo.destino());
		}
	}

}
