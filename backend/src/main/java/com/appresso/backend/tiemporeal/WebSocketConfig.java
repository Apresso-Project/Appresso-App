package com.appresso.backend.tiemporeal;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.appresso.backend.seguridad.AutenticacionStomp;

/**
 * Configura WebSocket con STOMP sobre SockJS (8.2.1).
 *
 * <ul>
 * <li>Punto de conexión: {@code /ws} (SockJS). Los clientes Android pueden usar
 * WebSocket puro en {@code /ws/websocket}.</li>
 * <li>Broker en memoria para {@code /topic/**}. Sirve para un solo servidor;
 * si algún día hay varios, habría que cambiarlo por un broker externo.</li>
 * <li>Latidos cada 10 s en ambos sentidos: así cliente y servidor notan una
 * conexión caída aunque la red no avise.</li>
 * <li>Autenticación: el {@code CONNECT} debe traer {@code Authorization: Bearer <token>}
 * (ver {@link com.appresso.backend.seguridad.AutenticacionStomp}).</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private static final long LATIDO_MS = 10_000;

	private final AutenticacionStomp autenticacion;

	public WebSocketConfig(AutenticacionStomp autenticacion) {
		this.autenticacion = autenticacion;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Las apps nativas no envían "Origin"; el patrón abierto solo afecta a navegadores.
		// El apretón de manos HTTP es público; el token JWT se valida en el CONNECT de STOMP
		// (ver configureClientInboundChannel y AutenticacionStomp).
		registry.addEndpoint(Destinos.ENDPOINT)
				.setAllowedOriginPatterns("*")
				.withSockJS();
	}

	/** Todo marco que manda el cliente pasa primero por la autenticación del CONNECT. */
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(autenticacion);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		ThreadPoolTaskScheduler latidos = new ThreadPoolTaskScheduler();
		latidos.setPoolSize(1);
		latidos.setThreadNamePrefix("ws-latidos-");
		latidos.setDaemon(true);
		latidos.initialize();

		registry.enableSimpleBroker(Destinos.PREFIJO_TOPICOS)
				.setHeartbeatValue(new long[] { LATIDO_MS, LATIDO_MS })
				.setTaskScheduler(latidos);
		registry.setApplicationDestinationPrefixes("/app");
	}

}
