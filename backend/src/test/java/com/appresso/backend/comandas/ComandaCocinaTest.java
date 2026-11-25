package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.ComandaService.ItemSolicitado;
import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.CategoriaRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoService;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas de las comandas activas para cocina (SCRUM-88 y SCRUM-89, RF-23, RF-24): qué comandas salen,
 * en qué orden, con qué detalle y quién puede consultarlas. Usan la cadena de seguridad real y se
 * deshacen al terminar. Que los eventos lleguen por WebSocket y a tiempo lo prueba
 * {@code CocinaTiempoRealTest}.
 */
@SpringBootTest
@Transactional
class ComandaCocinaTest {

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired WebApplicationContext context;
	@Autowired ComandaService comandaService;
	@Autowired ProductoService productoService;
	@Autowired MesaRepository mesas;
	@Autowired UsuarioRepository usuarios;
	@Autowired CategoriaRepository categorias;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario mesero;
	Usuario cocinero;
	Usuario cajero;
	Producto cafe;
	Producto croissant;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		mesero = usuario(Rol.MESERO);
		cocinero = usuario(Rol.COCINERO);
		cajero = usuario(Rol.CAJERO);

		Categoria categoria = new Categoria();
		categoria.setNombre("Cocina " + UUID.randomUUID());
		categorias.save(categoria);
		cafe = productoService.crear(categoria.getId(), "Café", "Americano", new BigDecimal("4500.00"), 100, 3);
		croissant = productoService.crear(categoria.getId(), "Croissant", null, new BigDecimal("5500.00"), 100, 3);
	}

	// ------------------------------------------------------------------ utilidades

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre("Cuenta " + rol);
		u.setCorreo(rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash("no-se-usa-en-esta-prueba");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.save(u);
	}

	private Mesa mesa(String nombre) {
		Mesa m = new Mesa();
		m.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 6));
		return mesas.save(m);
	}

	private Comanda crear(Mesa mesa, ItemSolicitado... items) {
		return comandaService.crear(mesa.getId(), mesero.getId(), List.of(items));
	}

	private void avanzar(Comanda c, EstadoComanda nuevo) {
		Usuario quien = switch (nuevo) {
			case ENVIADA -> mesero;
			case EN_PREPARACION, LISTA -> cocinero;
			default -> cajero;
		};
		comandaService.cambiarEstado(c.getId(), nuevo, quien.getId());
	}

	private String cocina(Usuario quien) throws Exception {
		return mvc.perform(get("/api/comandas/cocina").header("Authorization", "Bearer " + jwt.generar(quien)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	/** Las comandas de la respuesta que son de esta prueba, en el orden en que llegaron. */
	private List<Integer> idsDe(String json, Comanda... nuestras) {
		List<Integer> todos = JsonPath.read(json, "$[*].id");
		List<Integer> propios = List.of(nuestras).stream().map(c -> c.getId().intValue()).toList();
		return todos.stream().filter(propios::contains).toList();
	}

	private Map<String, Object> comandaDe(String json, Comanda c) {
		List<Map<String, Object>> lista = JsonPath.read(json, "$[?(@.id == " + c.getId() + ")]");
		assertThat(lista).as("la comanda %d sale en cocina", c.getId()).hasSize(1);
		return lista.get(0);
	}

	// ------------------------------------------------------------------ qué comandas salen (RF-23)

	@Test
	void unaComandaSinEnviarNoApareceEnCocina() throws Exception {
		Comanda pendiente = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));

		assertThat(idsDe(cocina(cocinero), pendiente)).isEmpty();
	}

	@Test
	void alEnviarlaApareceConEstadoPendienteEnCocina() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);

		Map<String, Object> enCocina = comandaDe(cocina(cocinero), c);

		assertThat(enCocina.get("estado")).isEqualTo("ENVIADA");
		assertThat(enCocina.get("estadoCocina")).isEqualTo("Pendiente");
	}

	@Test
	void losTresEstadosDeCocinaSeMuestranConSuNombre() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);
		avanzar(c, EstadoComanda.EN_PREPARACION);
		assertThat(comandaDe(cocina(cocinero), c).get("estadoCocina")).isEqualTo("En preparación");

		avanzar(c, EstadoComanda.LISTA);
		assertThat(comandaDe(cocina(cocinero), c).get("estadoCocina")).isEqualTo("Lista");
	}

	@Test
	void unaComandaCerradaDejaDeVerseEnCocina() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);
		avanzar(c, EstadoComanda.EN_PREPARACION);
		avanzar(c, EstadoComanda.LISTA);
		avanzar(c, EstadoComanda.CERRADA);

		assertThat(idsDe(cocina(cocinero), c)).isEmpty();
	}

	// ------------------------------------------------------------------ orden (RF-23)

	@Test
	void vanDeLaMasAntiguaALaMasRecientePorHoraDeEnvio() throws Exception {
		Comanda primera = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		Comanda segunda = crear(mesa("B"), new ItemSolicitado(cafe.getId(), 1, null));
		Comanda tercera = crear(mesa("C"), new ItemSolicitado(cafe.getId(), 1, null));

		// Se envían en otro orden que el de creación: manda la hora de envío, no la de creación.
		avanzar(segunda, EstadoComanda.ENVIADA);
		pausa();
		avanzar(tercera, EstadoComanda.ENVIADA);
		pausa();
		avanzar(primera, EstadoComanda.ENVIADA);

		assertThat(idsDe(cocina(cocinero), primera, segunda, tercera))
				.containsExactly(segunda.getId().intValue(), tercera.getId().intValue(), primera.getId().intValue());
	}

	/** Separa las horas de envío para que el orden no dependa de la resolución del reloj. */
	private static void pausa() throws InterruptedException {
		Thread.sleep(15);
	}

	@Test
	void elOrdenNoCambiaPorElEstadoDeLaComanda() throws Exception {
		Comanda primera = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		Comanda segunda = crear(mesa("B"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(primera, EstadoComanda.ENVIADA);
		pausa();
		avanzar(segunda, EstadoComanda.ENVIADA);

		// La segunda avanza más que la primera, pero sigue llegando después.
		avanzar(segunda, EstadoComanda.EN_PREPARACION);
		avanzar(segunda, EstadoComanda.LISTA);

		assertThat(idsDe(cocina(cocinero), primera, segunda))
				.containsExactly(primera.getId().intValue(), segunda.getId().intValue());
	}

	// ------------------------------------------------------------------ detalle (RF-24, RF-21)

	@Test
	void muestraMesaProductosCantidadesYObservacionesSinRecortar() throws Exception {
		String observacion = "x".repeat(100);
		Mesa mesa = mesa("Terraza");
		Comanda c = crear(mesa,
				new ItemSolicitado(cafe.getId(), 2, "sin azúcar"),
				new ItemSolicitado(croissant.getId(), 1, observacion));
		avanzar(c, EstadoComanda.ENVIADA);

		Map<String, Object> enCocina = comandaDe(cocina(cocinero), c);

		assertThat(enCocina.get("mesa")).isEqualTo(mesa.getNombre());
		assertThat(((Number) enCocina.get("mesaId")).longValue()).isEqualTo(mesa.getId());
		List<Map<String, Object>> items = JsonPath.read(cocina(cocinero), "$[?(@.id == " + c.getId() + ")].items[*]");
		assertThat(items).hasSize(2);
		assertThat(items.get(0)).containsEntry("producto", "Café").containsEntry("cantidad", 2)
				.containsEntry("observaciones", "sin azúcar");
		assertThat(items.get(1)).containsEntry("producto", "Croissant").containsEntry("cantidad", 1)
				.containsEntry("observaciones", observacion);
	}

	@Test
	void unProductoSinObservacionesLlegaConNull() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);

		List<Map<String, Object>> items = JsonPath.read(cocina(cocinero), "$[?(@.id == " + c.getId() + ")].items[*]");

		assertThat(items.get(0)).containsEntry("observaciones", null);
	}

	@Test
	void unProductoAgregadoDespuesDelEnvioQuedaMarcadoYLosOriginalesNo() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);
		comandaService.agregarProducto(c.getId(), new ItemSolicitado(croissant.getId(), 1, "tibio"), mesero.getId());

		List<Map<String, Object>> items = JsonPath.read(cocina(cocinero), "$[?(@.id == " + c.getId() + ")].items[*]");

		assertThat(items).hasSize(2);
		assertThat(items.get(0)).containsEntry("producto", "Café").containsEntry("agregadoDespuesDeEnvio", false);
		assertThat(items.get(1)).containsEntry("producto", "Croissant").containsEntry("agregadoDespuesDeEnvio", true);
	}

	@Test
	void noLlevaPreciosNiTotalesPorqueCocinaNoLosNecesita() throws Exception {
		Comanda c = crear(mesa("A"), new ItemSolicitado(cafe.getId(), 1, null));
		avanzar(c, EstadoComanda.ENVIADA);

		Map<String, Object> enCocina = comandaDe(cocina(cocinero), c);

		assertThat(enCocina).doesNotContainKeys("total", "subtotal", "descuento");
	}

	// ------------------------------------------------------------------ quién puede consultarlas

	@Test
	void administradorMeseroCajeroYCocineroLaConsultan() throws Exception {
		for (Rol rol : List.of(Rol.ADMINISTRADOR, Rol.MESERO, Rol.CAJERO, Rol.COCINERO)) {
			mvc.perform(get("/api/comandas/cocina").header("Authorization", "Bearer " + jwt.generar(usuario(rol))))
					.andExpect(status().isOk());
		}
	}

	@Test
	void elClienteRecibeError403() throws Exception {
		mvc.perform(get("/api/comandas/cocina").header("Authorization", "Bearer " + jwt.generar(usuario(Rol.CLIENTE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mvc.perform(get("/api/comandas/cocina")).andExpect(status().isUnauthorized());
	}

}
