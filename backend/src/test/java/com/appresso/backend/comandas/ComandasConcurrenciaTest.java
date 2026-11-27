package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.CategoriaRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoRepository;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cada operación confirma su propia transacción, como dos peticiones de dispositivos distintos. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:comandas-concurrencia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ComandasConcurrenciaTest {

	@Autowired ComandaService comandas;
	@Autowired MesaService servicioMesas;
	@Autowired MesaRepository mesas;
	@Autowired CategoriaRepository categorias;
	@Autowired ProductoRepository productos;
	@Autowired UsuarioRepository usuarios;
	@Autowired JdbcTemplate jdbc;

	@Test
	void dosSolicitudesParaLaMismaMesaSoloCreanUnaComanda() throws Exception {
		Mesa mesa = new Mesa();
		mesa.setNombre("Mesa " + UUID.randomUUID().toString().substring(0, 8));
		long mesaId = mesas.saveAndFlush(mesa).getId();
		Categoria categoria = new Categoria();
		categoria.setNombre("Bebidas");
		categoria = categorias.saveAndFlush(categoria);
		Producto producto = new Producto();
		producto.setNombre("Café");
		producto.setCategoria(categoria);
		producto.setPrecio(BigDecimal.ONE);
		producto.setStockActual(2);
		long productoId = productos.saveAndFlush(producto).getId();
		long primerMesero = cuenta(Rol.MESERO);
		long segundoMesero = cuenta(Rol.MESERO);
		var pedido = List.of(new ComandaService.ItemSolicitado(productoId, 1, null));

		assertThat(enParalelo(
				() -> comandas.crear(mesaId, primerMesero, pedido),
				() -> comandas.crear(mesaId, segundoMesero, pedido)))
				.containsExactlyInAnyOrder("creada", "mesaId");
		assertThat(jdbc.queryForObject("select count(*) from comandas where mesa_id = ? and estado <> 'CERRADA'",
				Long.class, mesaId)).isEqualTo(1L);
	}

	@Test
	void dosAltasConElMismoNombreNormalizadoSoloCreanUnaMesa() throws Exception {
		long primerAdmin = cuenta(Rol.ADMINISTRADOR);
		long segundoAdmin = cuenta(Rol.ADMINISTRADOR);
		assertThat(enParalelo(
				() -> servicioMesas.crear(new MesaRequest(" Terraza 2 "), primerAdmin),
				() -> servicioMesas.crear(new MesaRequest("TERRAZA 2"), segundoAdmin)))
				.containsExactlyInAnyOrder("creada", "nombre");
		assertThat(jdbc.queryForObject("select count(*) from mesas where nombre_normalizado = ?",
				Long.class, "terraza 2")).isEqualTo(1L);
	}

	private long cuenta(Rol rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Prueba");
		usuario.setCorreo(UUID.randomUUID() + "@appresso.test");
		usuario.setContrasenaHash("solo-datos-de-prueba");
		usuario.setRol(rol);
		return usuarios.saveAndFlush(usuario).getId();
	}

	private List<String> enParalelo(Runnable primera, Runnable segunda) throws Exception {
		var barrera = new CyclicBarrier(2);
		try (var hilos = Executors.newFixedThreadPool(2)) {
			var una = hilos.submit(trasLaBarrera(barrera, primera));
			var otra = hilos.submit(trasLaBarrera(barrera, segunda));
			return List.of(una.get(15, TimeUnit.SECONDS), otra.get(15, TimeUnit.SECONDS));
		}
	}

	private Callable<String> trasLaBarrera(CyclicBarrier barrera, Runnable operacion) {
		return () -> {
			barrera.await(5, TimeUnit.SECONDS);
			try {
				operacion.run();
				return "creada";
			}
			catch (ConflictoComandaException e) {
				return e.getCampo();
			}
		};
	}
}
