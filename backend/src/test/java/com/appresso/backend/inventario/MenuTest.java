package com.appresso.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas del menú de solo lectura (SCRUM-87 y SCRUM-89, RF-11, RF-13, RNF-17): qué productos salen,
 * cómo se agrupan, que precio y existencias estén vigentes, quién puede consultarlo y que responda en
 * 2 segundos o menos. Usan la cadena de seguridad real (2.3.1) y se deshacen al terminar.
 */
@SpringBootTest
@Transactional
class MenuTest {

	private static final String CLAVE = "Clave-Segura-1";
	/** Plazo del criterio de aceptación de 3.3.1 y del SCRUM-89. */
	private static final long PLAZO_MS = 2_000;

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired ProductoService productoService;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario mesero;
	Usuario admin; // solo el administrador ajusta existencias (3.2.1)

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		mesero = cuenta(Rol.MESERO);
		admin = cuenta(Rol.ADMINISTRADOR);
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

	private String nombreUnico(String base) {
		return base + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private Categoria categoria(String nombre, boolean activa) {
		Categoria c = new Categoria();
		c.setNombre(nombre);
		c.setActiva(activa);
		return categorias.save(c);
	}

	private Producto producto(Categoria categoria, String nombre, int stock, int umbral, boolean activo) {
		Producto p = new Producto();
		p.setCategoria(categoria);
		p.setNombre(nombre);
		p.setDescripcion("Rico");
		p.setPrecio(new BigDecimal("6500.00"));
		p.setStockActual(stock);
		p.setUmbralMinimo(umbral);
		p.setActivo(activo);
		return productos.save(p);
	}

	private String consultar(Usuario quien) throws Exception {
		return mvc.perform(get("/api/menu").header("Authorization", "Bearer " + jwt.generar(quien)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	/** Los productos del menú que pertenecen a una categoría (por id), o lista vacía si no sale. */
	private List<Map<String, Object>> productosDe(String json, Categoria categoria) {
		List<Map<String, Object>> lista = JsonPath.read(json,
				"$[?(@.id == " + categoria.getId() + ")].productos[*]");
		return lista;
	}

	private List<String> nombresDe(String json, Categoria categoria) {
		return productosDe(json, categoria).stream().map(p -> (String) p.get("nombre")).toList();
	}

	// ------------------------------------------------------------------ qué sale (RF-11, RF-13)

	@Test
	void muestraLosProductosActivosConStockAgrupadosPorCategoria() throws Exception {
		Categoria bebidas = categoria(nombreUnico("Bebidas"), true);
		Categoria postres = categoria(nombreUnico("Postres"), true);
		producto(bebidas, "Latte", 12, 3, true);
		producto(postres, "Brownie", 5, 2, true);

		String menu = consultar(mesero);

		assertThat(nombresDe(menu, bebidas)).containsExactly("Latte");
		assertThat(nombresDe(menu, postres)).containsExactly("Brownie");
	}

	@Test
	void cadaProductoTraePrecioExistenciasYDisponibilidad() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 12, 3, true);

		Map<String, Object> latte = productosDe(consultar(mesero), c).get(0);

		assertThat(latte).containsKeys("id", "nombre", "descripcion", "precio", "imagenUrl", "existencias",
				"disponibilidad");
		assertThat(((Number) latte.get("precio")).doubleValue()).isEqualTo(6500.0);
		assertThat(latte.get("existencias")).isEqualTo(12);
		assertThat(latte.get("descripcion")).isEqualTo("Rico");
	}

	@Test
	void unProductoSinStockNoAparece() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 12, 3, true);
		producto(c, "Agotado", 0, 3, true);

		assertThat(nombresDe(consultar(mesero), c)).containsExactly("Latte");
	}

	@Test
	void unProductoDesactivadoNoAparece() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 12, 3, true);
		producto(c, "Descontinuado", 20, 3, false);

		assertThat(nombresDe(consultar(mesero), c)).containsExactly("Latte");
	}

	@Test
	void losProductosDeUnaCategoriaDesactivadaNoAparecenNiLaCategoria() throws Exception {
		Categoria activa = categoria(nombreUnico("Bebidas"), true);
		Categoria apagada = categoria(nombreUnico("Temporada"), false);
		producto(activa, "Latte", 12, 3, true);
		producto(apagada, "Frappé", 12, 3, true);

		String menu = consultar(mesero);

		assertThat(nombresDe(menu, apagada)).isEmpty();
		List<Object> ids = JsonPath.read(menu, "$[*].id");
		assertThat(ids).doesNotContain(apagada.getId().intValue());
	}

	@Test
	void unaCategoriaSinProductosDisponiblesNoSale() throws Exception {
		Categoria vacia = categoria(nombreUnico("Vacía"), true);
		Categoria agotada = categoria(nombreUnico("Agotada"), true);
		producto(agotada, "Sin stock", 0, 0, true);

		List<Object> ids = JsonPath.read(consultar(mesero), "$[*].id");

		assertThat(ids).doesNotContain(vacia.getId().intValue(), agotada.getId().intValue());
	}

	@Test
	void categoriasYProductosVanOrdenadosPorNombreSinDistinguirMayusculas() throws Exception {
		String marca = UUID.randomUUID().toString().substring(0, 6);
		Categoria b = categoria("b-" + marca, true);
		Categoria a = categoria("A-" + marca, true);
		producto(a, "zeta", 5, 1, true);
		producto(a, "Alfa", 5, 1, true);
		producto(a, "beta", 5, 1, true);
		producto(b, "x", 5, 1, true);

		String menu = consultar(mesero);

		assertThat(nombresDe(menu, a)).containsExactly("Alfa", "beta", "zeta");
		List<String> categoriasEnOrden = JsonPath.read(menu, "$[?(@.nombre =~ /.*-" + marca + "/)].nombre");
		assertThat(categoriasEnOrden).containsExactly("A-" + marca, "b-" + marca);
	}

	// ------------------------------------------------------------------ disponibilidad (RNF-17)

	@Test
	void conExistenciasPorEncimaDelUmbralEstaDisponible() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 4, 3, true);

		assertThat(productosDe(consultar(mesero), c).get(0).get("disponibilidad")).isEqualTo("DISPONIBLE");
	}

	@Test
	void conExistenciasIgualesOMenoresAlUmbralQuedanPocasUnidades() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Justo", 3, 3, true);
		producto(c, "Menos", 1, 3, true);

		Map<String, Object> justo = productosDe(consultar(mesero), c).stream()
				.filter(p -> p.get("nombre").equals("Justo")).findFirst().orElseThrow();
		Map<String, Object> menos = productosDe(consultar(mesero), c).stream()
				.filter(p -> p.get("nombre").equals("Menos")).findFirst().orElseThrow();

		assertThat(justo.get("disponibilidad")).isEqualTo("POCAS_UNIDADES");
		assertThat(menos.get("disponibilidad")).isEqualTo("POCAS_UNIDADES");
		assertThat(menos.get("existencias")).isEqualTo(1);
	}

	@Test
	void elUmbralMinimoNoSeEnviaAQuienConsulta() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 12, 3, true);

		assertThat(productosDe(consultar(mesero), c).get(0)).doesNotContainKeys("umbralMinimo", "stockActual", "activo");
	}

	// ------------------------------------------------------------------ datos vigentes (sin caché)

	@Test
	void unIngresoManualHaceReaparecerUnProductoAgotado() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		Producto agotado = producto(c, "Latte", 0, 3, true);
		assertThat(nombresDe(consultar(mesero), c)).isEmpty();

		productoService.ajustarExistencias(agotado.getId(), TipoMovimientoInventario.ENTRADA_MANUAL, 8,
				"Reposición", admin.getId());

		Map<String, Object> latte = productosDe(consultar(mesero), c).get(0);
		assertThat(latte.get("existencias")).isEqualTo(8);
	}

	@Test
	void unaVentaQueAgotaElProductoLoQuitaDelMenu() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		Producto p = producto(c, "Latte", 2, 1, true);
		assertThat(nombresDe(consultar(mesero), c)).containsExactly("Latte");

		productoService.ajustarExistencias(p.getId(), TipoMovimientoInventario.SALIDA_MANUAL, 2, "Merma", admin.getId());

		assertThat(nombresDe(consultar(mesero), c)).isEmpty();
	}

	@Test
	void elCambioDePrecioSeVeEnLaSiguienteConsulta() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		Producto p = producto(c, "Latte", 12, 3, true);
		assertThat(((Number) productosDe(consultar(mesero), c).get(0).get("precio")).doubleValue()).isEqualTo(6500.0);

		p.setPrecio(new BigDecimal("7200.00"));
		productos.save(p);

		assertThat(((Number) productosDe(consultar(mesero), c).get(0).get("precio")).doubleValue()).isEqualTo(7200.0);
	}

	@Test
	void desactivarYReactivarUnaCategoriaSacaYDevuelveSusProductos() throws Exception {
		Categoria c = categoria(nombreUnico("Bebidas"), true);
		producto(c, "Latte", 12, 3, true);

		c.setActiva(false);
		categorias.save(c);
		assertThat(nombresDe(consultar(mesero), c)).isEmpty();

		c.setActiva(true);
		categorias.save(c);
		assertThat(nombresDe(consultar(mesero), c)).containsExactly("Latte");
	}

	// ------------------------------------------------------------------ quién puede consultarlo

	@Test
	void administradorMeseroYClienteLoConsultan() throws Exception {
		for (Rol rol : List.of(Rol.ADMINISTRADOR, Rol.MESERO, Rol.CLIENTE)) {
			mvc.perform(get("/api/menu").header("Authorization", "Bearer " + jwt.generar(cuenta(rol))))
					.andExpect(status().isOk());
		}
	}

	@Test
	void cajeroYCocineroRecibenError403() throws Exception {
		for (Rol rol : List.of(Rol.CAJERO, Rol.COCINERO)) {
			mvc.perform(get("/api/menu").header("Authorization", "Bearer " + jwt.generar(cuenta(rol))))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void sinSesionResponde401() throws Exception {
		mvc.perform(get("/api/menu")).andExpect(status().isUnauthorized());
	}

	@Test
	void elMenuEsDeSoloLectura() throws Exception {
		String token = "Bearer " + jwt.generar(cuenta(Rol.ADMINISTRADOR));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/menu")
				.header("Authorization", token)).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/menu/1")
				.header("Authorization", token)).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------ rendimiento (SCRUM-89)

	/**
	 * Criterio de 3.3.1: el menú responde en 2 segundos o menos. Se mide con un catálogo mucho más
	 * grande que el de una cafetería (10 categorías × 100 productos) y la respuesta completa: seguridad,
	 * consulta y conversión a JSON. La primera llamada calienta la conexión y las clases; se mide la
	 * siguiente.
	 */
	@Test
	void conUnCatalogoGrandeElMenuRespondeEnDosSegundosOMenos() throws Exception {
		List<Categoria> grandes = new ArrayList<>();
		List<Producto> lote = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			Categoria c = categoria(nombreUnico("Carga " + i), true);
			grandes.add(c);
			for (int j = 0; j < 100; j++) {
				Producto p = new Producto();
				p.setCategoria(c);
				p.setNombre("Producto " + i + "-" + j);
				p.setPrecio(new BigDecimal("4500.00"));
				p.setStockActual(j % 7 == 0 ? 0 : 10 + j); // algunos agotados: no deben salir
				p.setUmbralMinimo(5);
				lote.add(p);
			}
		}
		productos.saveAll(lote);
		productos.flush();
		consultar(mesero); // calentamiento

		long inicio = System.nanoTime();
		String menu = consultar(mesero);
		long ms = (System.nanoTime() - inicio) / 1_000_000;

		System.out.println("Menú con " + lote.size() + " productos: " + ms + " ms");
		assertThat(ms).as("tiempo de respuesta del menú (ms)").isLessThanOrEqualTo(PLAZO_MS);
		// Y la respuesta es la correcta: 100 por categoría menos los agotados (j múltiplo de 7: 15 de 100).
		for (Categoria c : grandes) {
			assertThat(productosDe(menu, c)).hasSize(85);
		}
	}

}
