package com.appresso.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.ComandaRepository;
import com.appresso.backend.comandas.ComandaService;
import com.appresso.backend.comandas.ComandaService.ItemSolicitado;
import com.appresso.backend.comandas.EstadoComanda;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.comandas.MesaRepository;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

@SpringBootTest
class InventarioTest {

	@Autowired WebApplicationContext contexto;
	@Autowired JwtService jwt;
	@Autowired JdbcTemplate jdbc;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired UsuarioRepository usuarios;
	@Autowired MesaRepository mesas;
	@Autowired ComandaRepository comandas;
	@Autowired ComandaService servicio;
	@Autowired ZoneId zona;

	private final List<Long> comandasCreadas = new ArrayList<>();
	private final List<Long> mesasCreadas = new ArrayList<>();
	private Categoria categoria;
	private Usuario administrador;
	private Usuario mesero;
	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
		Categoria nueva = new Categoria();
		nueva.setNombre("Inventario " + UUID.randomUUID());
		categoria = categorias.save(nueva);
		administrador = usuario(Rol.ADMINISTRADOR);
		mesero = usuario(Rol.MESERO);
	}

	@AfterEach
	void limpiar() {
		if (categoria != null) {
			jdbc.update("delete from movimientos_inventario where producto_id in (select id from productos where categoria_id=?)",
					categoria.getId());
		}
		for (Long id : comandasCreadas) {
			jdbc.update("delete from historial_comanda where comanda_id=?", id);
			jdbc.update("delete from detalles_comanda where comanda_id=?", id);
			comandas.deleteById(id);
		}
		mesas.deleteAllById(mesasCreadas);
		if (categoria != null) {
			jdbc.update("delete from productos where categoria_id=?", categoria.getId());
			categorias.deleteById(categoria.getId());
		}
		if (mesero != null) usuarios.deleteById(mesero.getId());
		if (administrador != null) usuarios.deleteById(administrador.getId());
	}

	@Test
	void dosComandasConcurrentesNoVendenLasMismasUnidades() throws Exception {
		Producto cafe = producto("Café", 5, 2, true);
		Comanda a = comanda(new ItemSolicitado(cafe.getId(), 4, null));
		Comanda b = comanda(new ItemSolicitado(cafe.getId(), 4, null));

		assertThat(enParalelo(() -> intentarEnvio(a), () -> intentarEnvio(b))).containsExactlyInAnyOrder(true, false);
		assertThat(stock(cafe)).isEqualTo(1);
		assertThat(List.of(estado(a), estado(b))).containsExactlyInAnyOrder(EstadoComanda.ENVIADA, EstadoComanda.PENDIENTE);
		assertThat(movimientos(cafe)).isEqualTo(1);
	}

	@Test
	void confirmarDosVecesLaMismaComandaDescuentaUnaSolaVez() throws Exception {
		Producto cafe = producto("Café", 10, 2, true);
		Comanda pedido = comanda(new ItemSolicitado(cafe.getId(), 4, null));

		Callable<Boolean> confirmar = () -> {
			try {
				enviar(pedido);
				return true;
			} catch (IllegalStateException rechazo) {
				assertThat(rechazo).hasMessageContaining("Transición no permitida");
				return false;
			}
		};
		assertThat(enParalelo(confirmar, confirmar)).containsExactlyInAnyOrder(true, false);
		assertThat(stock(cafe)).isEqualTo(6);
		assertThat(movimientos(cafe)).isEqualTo(1);
	}

	@Test
	void faltaDeUnProductoRevierteTodosLosDescuentos() {
		Producto cafe = producto("Café", 10, 2, true);
		Producto leche = producto("Leche", 5, 2, true);
		Comanda pedido = comanda(new ItemSolicitado(cafe.getId(), 4, null), new ItemSolicitado(leche.getId(), 4, null));
		enviar(comanda(new ItemSolicitado(leche.getId(), 3, null)));

		assertThatThrownBy(() -> enviar(pedido)).isInstanceOf(StockInsuficienteException.class)
				.hasMessageContaining("Leche");
		assertThat(stock(cafe)).isEqualTo(10);
		assertThat(stock(leche)).isEqualTo(2);
		assertThat(estado(pedido)).isEqualTo(EstadoComanda.PENDIENTE);
		assertThat(comandas.findById(pedido.getId()).orElseThrow().getFechaEnvio()).isNull();
		assertThat(movimientos(cafe)).isZero();
	}

	@Test
	void ajusteActualizaAlertasYConservaLaTrazabilidad() throws Exception {
		Producto cafe = producto("Café inventario " + UUID.randomUUID(), 2, 2, true);
		Producto inactivo = producto("Inactivo", 0, 2, false);
		String token = "Bearer " + jwt.generar(administrador);
		String url = "/api/inventario/productos/" + cafe.getId() + "/ajustes";
		mvc.perform(get("/api/inventario/alertas").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + cafe.getId() + ")]").isNotEmpty())
				.andExpect(jsonPath("$[?(@.id == " + inactivo.getId() + ")]").isEmpty());
		mvc.perform(post(url).header("Authorization", "Bearer " + jwt.generar(mesero))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
		mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"SALIDA_MANUAL\",\"cantidad\":1.5,\"concepto\":\"Merma\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"SALIDA_MANUAL\",\"cantidad\":3,\"concepto\":\"Merma\"}"))
				.andExpect(status().isConflict());
		mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"ENTRADA_MANUAL\",\"cantidad\":5,\"concepto\":\"Reposición\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.stockActual").value(7));
		mvc.perform(get("/api/inventario/alertas").header("Authorization", token))
				.andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + cafe.getId() + ")]").isEmpty());

		String hoy = LocalDate.now(zona).toString();
		mvc.perform(get("/api/inventario/movimientos").header("Authorization", token)
				.param("producto", cafe.getNombre()).param("desde", hoy).param("hasta", hoy))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].concepto").value("Reposición"))
				.andExpect(jsonPath("$[0].stockAnterior").value(2))
				.andExpect(jsonPath("$[0].stockResultante").value(7))
				.andExpect(jsonPath("$[0].usuarioId").value(administrador.getId()))
				.andExpect(jsonPath("$[0].fecha").isNotEmpty());
	}

	private Usuario usuario(Rol rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre(rol.name());
		usuario.setCorreo(UUID.randomUUID() + "@appresso.test");
		usuario.setContrasenaHash("hash");
		usuario.setRol(rol);
		usuario.setActivo(true);
		return usuarios.save(usuario);
	}

	private Producto producto(String nombre, int stock, int umbral, boolean activo) {
		Producto producto = new Producto();
		producto.setNombre(nombre);
		producto.setCategoria(categoria);
		producto.setPrecio(new BigDecimal("1000.00"));
		producto.setStockActual(stock);
		producto.setUmbralMinimo(umbral);
		producto.setActivo(activo);
		return productos.save(producto);
	}

	private Comanda comanda(ItemSolicitado... items) {
		Mesa mesa = new Mesa();
		mesa.setNombre("Inv-" + UUID.randomUUID().toString().substring(0, 8));
		mesasCreadas.add(mesas.save(mesa).getId());
		Comanda comanda = servicio.crear(mesa.getId(), mesero.getId(), List.of(items));
		comandasCreadas.add(comanda.getId());
		return comanda;
	}

	private void enviar(Comanda comanda) {
		servicio.cambiarEstado(comanda.getId(), EstadoComanda.ENVIADA, mesero.getId());
	}

	private boolean intentarEnvio(Comanda comanda) {
		try {
			enviar(comanda);
			return true;
		} catch (StockInsuficienteException rechazo) {
			return false;
		}
	}

	private int stock(Producto producto) {
		return productos.findById(producto.getId()).orElseThrow().getStockActual();
	}

	private EstadoComanda estado(Comanda comanda) {
		return comandas.findById(comanda.getId()).orElseThrow().getEstado();
	}

	private int movimientos(Producto producto) {
		return jdbc.queryForObject("select count(*) from movimientos_inventario where producto_id = ?",
				Integer.class, producto.getId());
	}

	private List<Boolean> enParalelo(Callable<Boolean> primero, Callable<Boolean> segundo) throws Exception {
		CountDownLatch preparados = new CountDownLatch(2);
		CountDownLatch salir = new CountDownLatch(1);
		try (var ejecutor = Executors.newFixedThreadPool(2)) {
			var a = ejecutor.submit(() -> competir(preparados, salir, primero));
			var b = ejecutor.submit(() -> competir(preparados, salir, segundo));
			boolean listos = preparados.await(5, TimeUnit.SECONDS);
			salir.countDown();
			assertThat(listos).isTrue();
			return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
		}
	}

	private boolean competir(CountDownLatch preparados, CountDownLatch salir, Callable<Boolean> confirmar) throws Exception {
		preparados.countDown();
		if (!salir.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("La confirmación no arrancó");
		return confirmar.call();
	}
}
