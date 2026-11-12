package com.appresso.backend.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.appresso.backend.tiemporeal.ComandaEventoDatos.ItemAgregado;

/**
 * Pruebas de la sincronización en tiempo real (8.2.1). Levantan el servidor en un
 * puerto libre y se conectan con clientes STOMP reales por WebSocket.
 *
 * <p>La prueba de carga mide el tiempo desde que el servidor envía el evento hasta
 * que el cliente lo recibe, dentro de la misma máquina: sirve para detectar
 * problemas del servidor, no mide la red del local.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TiempoRealTest {

	private static final Pattern FECHA = Pattern.compile("\"fecha\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern SECUENCIA = Pattern.compile("\"secuencia\"\\s*:\\s*(\\d+)");

	@Value("${local.server.port}")
	private int puerto;

	@Autowired
	private PublicadorTiempoReal publicador;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private SimpleBrokerMessageHandler broker;

	/** Los clientes STOMP lo necesitan para latidos y confirmaciones (recibos). */
	private static final ThreadPoolTaskScheduler PLANIFICADOR = new ThreadPoolTaskScheduler();

	static {
		PLANIFICADOR.setPoolSize(2);
		PLANIFICADOR.setDaemon(true);
		PLANIFICADOR.setThreadNamePrefix("prueba-ws-");
		PLANIFICADOR.initialize();
	}

	private final List<WebSocketStompClient> clientes = new ArrayList<>();

	@AfterEach
	void cerrarClientes() {
		clientes.forEach(WebSocketStompClient::stop);
		clientes.clear();
	}

	@Test
	void entregaLosEventosDeComandasALosSuscritos() throws Exception {
		BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
		StompSession sesion = conectar();
		suscribir(sesion, Destinos.COMANDAS, recibidos::add);

		publicador.publicarComanda(TipoEvento.COMANDA_MODIFICADA, comanda(7L, List.of(new ItemAgregado(3L, 2, "sin azúcar"))));

		String mensaje = recibidos.poll(3, TimeUnit.SECONDS);
		assertThat(mensaje).isNotNull()
				.contains("\"tipo\":\"COMANDA_MODIFICADA\"")
				.contains("\"comandaId\":7")
				.contains("\"productoId\":3")
				.contains("sin azúcar");
	}

	@Test
	void cadaTopicoSoloRecibeSusEventos() throws Exception {
		BlockingQueue<String> deComandas = new LinkedBlockingQueue<>();
		BlockingQueue<String> deCatalogo = new LinkedBlockingQueue<>();
		StompSession sesion = conectar();
		suscribir(sesion, Destinos.COMANDAS, deComandas::add);
		suscribir(sesion, Destinos.CATALOGO, deCatalogo::add);

		publicador.publicarProducto(TipoEvento.EXISTENCIAS_ACTUALIZADAS,
				new ProductoEventoDatos(5L, 1L, "Café americano", new BigDecimal("4500.00"), 12, 5, true));

		assertThat(deCatalogo.poll(3, TimeUnit.SECONDS)).contains("\"tipo\":\"EXISTENCIAS_ACTUALIZADAS\"");
		assertThat(deComandas.poll(500, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void noSePublicaUnTipoEnElTopicoEquivocado() {
		assertThatThrownBy(
				() -> publicador.publicarComanda(TipoEvento.PRODUCTO_CREADO, comanda(1L, List.of())))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaTransaccionRevertidaNoEnviaNada() throws Exception {
		BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
		StompSession sesion = conectar();
		suscribir(sesion, Destinos.COMANDAS, recibidos::add);

		new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			publicador.publicarComanda(TipoEvento.COMANDA_CREADA, comanda(1L, List.of()));
			estado.setRollbackOnly();
		});
		assertThat(recibidos.poll(500, TimeUnit.MILLISECONDS)).isNull();

		new TransactionTemplate(transacciones).executeWithoutResult(
				estado -> publicador.publicarComanda(TipoEvento.COMANDA_CREADA, comanda(2L, List.of())));
		assertThat(recibidos.poll(3, TimeUnit.SECONDS)).contains("\"comandaId\":2");
	}

	@Test
	void unClienteQueSeReconectaSigueRecibiendoYVeLaSecuencia() throws Exception {
		BlockingQueue<String> primera = new LinkedBlockingQueue<>();
		StompSession sesion1 = conectar();
		suscribir(sesion1, Destinos.COMANDAS, primera::add);
		publicador.publicarComanda(TipoEvento.COMANDA_CREADA, comanda(1L, List.of()));
		long antes = secuencia(primera.poll(3, TimeUnit.SECONDS));

		// Se cae la conexión: mientras tanto el servidor sigue publicando.
		sesion1.disconnect();
		publicador.publicarComanda(TipoEvento.COMANDA_CREADA, comanda(2L, List.of()));

		// El cliente reconecta y se vuelve a suscribir.
		BlockingQueue<String> segunda = new LinkedBlockingQueue<>();
		StompSession sesion2 = conectar();
		suscribir(sesion2, Destinos.COMANDAS, segunda::add);
		publicador.publicarComanda(TipoEvento.COMANDA_CREADA, comanda(3L, List.of()));
		long despues = secuencia(segunda.poll(3, TimeUnit.SECONDS));

		// El salto en la secuencia le indica al cliente que se perdió un evento y debe
		// volver a consultar el estado por la API REST.
		assertThat(despues).isEqualTo(antes + 2);
	}

	@Test
	void cargaBasicaTreintaDispositivosCincuentaEventos() throws Exception {
		final int dispositivos = 30; // RNF-04
		final int eventos = 50;
		CountDownLatch todos = new CountDownLatch(dispositivos * eventos);
		ConcurrentLinkedQueue<Long> latenciasMs = new ConcurrentLinkedQueue<>();

		for (int i = 0; i < dispositivos; i++) {
			StompSession sesion = conectar();
			suscribir(sesion, Destinos.COMANDAS, mensaje -> {
				latenciasMs.add(Duration.between(fecha(mensaje), Instant.now()).toMillis());
				todos.countDown();
			});
		}

		for (int i = 0; i < eventos; i++) {
			publicador.publicarComanda(TipoEvento.COMANDA_ESTADO_CAMBIADO, comanda(i, List.of()));
		}

		assertThat(todos.await(20, TimeUnit.SECONDS))
				.as("llegaron todos los mensajes (faltan %d)", todos.getCount()).isTrue();

		List<Long> ordenadas = new ArrayList<>(latenciasMs);
		Collections.sort(ordenadas);
		long p95 = ordenadas.get((int) Math.ceil(ordenadas.size() * 0.95) - 1);
		System.out.printf("Carga básica: %d mensajes entregados, latencia p95 = %d ms, máxima = %d ms%n",
				ordenadas.size(), p95, ordenadas.get(ordenadas.size() - 1));
		assertThat(p95).as("p95 en ms").isLessThan(3000);
	}

	// --- Ayudas ---

	private StompSession conectar() throws Exception {
		WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
		cliente.setMessageConverter(new TextoPlano());
		cliente.setTaskScheduler(PLANIFICADOR);
		clientes.add(cliente);
		return cliente.connectAsync("ws://localhost:" + puerto + Destinos.ENDPOINT + "/websocket",
				new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	/**
	 * Se suscribe y espera a que el broker registre la suscripción, para no publicar
	 * antes de tiempo. (El broker simple de Spring no envía recibos STOMP, por eso se
	 * consulta directamente su registro de suscripciones.)
	 */
	private void suscribir(StompSession sesion, String destino, java.util.function.Consumer<String> alRecibir)
			throws InterruptedException {
		int antes = suscripciones(destino);
		sesion.subscribe(destino, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				alRecibir.accept((String) payload);
			}
		});
		long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (suscripciones(destino) <= antes && System.nanoTime() < limite) {
			Thread.sleep(10);
		}
		assertThat(suscripciones(destino)).as("el broker registró la suscripción a " + destino).isGreaterThan(antes);
	}

	/** Cuántas sesiones están suscritas ahora mismo a un destino. */
	private int suscripciones(String destino) {
		SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		cabeceras.setDestination(destino);
		Message<byte[]> sonda = MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
		return broker.getSubscriptionRegistry().findSubscriptions(sonda).size();
	}

	private static ComandaEventoDatos comanda(long id, List<ItemAgregado> items) {
		return new ComandaEventoDatos(id, 1L, "PENDIENTE", "BORRADOR", new BigDecimal("9000.00"), items);
	}

	private static Instant fecha(String mensaje) {
		Matcher m = FECHA.matcher(mensaje);
		assertThat(m.find()).as("el mensaje trae fecha: %s", mensaje).isTrue();
		return Instant.parse(m.group(1));
	}

	private static long secuencia(String mensaje) {
		assertThat(mensaje).isNotNull();
		Matcher m = SECUENCIA.matcher(mensaje);
		assertThat(m.find()).isTrue();
		return Long.parseLong(m.group(1));
	}

	/** Entrega el cuerpo del mensaje tal cual llega (JSON como texto), sin interpretarlo. */
	private static final class TextoPlano implements MessageConverter {

		@Override
		public Object fromMessage(Message<?> message, Class<?> targetClass) {
			Object cuerpo = message.getPayload();
			return cuerpo instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(cuerpo);
		}

		@Override
		public Message<?> toMessage(Object payload, MessageHeaders headers) {
			return null;
		}

	}

}
