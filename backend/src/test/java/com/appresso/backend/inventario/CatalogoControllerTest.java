package com.appresso.backend.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.tiemporeal.CategoriaEventoDatos;
import com.appresso.backend.tiemporeal.ProductoEventoDatos;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas de punta a punta de productos y categorías (3.1.1): creación, edición, desactivación,
 * integridad referencial, 401/403, tiempo real y auditoría. Usan la cadena de seguridad real (2.3.1).
 *
 * <p>Cada prueba corre en una transacción que se deshace al terminar. El publicador de tiempo real
 * es un doble: aquí se verifica <i>qué</i> se publica; que llegue por WebSocket lo prueba
 * {@code TiempoRealTest}.</p>
 */
@SpringBootTest
@Transactional
class CatalogoControllerTest {

	private static final String CLAVE = "Clave-Segura-1";

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired MovimientoInventarioRepository movimientos;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario admin;
	Usuario mesero;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = cuenta(Rol.ADMINISTRADOR);
		mesero = cuenta(Rol.MESERO);
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

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion, Usuario quien) {
		return peticion.header("Authorization", "Bearer " + jwt.generar(quien));
	}

	private ResultActions enviar(MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private String nombreUnico(String base) {
		return base + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private Categoria categoria(boolean activa) {
		Categoria c = new Categoria();
		c.setNombre(nombreUnico("Cat"));
		c.setActiva(activa);
		return categorias.save(c);
	}

	private Producto producto(Categoria categoria, boolean activo, int stock) {
		Producto p = new Producto();
		p.setCategoria(categoria);
		p.setNombre(nombreUnico("Prod"));
		p.setPrecio(new BigDecimal("5000.00"));
		p.setStockActual(stock);
		p.setActivo(activo);
		return productos.save(p);
	}

	private static String cuerpoCategoria(String nombre) {
		return "{\"nombre\":" + texto(nombre) + "}";
	}

	private static String cuerpoProducto(String nombre, String precio, Long categoriaId, String imagen,
			Integer existencias, Integer umbral) {
		return """
				{"nombre":%s,"descripcion":"Rico","precio":%s,"categoriaId":%s,"imagenUrl":%s,
				"existenciasIniciales":%s,"umbralMinimo":%s}
				""".formatted(texto(nombre), precio, categoriaId, texto(imagen), existencias, umbral);
	}

	private static String texto(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}

	private long idDe(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private List<RegistroAuditoria> auditoriaDe(String operacion, Long entidadId) {
		return auditoria.findAll().stream()
				.filter(r -> r.getTipoOperacion().equals(operacion) && entidadId.equals(r.getEntidadId()))
				.toList();
	}

	// ------------------------------------------------------------------ acceso (401 y 403)

	/** Todos los endpoints de escritura, con un id cualquiera y cuerpo válido. */
	private List<MockHttpServletRequestBuilder> escrituras(Long categoriaId, Long productoId) {
		return List.of(
				json(post("/api/categorias"), cuerpoCategoria(nombreUnico("Nueva"))),
				json(put("/api/categorias/" + categoriaId), cuerpoCategoria(nombreUnico("Otra"))),
				post("/api/categorias/" + categoriaId + "/desactivar"),
				post("/api/categorias/" + categoriaId + "/activar"),
				json(post("/api/productos"), cuerpoProducto(nombreUnico("P"), "1000", categoriaId, null, 1, 1)),
				json(put("/api/productos/" + productoId), cuerpoProducto(nombreUnico("P"), "1000", categoriaId, null, 1, 1)),
				post("/api/productos/" + productoId + "/desactivar"),
				post("/api/productos/" + productoId + "/activar"));
	}

	@Test
	void sinSesionTodoResponde401() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 1);
		for (MockHttpServletRequestBuilder peticion : escrituras(c.getId(), p.getId())) {
			enviar(peticion).andExpect(status().isUnauthorized());
		}
		enviar(get("/api/categorias")).andExpect(status().isUnauthorized());
		enviar(get("/api/productos")).andExpect(status().isUnauthorized());
	}

	@Test
	void losRolesSinPermisoRecibenError403EnLasEscrituras() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 1);
		for (Rol rol : List.of(Rol.MESERO, Rol.CAJERO, Rol.COCINERO, Rol.CLIENTE)) {
			Usuario intruso = cuenta(rol);
			for (MockHttpServletRequestBuilder peticion : escrituras(c.getId(), p.getId())) {
				enviar(como(peticion, intruso))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));
			}
		}
	}

	@Test
	void cajeroCocineroYClienteTampocoPuedenLeerElCatalogoDeAdministracion() throws Exception {
		for (Rol rol : List.of(Rol.CAJERO, Rol.COCINERO, Rol.CLIENTE)) {
			Usuario otro = cuenta(rol);
			enviar(como(get("/api/categorias"), otro)).andExpect(status().isForbidden());
			enviar(como(get("/api/productos"), otro)).andExpect(status().isForbidden());
		}
	}

	@Test
	void unIntrusoNoCambiaNingunDato() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 4);
		String nombreCat = c.getNombre();
		String nombreProd = p.getNombre();
		long cats = categorias.count();
		long prods = productos.count();
		Usuario intruso = cuenta(Rol.MESERO);

		enviar(como(json(put("/api/categorias/" + c.getId()), cuerpoCategoria("Hackeada")), intruso))
				.andExpect(status().isForbidden());
		enviar(como(post("/api/categorias/" + c.getId() + "/desactivar"), intruso)).andExpect(status().isForbidden());
		enviar(como(json(put("/api/productos/" + p.getId()),
				cuerpoProducto("Hackeado", "1", c.getId(), null, 0, 0)), intruso)).andExpect(status().isForbidden());
		enviar(como(post("/api/productos/" + p.getId() + "/desactivar"), intruso)).andExpect(status().isForbidden());

		assertThat(categorias.count()).isEqualTo(cats);
		assertThat(productos.count()).isEqualTo(prods);
		assertThat(categorias.findById(c.getId()).orElseThrow().getNombre()).isEqualTo(nombreCat);
		assertThat(categorias.findById(c.getId()).orElseThrow().isActiva()).isTrue();
		assertThat(productos.findById(p.getId()).orElseThrow().getNombre()).isEqualTo(nombreProd);
		assertThat(productos.findById(p.getId()).orElseThrow().isActivo()).isTrue();
	}

	@Test
	void elMeseroPuedeLeerPeroNoVeLoDesactivadoNiPuedePedirlo() throws Exception {
		enviar(como(get("/api/categorias"), mesero)).andExpect(status().isOk());
		enviar(como(get("/api/productos"), mesero)).andExpect(status().isOk());
		enviar(como(get("/api/categorias?todas=true"), mesero)).andExpect(status().isForbidden());
		enviar(como(get("/api/productos?todos=true"), mesero)).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------ categorías

	@Test
	void crearCategoriaLaGuardaActivaLaAuditaYLaPublica() throws Exception {
		String nombre = nombreUnico("Postres");

		ResultActions r = enviar(como(json(post("/api/categorias"), cuerpoCategoria("  " + nombre + "  ")), admin))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.activa").value(true))
				.andExpect(jsonPath("$.totalProductos").value(0));
		long id = idDe(r);
		r.andExpect(header().string("Location", "/api/categorias/" + id));

		assertThat(categorias.findById(id).orElseThrow().getNombre()).isEqualTo(nombre);
		RegistroAuditoria registro = auditoriaDe("CATEGORIA_CREADA", id).get(0);
		assertThat(registro.getUsuario().getId()).isEqualTo(admin.getId());
		ArgumentCaptor<CategoriaEventoDatos> datos = ArgumentCaptor.forClass(CategoriaEventoDatos.class);
		verify(publicador).publicarCategoria(eq(TipoEvento.CATEGORIA_CREADA), datos.capture());
		assertThat(datos.getValue().categoriaId()).isEqualTo(id);
		assertThat(datos.getValue().activa()).isTrue();
	}

	@Test
	void crearCategoriaConNombreInvalidoResponde400ConElCampo() throws Exception {
		for (String malo : new String[] { "", "   ", null, "x".repeat(81) }) {
			enviar(como(json(post("/api/categorias"), cuerpoCategoria(malo)), admin))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.campos.nombre").exists());
		}
		verify(publicador, never()).publicarCategoria(any(), any());
	}

	@Test
	void nombreDeCategoriaRepetidoSinImportarMayusculasResponde409() throws Exception {
		Categoria existente = categoria(true);

		enviar(como(json(post("/api/categorias"), cuerpoCategoria(existente.getNombre().toUpperCase())), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.nombre").value("Ya existe una categoría con ese nombre"));
		verify(publicador, never()).publicarCategoria(any(), any());
	}

	@Test
	void renombrarCategoriaCambiaElNombreLaAuditaYLaPublica() throws Exception {
		Categoria c = categoria(true);
		String nuevo = nombreUnico("Panadería");

		enviar(como(json(put("/api/categorias/" + c.getId()), cuerpoCategoria(nuevo)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value(nuevo));

		assertThat(categorias.findById(c.getId()).orElseThrow().getNombre()).isEqualTo(nuevo);
		assertThat(auditoriaDe("CATEGORIA_RENOMBRADA", c.getId())).hasSize(1);
		verify(publicador).publicarCategoria(eq(TipoEvento.CATEGORIA_ACTUALIZADA), any());
	}

	@Test
	void renombrarAUnNombreQueYaTieneOtraCategoriaResponde409YNoCambiaNada() throws Exception {
		Categoria a = categoria(true);
		Categoria b = categoria(true);
		String original = b.getNombre();

		enviar(como(json(put("/api/categorias/" + b.getId()), cuerpoCategoria(a.getNombre())), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.nombre").exists());

		assertThat(categorias.findById(b.getId()).orElseThrow().getNombre()).isEqualTo(original);
	}

	@Test
	void renombrarConElMismoNombreNoHaceNada() throws Exception {
		Categoria c = categoria(true);

		enviar(como(json(put("/api/categorias/" + c.getId()), cuerpoCategoria(c.getNombre())), admin))
				.andExpect(status().isOk());

		assertThat(auditoriaDe("CATEGORIA_RENOMBRADA", c.getId())).isEmpty();
		verify(publicador, never()).publicarCategoria(any(), any());
	}

	@Test
	void categoriaInexistenteResponde404() throws Exception {
		enviar(como(get("/api/categorias/99999999"), admin)).andExpect(status().isNotFound());
		enviar(como(json(put("/api/categorias/99999999"), cuerpoCategoria("X")), admin)).andExpect(status().isNotFound());
		enviar(como(post("/api/categorias/99999999/desactivar"), admin)).andExpect(status().isNotFound());
		enviar(como(post("/api/categorias/99999999/activar"), admin)).andExpect(status().isNotFound());
	}

	@Test
	void desactivarCategoriaLaMarcaInactivaYEsIdempotente() throws Exception {
		Categoria c = categoria(true);

		enviar(como(post("/api/categorias/" + c.getId() + "/desactivar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activa").value(false));
		enviar(como(post("/api/categorias/" + c.getId() + "/desactivar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activa").value(false));

		assertThat(categorias.findById(c.getId()).orElseThrow().isActiva()).isFalse();
		assertThat(auditoriaDe("CATEGORIA_DESACTIVADA", c.getId())).hasSize(1); // la segunda vez no cambió nada
		ArgumentCaptor<CategoriaEventoDatos> datos = ArgumentCaptor.forClass(CategoriaEventoDatos.class);
		verify(publicador).publicarCategoria(eq(TipoEvento.CATEGORIA_ACTUALIZADA), datos.capture());
		assertThat(datos.getValue().activa()).isFalse();
	}

	@Test
	void laListaDeCategoriasOcultaLasDesactivadasSalvoParaElAdministradorQueLasPide() throws Exception {
		Categoria activa = categoria(true);
		Categoria inactiva = categoria(false);
		producto(inactiva, true, 3);

		enviar(como(get("/api/categorias"), mesero))
				.andExpect(jsonPath("$[?(@.id==" + activa.getId() + ")]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.id==" + inactiva.getId() + ")]", empty()));
		enviar(como(get("/api/categorias"), admin))
				.andExpect(jsonPath("$[?(@.id==" + inactiva.getId() + ")]", empty()));
		String todas = enviar(como(get("/api/categorias?todas=true"), admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Number> totales = JsonPath.read(todas, "$[?(@.id==" + inactiva.getId() + ")].totalProductos");
		List<Boolean> activas = JsonPath.read(todas, "$[?(@.id==" + inactiva.getId() + ")].activa");
		assertThat(totales).as("respuesta: " + todas).hasSize(1);
		assertThat(totales.get(0).intValue()).isEqualTo(1);
		assertThat(activas).containsExactly(false);
		// para el mesero una categoría desactivada no existe
		enviar(como(get("/api/categorias/" + inactiva.getId()), mesero)).andExpect(status().isNotFound());
		enviar(como(get("/api/categorias/" + inactiva.getId()), admin)).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ productos: crear

	@Test
	void crearProductoGuardaTodoRegistraLasExistenciasIniciales() throws Exception {
		Categoria c = categoria(true);
		String nombre = nombreUnico("Latte");

		ResultActions r = enviar(como(json(post("/api/productos"),
				cuerpoProducto("  " + nombre + " ", "6500.50", c.getId(), "https://img.test/latte.png", 12, 4)), admin))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.precio").value(6500.50))
				.andExpect(jsonPath("$.categoriaId").value(c.getId()))
				.andExpect(jsonPath("$.categoriaNombre").value(c.getNombre()))
				.andExpect(jsonPath("$.imagenUrl").value("https://img.test/latte.png"))
				.andExpect(jsonPath("$.stockActual").value(12))
				.andExpect(jsonPath("$.umbralMinimo").value(4))
				.andExpect(jsonPath("$.activo").value(true));
		long id = idDe(r);
		r.andExpect(header().string("Location", "/api/productos/" + id));

		Producto guardado = productos.findById(id).orElseThrow();
		assertThat(guardado.getStockActual()).isEqualTo(12);
		// las existencias iniciales quedan en el historial (RF-14)
		List<MovimientoInventario> historial = movimientos.findAll().stream()
				.filter(m -> m.getProducto().getId().equals(id)).toList();
		assertThat(historial).hasSize(1);
		assertThat(historial.get(0).getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA_MANUAL);
		assertThat(historial.get(0).getCantidad()).isEqualTo(12);
		assertThat(historial.get(0).getStockResultante()).isEqualTo(12);
		assertThat(historial.get(0).getUsuario().getId()).isEqualTo(admin.getId());
		assertThat(auditoriaDe("PRODUCTO_CREADO", id)).hasSize(1);
		ArgumentCaptor<ProductoEventoDatos> datos = ArgumentCaptor.forClass(ProductoEventoDatos.class);
		verify(publicador).publicarProducto(eq(TipoEvento.PRODUCTO_CREADO), datos.capture());
		assertThat(datos.getValue().productoId()).isEqualTo(id);
		assertThat(datos.getValue().categoriaId()).isEqualTo(c.getId());
	}

	@Test
	void crearProductoSinExistenciasNiUmbralNiImagenUsaLosValoresPorDefecto() throws Exception {
		Categoria c = categoria(true);

		ResultActions r = enviar(como(json(post("/api/productos"),
				"{\"nombre\":\"" + nombreUnico("Té") + "\",\"precio\":3000,\"categoriaId\":" + c.getId() + "}"), admin))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.stockActual").value(0))
				.andExpect(jsonPath("$.umbralMinimo").value(0))
				.andExpect(jsonPath("$.imagenUrl").doesNotExist());
		long id = idDe(r);

		assertThat(movimientos.findAll().stream().filter(m -> m.getProducto().getId().equals(id))).isEmpty();
	}

	@Test
	void crearProductoConDatosInvalidosResponde400ConCadaCampo() throws Exception {
		Categoria c = categoria(true);
		long productosAntes = productos.count();

		enviar(como(json(post("/api/productos"), cuerpoProducto("", "1000", c.getId(), null, 1, 1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nombre").exists());
		enviar(como(json(post("/api/productos"), cuerpoProducto("x".repeat(121), "1000", c.getId(), null, 1, 1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nombre").exists());
		for (String precio : new String[] { "0", "-5", "10.555", "null" }) {
			enviar(como(json(post("/api/productos"), cuerpoProducto("P", precio, c.getId(), null, 1, 1)), admin))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.precio").exists());
		}
		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", null, null, 1, 1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.categoriaId").exists());
		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", c.getId(), "ftp://x/y.png", 1, 1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.imagenUrl").exists());
		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", c.getId(), null, -1, 1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.existenciasIniciales").exists());
		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", c.getId(), null, 1, -1)), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.umbralMinimo").exists());
		enviar(como(json(post("/api/productos"), "{esto no es json"), admin))
				.andExpect(status().isBadRequest());

		assertThat(productos.count()).isEqualTo(productosAntes);
		verify(publicador, never()).publicarProducto(any(), any());
	}

	// ------------------------------------------------------------------ productos: integridad referencial

	@Test
	void crearProductoEnUnaCategoriaQueNoExisteResponde400() throws Exception {
		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", 99999999L, null, 1, 1)), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.categoriaId").value("La categoría no existe"));
	}

	@Test
	void crearProductoEnUnaCategoriaDesactivadaResponde409() throws Exception {
		Categoria inactiva = categoria(false);
		long antes = productos.count();

		enviar(como(json(post("/api/productos"), cuerpoProducto("P", "1000", inactiva.getId(), null, 1, 1)), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.categoriaId").value("La categoría está desactivada"));

		assertThat(productos.count()).isEqualTo(antes);
	}

	@Test
	void noExisteNingunaOperacionParaBorrar() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 1);

		enviar(como(delete("/api/productos/" + p.getId()), admin)).andExpect(status().isMethodNotAllowed());
		enviar(como(delete("/api/categorias/" + c.getId()), admin)).andExpect(status().isMethodNotAllowed());

		assertThat(productos.existsById(p.getId())).isTrue();
		assertThat(categorias.existsById(c.getId())).isTrue();
	}

	// ------------------------------------------------------------------ productos: editar

	@Test
	void editarProductoCambiaSusDatosSinTocarLasExistencias() throws Exception {
		Categoria origen = categoria(true);
		Categoria destino = categoria(true);
		Producto p = producto(origen, true, 7);
		String nuevoNombre = nombreUnico("Mocha");

		enviar(como(json(put("/api/productos/" + p.getId()),
				"""
				{"nombre":"%s","descripcion":"Con chocolate","precio":8200,"categoriaId":%d,
				"imagenUrl":"https://img.test/m.png","umbralMinimo":5,"stockActual":999}
				""".formatted(nuevoNombre, destino.getId())), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value(nuevoNombre))
				.andExpect(jsonPath("$.precio").value(8200.0))
				.andExpect(jsonPath("$.categoriaId").value(destino.getId()))
				.andExpect(jsonPath("$.umbralMinimo").value(5))
				.andExpect(jsonPath("$.stockActual").value(7)); // las existencias solo cambian con ajustes (RF-07)

		Producto despues = productos.findById(p.getId()).orElseThrow();
		assertThat(despues.getCategoria().getId()).isEqualTo(destino.getId());
		assertThat(despues.getStockActual()).isEqualTo(7);
		assertThat(auditoriaDe("PRODUCTO_EDITADO", p.getId())).hasSize(1);
		verify(publicador).publicarProducto(eq(TipoEvento.PRODUCTO_ACTUALIZADO), any());
	}

	@Test
	void editarSinCambiarNadaNoAuditaNiPublica() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 2);

		enviar(como(json(put("/api/productos/" + p.getId()),
				cuerpoProducto(p.getNombre(), "5000", c.getId(), null, null, null).replace("\"Rico\"", "null")), admin))
				.andExpect(status().isOk());

		assertThat(auditoriaDe("PRODUCTO_EDITADO", p.getId())).isEmpty();
		verify(publicador, never()).publicarProducto(any(), any());
	}

	@Test
	void moverUnProductoAUnaCategoriaDesactivadaOInexistenteFallaYNoDejaCambiosAMedias() throws Exception {
		Categoria origen = categoria(true);
		Categoria inactiva = categoria(false);
		Producto p = producto(origen, true, 2);
		String nombre = p.getNombre();

		enviar(como(json(put("/api/productos/" + p.getId()),
				cuerpoProducto("Otro nombre", "7000", inactiva.getId(), null, 0, 0)), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.categoriaId").exists());
		enviar(como(json(put("/api/productos/" + p.getId()),
				cuerpoProducto("Otro nombre", "7000", 99999999L, null, 0, 0)), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.categoriaId").exists());

		Producto despues = productos.findById(p.getId()).orElseThrow();
		assertThat(despues.getNombre()).isEqualTo(nombre);
		assertThat(despues.getPrecio()).isEqualByComparingTo("5000.00");
		assertThat(despues.getCategoria().getId()).isEqualTo(origen.getId());
	}

	@Test
	void unProductoEnCategoriaDesactivadaSePuedeSeguirEditandoSiNoCambiaDeCategoria() throws Exception {
		Categoria inactiva = categoria(false);
		Producto p = producto(inactiva, true, 2);

		enviar(como(json(put("/api/productos/" + p.getId()),
				cuerpoProducto(p.getNombre(), "5500", inactiva.getId(), null, null, null)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(5500.0))
				.andExpect(jsonPath("$.categoriaActiva").value(false));
	}

	@Test
	void productoInexistenteResponde404() throws Exception {
		Categoria c = categoria(true);
		enviar(como(get("/api/productos/99999999"), admin)).andExpect(status().isNotFound());
		enviar(como(json(put("/api/productos/99999999"), cuerpoProducto("P", "1000", c.getId(), null, 0, 0)), admin))
				.andExpect(status().isNotFound());
		enviar(como(post("/api/productos/99999999/desactivar"), admin)).andExpect(status().isNotFound());
		enviar(como(post("/api/productos/99999999/activar"), admin)).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------ productos: desactivar y menú

	@Test
	void desactivarProductoLoSacaDelMenuPeroElAdministradorSigueViendolo() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 5);

		enviar(como(get("/api/productos?categoriaId=" + c.getId()), mesero))
				.andExpect(jsonPath("$[?(@.id==" + p.getId() + ")]", hasSize(1)));

		enviar(como(post("/api/productos/" + p.getId() + "/desactivar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));

		enviar(como(get("/api/productos?categoriaId=" + c.getId()), mesero))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", empty()));
		enviar(como(get("/api/productos/" + p.getId()), mesero)).andExpect(status().isNotFound());
		enviar(como(get("/api/productos?categoriaId=" + c.getId()), admin)).andExpect(jsonPath("$", empty()));
		enviar(como(get("/api/productos?todos=true&categoriaId=" + c.getId()), admin))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].activo").value(false));
		enviar(como(get("/api/productos/" + p.getId()), admin)).andExpect(status().isOk());

		assertThat(auditoriaDe("PRODUCTO_DESACTIVADO", p.getId())).hasSize(1);
		verify(publicador).publicarProducto(eq(TipoEvento.PRODUCTO_ACTUALIZADO), any());
	}

	@Test
	void desactivarYActivarProductoSonIdempotentesYReversibles() throws Exception {
		Categoria c = categoria(true);
		Producto p = producto(c, true, 5);

		enviar(como(post("/api/productos/" + p.getId() + "/activar"), admin)) // ya estaba activo
				.andExpect(status().isOk());
		assertThat(auditoriaDe("PRODUCTO_ACTIVADO", p.getId())).isEmpty();

		enviar(como(post("/api/productos/" + p.getId() + "/desactivar"), admin)).andExpect(status().isOk());
		enviar(como(post("/api/productos/" + p.getId() + "/desactivar"), admin)).andExpect(status().isOk());
		assertThat(auditoriaDe("PRODUCTO_DESACTIVADO", p.getId())).hasSize(1);

		enviar(como(post("/api/productos/" + p.getId() + "/activar"), admin))
				.andExpect(jsonPath("$.activo").value(true));
		enviar(como(get("/api/productos/" + p.getId()), mesero)).andExpect(status().isOk());
	}

	@Test
	void losProductosDeUnaCategoriaDesactivadaNoApareceEnElMenuYVuelvenAlReactivarla() throws Exception {
		Categoria c = categoria(true);
		Producto uno = producto(c, true, 5);
		Producto dos = producto(c, true, 0);
		Categoria otra = categoria(true);
		Producto ajeno = producto(otra, true, 5);

		enviar(como(post("/api/categorias/" + c.getId() + "/desactivar"), admin)).andExpect(status().isOk());

		// el mesero ya no los ve, ni en la lista ni por id; los de otra categoría siguen
		enviar(como(get("/api/productos"), mesero))
				.andExpect(jsonPath("$[?(@.id==" + uno.getId() + ")]", empty()))
				.andExpect(jsonPath("$[?(@.id==" + dos.getId() + ")]", empty()))
				.andExpect(jsonPath("$[?(@.id==" + ajeno.getId() + ")]", hasSize(1)));
		enviar(como(get("/api/productos?categoriaId=" + c.getId()), mesero)).andExpect(jsonPath("$", empty()));
		enviar(como(get("/api/productos/" + uno.getId()), mesero)).andExpect(status().isNotFound());
		// el administrador los ve con la marca de categoría desactivada
		enviar(como(get("/api/productos?todos=true&categoriaId=" + c.getId()), admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].categoriaActiva").value(false))
				.andExpect(jsonPath("$[0].activo").value(true));
		// desactivar la categoría no desactiva los productos: siguen activos por dentro
		assertThat(productos.findById(uno.getId()).orElseThrow().isActivo()).isTrue();

		enviar(como(post("/api/categorias/" + c.getId() + "/activar"), admin)).andExpect(status().isOk());

		enviar(como(get("/api/productos?categoriaId=" + c.getId()), mesero))
				.andExpect(jsonPath("$", hasSize(2)));
		assertThat(auditoriaDe("CATEGORIA_ACTIVADA", c.getId())).hasSize(1);
	}

	@Test
	void unProductoActivoNoApareceSiSuCategoriaSigueDesactivadaAunqueSeReactive() throws Exception {
		Categoria inactiva = categoria(false);
		Producto p = producto(inactiva, false, 5);

		enviar(como(post("/api/productos/" + p.getId() + "/activar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.categoriaActiva").value(false));

		enviar(como(get("/api/productos/" + p.getId()), mesero)).andExpect(status().isNotFound());
	}

	@Test
	void laListaDeProductosVieneOrdenadaPorNombre() throws Exception {
		Categoria c = categoria(true);
		for (String nombre : new String[] { "zeta", "Alfa", "beta" }) {
			Producto p = producto(c, true, 1);
			p.setNombre(nombre);
		}

		enviar(como(get("/api/productos?categoriaId=" + c.getId()), mesero))
				.andExpect(jsonPath("$[0].nombre").value("Alfa"))
				.andExpect(jsonPath("$[1].nombre").value("beta"))
				.andExpect(jsonPath("$[2].nombre").value("zeta"))
				.andExpect(jsonPath("$[0].precio", not(empty())));
	}

}
