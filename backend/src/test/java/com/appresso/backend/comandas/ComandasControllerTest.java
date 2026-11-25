package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.ClienteRepository;
import com.appresso.backend.fidelizacion.Recompensa;
import com.appresso.backend.fidelizacion.RecompensaRepository;
import com.appresso.backend.fidelizacion.TipoRecompensa;
import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.CategoriaRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoRepository;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * SCRUM-96: mesas, creación de comandas con cálculos, ciclo de vida completo y contrato JSON de punta a
 * punta (HU 4.1.1), con la cadena de seguridad real. Cada prueba corre en una transacción que se
 * deshace; el publicador de tiempo real es un doble (lo que viaja por WebSocket lo prueba TiempoRealTest).
 */
@SpringBootTest
@Transactional
class ComandasControllerTest {

	private static final String CLAVE = "Clave-Segura-1";

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired MesaRepository mesas;
	@Autowired ComandaRepository comandas;
	@Autowired ComandaService comandaService;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired ClienteRepository clientes;
	@Autowired RecompensaRepository recompensas;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario admin;
	Usuario mesero;
	Usuario cajero;
	Usuario cocinero;
	Usuario clienteApp;
	Mesa mesa;
	Producto cafe;
	Producto pan;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = cuenta(Rol.ADMINISTRADOR);
		mesero = cuenta(Rol.MESERO);
		cajero = cuenta(Rol.CAJERO);
		cocinero = cuenta(Rol.COCINERO);
		clienteApp = cuenta(Rol.CLIENTE);
		mesa = mesa();
		Categoria categoria = new Categoria();
		categoria.setNombre(unico("Cat"));
		categorias.save(categoria);
		cafe = producto(categoria, "4500.00", 10);
		pan = producto(categoria, "2500.25", 10);
	}

	// ------------------------------------------------------------------ utilidades

	private Usuario cuenta(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre("Cuenta " + rol);
		u.setCorreo(rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash(codificador.encode(CLAVE));
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.save(u);
	}

	private static String unico(String base) {
		return base + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private Mesa mesa() {
		Mesa m = new Mesa();
		m.setNombre(unico("M"));
		return mesas.saveAndFlush(m);
	}

	private Producto producto(Categoria categoria, String precio, int stock) {
		Producto p = new Producto();
		p.setCategoria(categoria);
		p.setNombre(unico("Prod"));
		p.setPrecio(new BigDecimal(precio));
		p.setStockActual(stock);
		p.setActivo(true);
		return productos.save(p);
	}

	private Cliente cliente(int puntos) {
		Cliente c = new Cliente();
		c.setNombre("Marta");
		c.setApellido("Lopez");
		c.setDocumento(String.valueOf(1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L)));
		c.setCelular("3209998877");
		c.setAceptoTratamientoDatos(true);
		c.setPuntos(puntos);
		return clientes.saveAndFlush(c);
	}

	private Recompensa recompensa(TipoRecompensa tipo, String valor, LocalDate desde, LocalDate hasta) {
		Recompensa r = new Recompensa();
		r.setNombre(unico("Desc"));
		r.setTipo(tipo);
		r.setValor(new BigDecimal(valor));
		r.setPuntosMinimos(100);
		r.setCostoPuntos(100);
		r.setActiva(true);
		r.setVigenteDesde(desde);
		r.setVigenteHasta(hasta);
		return recompensas.saveAndFlush(r);
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion, Usuario quien) {
		return peticion.header("Authorization", "Bearer " + jwt.generar(quien));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private ResultActions enviar(MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion);
	}

	private static String cuerpoMesa(String nombre) {
		return "{\"nombre\":" + (nombre == null ? "null" : "\"" + nombre + "\"") + "}";
	}

	private String item(Producto p, int cantidad, String observaciones) {
		return "{\"productoId\":%d,\"cantidad\":%d,\"observaciones\":%s}".formatted(p.getId(), cantidad,
				observaciones == null ? "null" : "\"" + observaciones + "\"");
	}

	private String cuerpoComanda(Mesa m, String items, Long clienteId, Long recompensaId) {
		return "{\"mesaId\":%d,\"items\":[%s],\"clienteId\":%s,\"recompensaId\":%s}".formatted(m.getId(), items,
				clienteId, recompensaId);
	}

	private ResultActions crearComanda(Usuario quien, String cuerpo) throws Exception {
		return enviar(json(como(post("/api/comandas"), quien), cuerpo));
	}

	private long idDe(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private BigDecimal numero(ResultActions respuesta, String ruta) throws Exception {
		Object v = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), ruta);
		return new BigDecimal(v.toString());
	}

	private ResultActions pasarA(Long comandaId, String estado, Usuario quien) throws Exception {
		return enviar(json(como(patch("/api/comandas/" + comandaId + "/estado"), quien), "{\"estado\":\"" + estado + "\"}"));
	}

	private long comandaDe2Cafes() throws Exception {
		return idDe(crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 2, null), null, null)).andExpect(status().isCreated()));
	}

	private EstadoComanda estadoGuardado(long id) {
		return comandas.findById(id).orElseThrow().getEstado();
	}

	// ------------------------------------------------------------------ mesas (SCRUM-133)

	@Test
	void elAdministradorCreaUnaMesaQueQuedaDisponibleYElMeseroLaVe() throws Exception {
		String nombre = unico("Terraza");
		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa(nombre)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/mesas/")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.estado").value("DISPONIBLE"));

		enviar(como(get("/api/mesas"), mesero))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.nombre == '" + nombre + "')].estado").value("DISPONIBLE"));
	}

	@Test
	void elNombreSeRecortaYLaListaVieneOrdenadaPorNombreSinDistinguirMayusculas() throws Exception {
		String sufijo = UUID.randomUUID().toString().substring(0, 6);
		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa("  b2-" + sufijo + "  "))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("b2-" + sufijo));
		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa("A1-" + sufijo))).andExpect(status().isCreated());

		ResultActions lista = enviar(como(get("/api/mesas"), admin)).andExpect(status().isOk());
		java.util.List<String> nombres = JsonPath.read(lista.andReturn().getResponse().getContentAsString(), "$[*].nombre");
		assertThat(nombres).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
		assertThat(nombres.indexOf("A1-" + sufijo)).isLessThan(nombres.indexOf("b2-" + sufijo));
	}

	@Test
	void unNombreRepetidoSeRechazaSinDistinguirMayusculas() throws Exception {
		String nombre = unico("Barra");
		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa(nombre))).andExpect(status().isCreated());
		long antes = mesas.count();

		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa(nombre)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.nombre").exists());
		enviar(json(como(post("/api/mesas"), admin), cuerpoMesa(" " + nombre.toUpperCase() + " ")))
				.andExpect(status().isConflict());
		assertThat(mesas.count()).isEqualTo(antes);
	}

	@Test
	void unNombreVacioSoloConEspaciosFaltanteOMuyLargoSeRechaza() throws Exception {
		long antes = mesas.count();
		for (String cuerpo : new String[] { cuerpoMesa(""), cuerpoMesa("   "), cuerpoMesa(null), "{}",
				cuerpoMesa("x".repeat(31)) }) {
			enviar(json(como(post("/api/mesas"), admin), cuerpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.campos.nombre").exists());
		}
		assertThat(mesas.count()).isEqualTo(antes);
	}

	@Test
	void soloElAdministradorPuedeCrearMesas() throws Exception {
		long antes = mesas.count();
		for (Usuario quien : new Usuario[] { mesero, cajero, cocinero, clienteApp }) {
			enviar(json(como(post("/api/mesas"), quien), cuerpoMesa(unico("X")))).andExpect(status().isForbidden());
		}
		enviar(json(post("/api/mesas"), cuerpoMesa(unico("X")))).andExpect(status().isUnauthorized());
		assertThat(mesas.count()).isEqualTo(antes);
	}

	@Test
	void elMapaDeMesasEsDeAdministradorYMesero() throws Exception {
		enviar(como(get("/api/mesas"), admin)).andExpect(status().isOk());
		enviar(como(get("/api/mesas"), mesero)).andExpect(status().isOk());
		enviar(como(get("/api/mesas"), cajero)).andExpect(status().isForbidden());
		enviar(como(get("/api/mesas"), cocinero)).andExpect(status().isForbidden());
		enviar(get("/api/mesas")).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------ crear comanda y cálculos (SCRUM-95)

	@Test
	void laComandaNaceConSubtotalesYTotalYOcupaLaMesa() throws Exception {
		// 2 × 4.500,00 = 9.000,00;  3 × 2.500,25 = 7.500,75;  total = 16.500,75
		ResultActions r = crearComanda(mesero,
				cuerpoComanda(mesa, item(cafe, 2, "sin azúcar") + "," + item(pan, 3, null), null, null))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/comandas/")))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.mesaId").value(mesa.getId()))
				.andExpect(jsonPath("$.meseroId").value(mesero.getId()))
				.andExpect(jsonPath("$.lineas", hasSize(2)))
				.andExpect(jsonPath("$.lineas[0].productoId").value(cafe.getId()))
				.andExpect(jsonPath("$.lineas[0].cantidad").value(2))
				.andExpect(jsonPath("$.lineas[0].observaciones").value("sin azúcar"))
				.andExpect(jsonPath("$.lineas[0].agregadoDespuesDeEnvio").value(false));

		assertThat(numero(r, "$.lineas[0].precioUnitario")).isEqualByComparingTo("4500.00");
		assertThat(numero(r, "$.lineas[0].subtotal")).isEqualByComparingTo("9000.00");
		assertThat(numero(r, "$.lineas[1].subtotal")).isEqualByComparingTo("7500.75");
		assertThat(numero(r, "$.subtotal")).isEqualByComparingTo("16500.75");
		assertThat(numero(r, "$.descuento")).isEqualByComparingTo("0.00");
		assertThat(numero(r, "$.total")).isEqualByComparingTo("16500.75");
		assertThat(mesas.findById(mesa.getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.OCUPADA);
	}

	@Test
	void elAdministradorTambienPuedeCrearComandas() throws Exception {
		crearComanda(admin, cuerpoComanda(mesa, item(cafe, 1, null), null, null)).andExpect(status().isCreated());
	}

	@Test
	void sinProductosLaComandaSeRechazaYLaMesaSigueDisponible() throws Exception {
		long antes = comandas.count();
		for (String cuerpo : new String[] { cuerpoComanda(mesa, "", null, null), "{\"mesaId\":" + mesa.getId() + "}",
				"{\"mesaId\":" + mesa.getId() + ",\"items\":null}" }) {
			crearComanda(mesero, cuerpo)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.campos.items").exists());
		}
		assertThat(comandas.count()).isEqualTo(antes);
		assertThat(mesas.findById(mesa.getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
	}

	@Test
	void cantidadCeroObservacionesLargasYDatosInexistentesSonErrores400() throws Exception {
		crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 0, null), null, null)).andExpect(status().isBadRequest());
		crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 1, "x".repeat(101)), null, null)).andExpect(status().isBadRequest());
		crearComanda(mesero, "{\"mesaId\":999999,\"items\":[" + item(cafe, 1, null) + "]}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.mesaId").exists());
		crearComanda(mesero, "{\"mesaId\":%d,\"items\":[{\"productoId\":999999,\"cantidad\":1}]}".formatted(mesa.getId()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.items").exists());
		crearComanda(mesero, "no es json").andExpect(status().isBadRequest());
		assertThat(comandas.count()).isZero();
	}

	@Test
	void unaMesaSoloTieneUnaComandaAbiertaYSeLiberaAlCerrar() throws Exception {
		long id = comandaDe2Cafes();
		crearComanda(mesero, cuerpoComanda(mesa, item(pan, 1, null), null, null)).andExpect(status().isConflict());

		pasarA(id, "ENVIADA", mesero).andExpect(status().isOk());
		pasarA(id, "EN_PREPARACION", cocinero).andExpect(status().isOk());
		pasarA(id, "LISTA", cocinero).andExpect(status().isOk());
		pasarA(id, "CERRADA", cajero).andExpect(status().isOk());
		crearComanda(mesero, cuerpoComanda(mesa, item(pan, 1, null), null, null)).andExpect(status().isCreated());
	}

	@Test
	void noSePuedePedirMasDelStockNiUnProductoDesactivado() throws Exception {
		crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 11, null), null, null))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.campos.items").exists());
		crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 6, null) + "," + item(cafe, 5, null), null, null))
				.andExpect(status().isConflict());
		cafe.setActivo(false);
		productos.saveAndFlush(cafe);
		crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 1, null), null, null)).andExpect(status().isConflict());
		assertThat(comandas.count()).isZero();
	}

	@Test
	void soloAdministradorYMeseroCreanComandas() throws Exception {
		String cuerpo = cuerpoComanda(mesa, item(cafe, 1, null), null, null);
		crearComanda(cajero, cuerpo).andExpect(status().isForbidden());
		crearComanda(cocinero, cuerpo).andExpect(status().isForbidden());
		crearComanda(clienteApp, cuerpo).andExpect(status().isForbidden());
		enviar(json(post("/api/comandas"), cuerpo)).andExpect(status().isUnauthorized());
		assertThat(comandas.count()).isZero();
	}

	@Test
	void unDescuentoPorPorcentajeAutorizadoSeRefleja() throws Exception {
		Cliente cliente = cliente(250);
		Recompensa diez = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", LocalDate.now().minusDays(2),
				LocalDate.now().plusDays(2));
		// subtotal 9.000,00 · descuento 10 % = 900,00 · total 8.100,00
		ResultActions r = crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 2, null), cliente.getId(), diez.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.clienteId").value(cliente.getId()));
		assertThat(numero(r, "$.subtotal")).isEqualByComparingTo("9000.00");
		assertThat(numero(r, "$.descuento")).isEqualByComparingTo("900.00");
		assertThat(numero(r, "$.total")).isEqualByComparingTo("8100.00");
	}

	@Test
	void alAgregarProductosElDescuentoSeRecalculaSobreElNuevoSubtotal() throws Exception {
		Cliente cliente = cliente(250);
		Recompensa diez = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", null, null);
		long id = idDe(crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 2, null), cliente.getId(), diez.getId()))
				.andExpect(status().isCreated()));

		comandaService.agregarProducto(id, new ComandaService.ItemSolicitado(cafe.getId(), 2, null), mesero.getId());

		// subtotal 4 × 4.500 = 18.000 · descuento 1.800 · total 16.200
		Comanda c = comandas.findById(id).orElseThrow();
		assertThat(c.getSubtotal()).isEqualByComparingTo("18000.00");
		assertThat(c.getDescuento()).isEqualByComparingTo("1800.00");
		assertThat(c.getTotal()).isEqualByComparingTo("16200.00");
	}

	@Test
	void unDescuentoDeValorFijoNuncaDejaElTotalNegativo() throws Exception {
		Cliente cliente = cliente(250);
		Recompensa grande = recompensa(TipoRecompensa.DESCUENTO_VALOR, "50000", null, null);
		ResultActions r = crearComanda(mesero, cuerpoComanda(mesa, item(cafe, 1, null), cliente.getId(), grande.getId()))
				.andExpect(status().isCreated());
		assertThat(numero(r, "$.descuento")).isEqualByComparingTo("4500.00");
		assertThat(numero(r, "$.total")).isEqualByComparingTo("0.00");
	}

	@Test
	void unDescuentoNoAutorizadoOVencidoSeRechazaSinCrearLaComanda() throws Exception {
		Cliente cliente = cliente(250);
		Cliente pobre = cliente(10);
		Recompensa vencida = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", LocalDate.now().minusDays(10),
				LocalDate.now().minusDays(1));
		Recompensa futura = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", LocalDate.now().plusDays(1), null);
		Recompensa inactiva = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", null, null);
		inactiva.setActiva(false);
		recompensas.saveAndFlush(inactiva);
		Recompensa vigente = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10", null, null);
		Recompensa gratis = recompensa(TipoRecompensa.PRODUCTO_GRATIS, "0", null, null);

		String items = item(cafe, 2, null);
		Object[][] casos = {
				{ cliente.getId(), vencida.getId() },
				{ cliente.getId(), futura.getId() },
				{ cliente.getId(), inactiva.getId() },
				{ cliente.getId(), gratis.getId() },
				{ cliente.getId(), 999999L },
				{ pobre.getId(), vigente.getId() },
				{ null, vigente.getId() } };
		for (Object[] caso : casos) {
			crearComanda(mesero, cuerpoComanda(mesa, items, (Long) caso[0], (Long) caso[1]))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.campos.recompensaId").exists());
		}
		assertThat(comandas.count()).isZero();
		assertThat(mesas.findById(mesa.getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
	}

	// ------------------------------------------------------------------ ciclo de vida completo (SCRUM-94 y 96)

	@Test
	void elCicloCompletoPasaPorLosCincoEstadosCadaUnoConSuRol() throws Exception {
		long id = comandaDe2Cafes();
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.PENDIENTE);

		pasarA(id, "ENVIADA", mesero).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADA"))
				.andExpect(jsonPath("$.fechaEnvio").exists());
		assertThat(productos.findById(cafe.getId()).orElseThrow().getStockActual()).isEqualTo(8);
		pasarA(id, "EN_PREPARACION", cocinero).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
		pasarA(id, "LISTA", cocinero).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("LISTA"));
		assertThat(mesas.findById(mesa.getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.OCUPADA);
		pasarA(id, "CERRADA", cajero).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));

		assertThat(mesas.findById(mesa.getId()).orElseThrow().getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
		enviar(como(get("/api/comandas/" + id), cajero)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADA"));
	}

	@Test
	void elMeseroTambienCierraYElAdministradorHaceCualquierTransicion() throws Exception {
		long conMesero = comandaDe2Cafes();
		pasarA(conMesero, "ENVIADA", mesero).andExpect(status().isOk());
		pasarA(conMesero, "EN_PREPARACION", admin).andExpect(status().isOk());
		pasarA(conMesero, "LISTA", admin).andExpect(status().isOk());
		pasarA(conMesero, "CERRADA", mesero).andExpect(status().isOk());

		long conAdmin = comandaDe2Cafes();
		pasarA(conAdmin, "ENVIADA", admin).andExpect(status().isOk());
		pasarA(conAdmin, "EN_PREPARACION", admin).andExpect(status().isOk());
		pasarA(conAdmin, "LISTA", admin).andExpect(status().isOk());
		pasarA(conAdmin, "CERRADA", admin).andExpect(status().isOk());
	}

	@Test
	void unRolQueNoCorrespondeRecibe400YLaComandaNoCambia() throws Exception {
		long id = comandaDe2Cafes();
		pasarA(id, "ENVIADA", cocinero).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").exists());
		pasarA(id, "ENVIADA", cajero).andExpect(status().isBadRequest());
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.PENDIENTE);

		pasarA(id, "ENVIADA", mesero).andExpect(status().isOk());
		pasarA(id, "EN_PREPARACION", mesero).andExpect(status().isBadRequest());
		pasarA(id, "EN_PREPARACION", cajero).andExpect(status().isBadRequest());
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.ENVIADA);

		pasarA(id, "EN_PREPARACION", cocinero).andExpect(status().isOk());
		pasarA(id, "LISTA", cajero).andExpect(status().isBadRequest());
		pasarA(id, "LISTA", cocinero).andExpect(status().isOk());
		pasarA(id, "CERRADA", cocinero).andExpect(status().isBadRequest());
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.LISTA);
	}

	@Test
	void saltarseUnEstadoRetrocederORepetirEsUn400() throws Exception {
		long id = comandaDe2Cafes();
		pasarA(id, "LISTA", cocinero).andExpect(status().isBadRequest());
		pasarA(id, "CERRADA", admin).andExpect(status().isBadRequest());
		pasarA(id, "PENDIENTE", admin).andExpect(status().isBadRequest());
		pasarA(id, "ENVIADA", mesero).andExpect(status().isOk());
		pasarA(id, "ENVIADA", mesero).andExpect(status().isBadRequest());
		pasarA(id, "PENDIENTE", admin).andExpect(status().isBadRequest());
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.ENVIADA);
		// el stock se descontó una sola vez
		assertThat(productos.findById(cafe.getId()).orElseThrow().getStockActual()).isEqualTo(8);
	}

	@Test
	void unaComandaCerradaNoVuelveAMoverse() throws Exception {
		long id = comandaDe2Cafes();
		pasarA(id, "ENVIADA", mesero).andExpect(status().isOk());
		pasarA(id, "EN_PREPARACION", cocinero).andExpect(status().isOk());
		pasarA(id, "LISTA", cocinero).andExpect(status().isOk());
		pasarA(id, "CERRADA", cajero).andExpect(status().isOk());
		for (String estado : new String[] { "PENDIENTE", "ENVIADA", "LISTA", "CERRADA" }) {
			pasarA(id, estado, admin).andExpect(status().isBadRequest());
		}
	}

	@Test
	void unaComandaVaciaNoSePuedeEnviar() throws Exception {
		Comanda vacia = new Comanda();
		vacia.setMesa(mesa);
		vacia.setMesero(mesero);
		comandas.saveAndFlush(vacia);

		pasarA(vacia.getId(), "ENVIADA", mesero)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(containsString("vacía")));
		assertThat(estadoGuardado(vacia.getId())).isEqualTo(EstadoComanda.PENDIENTE);
	}

	@Test
	void estadosInvalidosFaltantesOComandasInexistentes() throws Exception {
		long id = comandaDe2Cafes();
		pasarA(id, "VOLANDO", admin).andExpect(status().isBadRequest());
		enviar(json(como(patch("/api/comandas/" + id + "/estado"), admin), "{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.estado").exists());
		pasarA(999999L, "ENVIADA", admin).andExpect(status().isNotFound());
		enviar(como(get("/api/comandas/999999"), admin)).andExpect(status().isNotFound());
	}

	@Test
	void unClienteNiSinSesionPuedenCambiarElEstado() throws Exception {
		long id = comandaDe2Cafes();
		pasarA(id, "ENVIADA", clienteApp).andExpect(status().isForbidden());
		enviar(json(patch("/api/comandas/" + id + "/estado"), "{\"estado\":\"ENVIADA\"}")).andExpect(status().isUnauthorized());
		assertThat(estadoGuardado(id)).isEqualTo(EstadoComanda.PENDIENTE);
	}

	// ------------------------------------------------------------------ contrato

	@Test
	void laRespuestaTieneExactamenteLosCamposDelContrato() throws Exception {
		long id = comandaDe2Cafes();
		ResultActions r = enviar(como(get("/api/comandas/" + id), mesero)).andExpect(status().isOk());
		java.util.Map<String, Object> cuerpo = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$");
		assertThat(cuerpo.keySet()).containsExactlyInAnyOrder("id", "mesaId", "mesaNombre", "meseroId", "clienteId",
				"estado", "lineas", "subtotal", "descuento", "total", "fechaCreacion", "fechaEnvio");
		java.util.Map<String, Object> linea = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.lineas[0]");
		assertThat(linea.keySet()).containsExactlyInAnyOrder("id", "productoId", "productoNombre", "cantidad",
				"precioUnitario", "subtotal", "observaciones", "agregadoDespuesDeEnvio");

		ResultActions mesaJson = enviar(como(get("/api/mesas"), admin)).andExpect(status().isOk());
		java.util.Map<String, Object> primera = JsonPath.read(mesaJson.andReturn().getResponse().getContentAsString(), "$[0]");
		assertThat(primera.keySet()).containsExactlyInAnyOrder("id", "nombre", "estado");
	}

	@Test
	void losErroresUsanElFormatoDeLaApi() throws Exception {
		crearComanda(mesero, cuerpoComanda(mesa, "", null, null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Revisa los datos ingresados."))
				.andExpect(jsonPath("$.campos").exists());
	}

}
