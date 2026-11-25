package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.ComandaService.ItemSolicitado;
import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.CategoriaRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoRepository;
import com.appresso.backend.inventario.ProductoService;
import com.appresso.backend.tiemporeal.Destinos;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Criterio de 3.3.1 (SCRUM-88 y SCRUM-89): la comanda aparece en cocina en menos de 3 segundos tras su
 * envío y los cambios de estado se propagan en el mismo plazo (RF-18, RF-21, RF-26).
 *
 * <p>Levanta el servidor, conecta un cliente STOMP real (la pantalla de cocina) y usa el servicio real
 * con commit real: el evento sale solo después del commit. Mide desde que se pide el cambio hasta que
 * el evento llegó <b>y</b> la lista REST de cocina ya lo refleja, que es lo que hace la pantalla.
 * Corre en una sola máquina: mide el servidor, no la red del local (eso se mide en la prueba piloto).</p>
 *
 * <p>Como los datos se guardan de verdad, esta prueba los borra al terminar.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CocinaTiempoRealTest {

	/** Plazo del criterio de aceptación. */
	private static final long PLAZO_MS = 3_000;

	private static final ThreadPoolTaskScheduler PLANIFICADOR = new ThreadPoolTaskScheduler();

	static {
		PLANIFICADOR.setPoolSize(2);
		PLANIFICADOR.setDaemon(true);
		PLANIFICADOR.setThreadNamePrefix("prueba-cocina-");
		PLANIFICADOR.initialize();
	}

	@Value("${local.server.port}")
	int puerto;

	@Autowired WebApplicationContext context;
	@Autowired ComandaService comandaService;
	@Autowired ProductoService productoService;
	@Autowired MesaRepository mesas;
	@Autowired UsuarioRepository usuarios;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired SimpleBrokerMessageHandler broker;
	@Autowired TransactionTemplate transaccion;
	@Autowired JwtService jwt;
	@PersistenceContext EntityManager em;

	MockMvc mvc;
	WebSocketStompClient cliente;
	Usuario mesero;
	Usuario cocinero;
	Usuario cajero;
	Mesa mesa;
	Categoria categoria;
	Producto cafe;
	Producto croissant;
	Long comandaId;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		mesero = usuario(Rol.MESERO);
		cocinero = usuario(Rol.COCINERO);
		cajero = usuario(Rol.CAJERO);

		mesa = new Mesa();
		mesa.setNombre("Cocina " + UUID.randomUUID().toString().substring(0, 8));
		mesas.save(mesa);

		categoria = new Categoria();
		categoria.setNombre("Cocina " + UUID.randomUUID());
		categorias.save(categoria);
		cafe = productoService.crear(categoria.getId(), "Café", "Americano", new BigDecimal("4500.00"), 50, 3);
		croissant = productoService.crear(categoria.getId(), "Croissant", null, new BigDecimal("5500.00"), 50, 3);
	}

	/** Borra lo que la prueba guardó de verdad, de lo más dependiente a lo menos. */
	@AfterEach
	void limpiar() {
		if (cliente != null) {
			cliente.stop();
		}
		transaccion.executeWithoutResult(estado -> {
			long productoCafe = cafe.getId();
			long productoCroissant = croissant.getId();
			em.createQuery("delete from MovimientoInventario m where m.producto.id in (:a, :b)")
					.setParameter("a", productoCafe).setParameter("b", productoCroissant).executeUpdate();
			if (comandaId != null) {
				em.createQuery("delete from HistorialComanda h where h.comanda.id = :id")
						.setParameter("id", comandaId).executeUpdate();
				em.createQuery("delete from DetalleComanda d where d.comanda.id = :id")
						.setParameter("id", comandaId).executeUpdate();
				em.createQuery("delete from Comanda c where c.id = :id").setParameter("id", comandaId).executeUpdate();
			}
			em.createQuery("delete from Mesa m where m.id = :id").setParameter("id", mesa.getId()).executeUpdate();
			em.createQuery("delete from Producto p where p.categoria.id = :id")
					.setParameter("id", categoria.getId()).executeUpdate();
			em.createQuery("delete from Categoria c where c.id = :id").setParameter("id", categoria.getId())
					.executeUpdate();
		});
		usuarios.deleteAllById(List.of(mesero.getId(), cocinero.getId(), cajero.getId()));
	}

	// ------------------------------------------------------------------ la prueba

	@Test
	void laComandaApareceEnCocinaYLosCambiosSePropaganEnMenosDeTresSegundos() throws Exception {
		BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
		StompSession pantallaDeCocina = conectar(cocinero);
		suscribir(pantallaDeCocina, Destinos.COMANDAS, eventos::add);

		// 1. El mesero crea la comanda: nace PENDIENTE y cocina no la muestra todavía (RF-23).
		comandaId = comandaService.crear(mesa.getId(), mesero.getId(),
				List.of(new ItemSolicitado(cafe.getId(), 2, "sin azúcar"))).getId();
		assertThat(enCocina()).as("una comanda sin enviar no está en cocina").isEmpty();

		// 2. El mesero la envía: aparece en cocina como «Pendiente» (RF-18).
		long msEnvio = propagar(eventos, "ENVIADA", () -> comandaService.cambiarEstado(comandaId,
				EstadoComanda.ENVIADA, mesero.getId()), "Pendiente");

		// 3. Agrega un producto a la comanda ya enviada: cocina lo ve marcado como agregado (RF-21).
		long inicio = System.nanoTime();
		comandaService.agregarProducto(comandaId, new ItemSolicitado(croissant.getId(), 1, "tibio"), mesero.getId());
		esperarEvento(eventos, "COMANDA_MODIFICADA", null);
		List<Map<String, Object>> items = itemsEnCocina();
		long msModificacion = milisDesde(inicio);
		assertThat(items).hasSize(2);
		assertThat(items.get(1)).containsEntry("producto", "Croissant").containsEntry("agregadoDespuesDeEnvio", true);

		// 4. El cocinero la pasa a preparación y a lista.
		long msPreparacion = propagar(eventos, "EN_PREPARACION", () -> comandaService.cambiarEstado(comandaId,
				EstadoComanda.EN_PREPARACION, cocinero.getId()), "En preparación");
		long msLista = propagar(eventos, "LISTA", () -> comandaService.cambiarEstado(comandaId,
				EstadoComanda.LISTA, cocinero.getId()), "Lista");

		// 5. Caja la cierra: desaparece de cocina.
		inicio = System.nanoTime();
		comandaService.cambiarEstado(comandaId, EstadoComanda.CERRADA, cajero.getId());
		esperarEvento(eventos, "COMANDA_ESTADO_CAMBIADO", "CERRADA");
		assertThat(enCocina()).as("una comanda cerrada sale de cocina").isEmpty();
		long msCierre = milisDesde(inicio);

		System.out.printf("Cocina (ms): envío=%d, modificación=%d, preparación=%d, lista=%d, cierre=%d%n",
				msEnvio, msModificacion, msPreparacion, msLista, msCierre);
		for (long ms : new long[] { msEnvio, msModificacion, msPreparacion, msLista, msCierre }) {
			assertThat(ms).as("tiempo hasta que cocina lo muestra (ms)").isLessThan(PLAZO_MS);
		}
	}

	// ------------------------------------------------------------------ ayudas

	/**
	 * Ejecuta un cambio de estado y devuelve cuánto tardó cocina en verlo: desde que se pide hasta que llegó
	 * el evento y la lista REST ya muestra la comanda con su nuevo nombre.
	 */
	private long propagar(BlockingQueue<String> eventos, String estado, Runnable cambio, String nombreEnCocina)
			throws Exception {
		long inicio = System.nanoTime();
		cambio.run();
		esperarEvento(eventos, "COMANDA_ESTADO_CAMBIADO", estado);
		List<Map<String, Object>> lista = enCocina();
		long ms = milisDesde(inicio);
		assertThat(lista).as("la comanda sale en cocina").hasSize(1);
		assertThat(lista.get(0).get("estadoCocina")).isEqualTo(nombreEnCocina);
		return ms;
	}

	/** Espera (hasta el plazo) un evento de esta comanda del tipo dado y, si se indica, con ese estado. */
	private void esperarEvento(BlockingQueue<String> eventos, String tipo, String estado) throws InterruptedException {
		Predicate<String> esElQueSeEspera = m -> m.contains("\"tipo\":\"" + tipo + "\"")
				&& m.contains("\"comandaId\":" + comandaId)
				&& (estado == null || m.contains("\"estado\":\"" + estado + "\""));
		long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PLAZO_MS);
		while (System.nanoTime() < limite) {
			String mensaje = eventos.poll(50, TimeUnit.MILLISECONDS);
			if (mensaje != null && esElQueSeEspera.test(mensaje)) {
				return;
			}
		}
		throw new AssertionError("No llegó el evento " + tipo + (estado == null ? "" : " " + estado)
				+ " de la comanda " + comandaId + " en " + PLAZO_MS + " ms");
	}

	private static long milisDesde(long inicioNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
	}

	/** Lo que la pantalla de cocina muestra de esta comanda (vacío si no está). */
	private List<Map<String, Object>> enCocina() throws Exception {
		String json = mvc.perform(get("/api/comandas/cocina").header("Authorization", "Bearer " + jwt.generar(cocinero)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$[?(@.id == " + comandaId + ")]");
	}

	private List<Map<String, Object>> itemsEnCocina() throws Exception {
		String json = mvc.perform(get("/api/comandas/cocina").header("Authorization", "Bearer " + jwt.generar(cocinero)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$[?(@.id == " + comandaId + ")].items[*]");
	}

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre("Cocina " + rol);
		u.setCorreo("cocina." + rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash("no-se-usa-en-esta-prueba");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.save(u);
	}

	private StompSession conectar(Usuario quien) throws Exception {
		cliente = new WebSocketStompClient(new StandardWebSocketClient());
		cliente.setMessageConverter(new TextoPlano());
		cliente.setTaskScheduler(PLANIFICADOR);
		StompHeaders cabeceras = new StompHeaders();
		cabeceras.add("Authorization", "Bearer " + jwt.generar(quien));
		return cliente.connectAsync("ws://localhost:" + puerto + Destinos.ENDPOINT + "/websocket",
				new WebSocketHttpHeaders(), cabeceras, new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	/** Se suscribe y espera a que el broker registre la suscripción, para no publicar antes de tiempo. */
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

	private int suscripciones(String destino) {
		SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		cabeceras.setDestination(destino);
		Message<byte[]> sonda = MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
		return broker.getSubscriptionRegistry().findSubscriptions(sonda).size();
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
