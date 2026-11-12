package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.comandas.ComandaService.ItemSolicitado;
import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.CategoriaRepository;
import com.appresso.backend.inventario.MovimientoInventarioRepository;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.inventario.ProductoService;
import com.appresso.backend.inventario.TipoMovimientoInventario;
import com.appresso.backend.tiemporeal.ComandaEventoDatos;
import com.appresso.backend.tiemporeal.ProductoEventoDatos;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * SCRUM-28: cada operación de los servicios publica el evento correcto y respeta las reglas de los
 * RF (RF-07, RF-12, RF-15, RF-16, RF-18, RF-20, RF-COMANDA-ESTADO). El publicador se simula; el envío
 * real por WebSocket ya lo prueba TiempoRealTest.
 */
@SpringBootTest
@Transactional
class ServiciosTiempoRealTest {

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired ComandaService comandaService;
	@Autowired ProductoService productoService;
	@Autowired MesaRepository mesas;
	@Autowired UsuarioRepository usuarios;
	@Autowired CategoriaRepository categorias;
	@Autowired MovimientoInventarioRepository movimientos;

	Usuario mesero;
	Usuario cocinero;
	Usuario cajero;
	Mesa mesa;
	Producto cafe;

	@BeforeEach
	void datos() {
		mesero = usuario(Rol.MESERO);
		cocinero = usuario(Rol.COCINERO);
		cajero = usuario(Rol.CAJERO);

		mesa = new Mesa();
		mesa.setNombre("M" + System.nanoTime() % 100000);
		mesas.save(mesa);

		Categoria cat = new Categoria();
		cat.setNombre("Bebidas " + System.nanoTime());
		categorias.save(cat);
		cafe = productoService.crear(cat.getId(), "Café", "Americano", new BigDecimal("4500.00"), 10, 3);
	}

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre(rol.name());
		u.setCorreo(rol.name().toLowerCase() + System.nanoTime() + "@appresso.test");
		u.setContrasenaHash("x");
		u.setRol(rol);
		return usuarios.save(u);
	}

	private Comanda nuevaComanda() {
		return comandaService.crear(mesa.getId(), mesero.getId(),
				List.of(new ItemSolicitado(cafe.getId(), 2, "sin azúcar")));
	}

	private void enviar(Comanda c) {
		comandaService.cambiarEstado(c.getId(), EstadoComanda.ENVIADA, mesero.getId());
	}

	// ---------- productos / inventario ----------

	@Test
	void crearProductoPublicaProductoCreado() {
		ArgumentCaptor<ProductoEventoDatos> datos = ArgumentCaptor.forClass(ProductoEventoDatos.class);
		verify(publicador).publicarProducto(eq(TipoEvento.PRODUCTO_CREADO), datos.capture());
		assertThat(datos.getValue().productoId()).isEqualTo(cafe.getId());
		assertThat(datos.getValue().stockActual()).isEqualTo(10);
	}

	@Test
	void actualizarProductoPublicaProductoActualizado() {
		productoService.actualizar(cafe.getId(), "Café", "Americano", new BigDecimal("5000.00"), 3, false);
		ArgumentCaptor<ProductoEventoDatos> datos = ArgumentCaptor.forClass(ProductoEventoDatos.class);
		verify(publicador).publicarProducto(eq(TipoEvento.PRODUCTO_ACTUALIZADO), datos.capture());
		assertThat(datos.getValue().precio()).isEqualByComparingTo("5000.00");
		assertThat(datos.getValue().activo()).isFalse();
	}

	@Test
	void ajusteManualPublicaExistenciasYRechazaStockNegativo() {
		productoService.ajustarExistencias(cafe.getId(), TipoMovimientoInventario.SALIDA_MANUAL, 4, "uso", mesero.getId());
		ArgumentCaptor<ProductoEventoDatos> datos = ArgumentCaptor.forClass(ProductoEventoDatos.class);
		verify(publicador).publicarProducto(eq(TipoEvento.EXISTENCIAS_ACTUALIZADAS), datos.capture());
		assertThat(datos.getValue().stockActual()).isEqualTo(6);

		assertThatThrownBy(() -> productoService.ajustarExistencias(cafe.getId(),
				TipoMovimientoInventario.AJUSTE_MERMA, 7, "merma", mesero.getId()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void ajusteManualExigeJustificacionYCantidadPositiva() {
		assertThatThrownBy(() -> productoService.ajustarExistencias(cafe.getId(),
				TipoMovimientoInventario.ENTRADA_MANUAL, 5, " ", mesero.getId()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> productoService.ajustarExistencias(cafe.getId(),
				TipoMovimientoInventario.ENTRADA_MANUAL, 0, "x", mesero.getId()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ---------- comandas ----------

	@Test
	void crearComandaPublicaCreadaYOcupaLaMesa() {
		Comanda c = nuevaComanda();
		ArgumentCaptor<ComandaEventoDatos> datos = ArgumentCaptor.forClass(ComandaEventoDatos.class);
		verify(publicador).publicarComanda(eq(TipoEvento.COMANDA_CREADA), datos.capture());
		assertThat(datos.getValue().comandaId()).isEqualTo(c.getId());
		assertThat(datos.getValue().estado()).isEqualTo("PENDIENTE");
		assertThat(datos.getValue().total()).isEqualByComparingTo("9000.00");
		assertThat(datos.getValue().itemsAgregados()).hasSize(1);
		assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.OCUPADA);
	}

	@Test
	void noSePuedeCrearOtraComandaEnMesaOcupada() {
		nuevaComanda();
		assertThatThrownBy(this::nuevaComanda).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void noSePuedePedirMasDeLasExistenciasNiProductoDesactivado() {
		assertThatThrownBy(() -> comandaService.crear(mesa.getId(), mesero.getId(),
				List.of(new ItemSolicitado(cafe.getId(), 11, null))))
				.isInstanceOf(IllegalStateException.class);
		// dos líneas del mismo producto cuentan juntas
		assertThatThrownBy(() -> comandaService.crear(mesa.getId(), mesero.getId(),
				List.of(new ItemSolicitado(cafe.getId(), 6, null), new ItemSolicitado(cafe.getId(), 5, null))))
				.isInstanceOf(IllegalStateException.class);

		productoService.actualizar(cafe.getId(), "Café", "Americano", cafe.getPrecio(), 3, false);
		assertThatThrownBy(this::nuevaComanda).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void observacionesDeMasDe100CaracteresSeRechazan() {
		assertThatThrownBy(() -> comandaService.crear(mesa.getId(), mesero.getId(),
				List.of(new ItemSolicitado(cafe.getId(), 1, "x".repeat(101)))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void enviarDescuentaInventarioYGuardaMovimientoVenta() {
		Comanda c = nuevaComanda();
		enviar(c);
		assertThat(cafe.getStockActual()).isEqualTo(8);
		assertThat(c.getFechaEnvio()).isNotNull();
		assertThat(movimientos.findAll()).anySatisfy(m -> {
			assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.VENTA);
			assertThat(m.getComanda().getId()).isEqualTo(c.getId());
			assertThat(m.getStockResultante()).isEqualTo(8);
		});
		verify(publicador).publicarProducto(eq(TipoEvento.EXISTENCIAS_ACTUALIZADAS), any());
	}

	@Test
	void cicloCompletoPublicaCuatroCambiosDeEstadoYLiberaMesa() {
		Comanda c = nuevaComanda();
		enviar(c);
		comandaService.cambiarEstado(c.getId(), EstadoComanda.EN_PREPARACION, cocinero.getId());
		comandaService.cambiarEstado(c.getId(), EstadoComanda.LISTA, cocinero.getId());
		comandaService.cambiarEstado(c.getId(), EstadoComanda.CERRADA, cajero.getId());

		ArgumentCaptor<ComandaEventoDatos> datos = ArgumentCaptor.forClass(ComandaEventoDatos.class);
		verify(publicador, times(4)).publicarComanda(eq(TipoEvento.COMANDA_ESTADO_CAMBIADO), datos.capture());
		ComandaEventoDatos ultimo = datos.getAllValues().get(3);
		assertThat(ultimo.estado()).isEqualTo("CERRADA");
		assertThat(ultimo.estadoAnterior()).isEqualTo("LISTA");
		assertThat(mesa.getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
	}

	@Test
	void saltarseUnEstadoOUsarOtroRolNoPublicaNada() {
		Comanda c = nuevaComanda();
		assertThatThrownBy(() -> comandaService.cambiarEstado(c.getId(), EstadoComanda.LISTA, cocinero.getId()))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> comandaService.cambiarEstado(c.getId(), EstadoComanda.ENVIADA, cocinero.getId()))
				.isInstanceOf(IllegalStateException.class);
		verify(publicador, never()).publicarComanda(eq(TipoEvento.COMANDA_ESTADO_CAMBIADO), any());
	}

	@Test
	void agregarProductoDespuesDeEnvioPublicaModificadaMarcaYDescuenta() {
		Comanda c = nuevaComanda();
		enviar(c);
		comandaService.agregarProducto(c.getId(), new ItemSolicitado(cafe.getId(), 1, null), mesero.getId());

		ArgumentCaptor<ComandaEventoDatos> datos = ArgumentCaptor.forClass(ComandaEventoDatos.class);
		verify(publicador).publicarComanda(eq(TipoEvento.COMANDA_MODIFICADA), datos.capture());
		assertThat(datos.getValue().total()).isEqualByComparingTo("13500.00");
		assertThat(c.getDetalles()).hasSize(2);
		assertThat(c.getDetalles().get(1).isAgregadoDespuesDeEnvio()).isTrue();
		assertThat(cafe.getStockActual()).isEqualTo(7);
	}

	@Test
	void comandaListaAunSePuedeAmpliarPeroPagadaNo() {
		Comanda c = nuevaComanda();
		enviar(c);
		comandaService.cambiarEstado(c.getId(), EstadoComanda.EN_PREPARACION, cocinero.getId());
		comandaService.cambiarEstado(c.getId(), EstadoComanda.LISTA, cocinero.getId());
		comandaService.agregarProducto(c.getId(), new ItemSolicitado(cafe.getId(), 1, null), mesero.getId());

		comandaService.cambiarEstado(c.getId(), EstadoComanda.CERRADA, cajero.getId());
		assertThatThrownBy(() -> comandaService.agregarProducto(c.getId(),
				new ItemSolicitado(cafe.getId(), 1, null), mesero.getId()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void agregarMasDeLoQueHayDespuesDelEnvioSeRechaza() {
		Comanda c = nuevaComanda();
		enviar(c); // quedan 8
		assertThatThrownBy(() -> comandaService.agregarProducto(c.getId(),
				new ItemSolicitado(cafe.getId(), 9, null), mesero.getId()))
				.isInstanceOf(IllegalStateException.class);
		verify(publicador, never()).publicarComanda(eq(TipoEvento.COMANDA_MODIFICADA), any());
	}

}
