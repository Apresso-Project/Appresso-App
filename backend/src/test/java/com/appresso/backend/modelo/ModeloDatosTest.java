package com.appresso.backend.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.caja.Jornada;
import com.appresso.backend.caja.MedioPago;
import com.appresso.backend.caja.Pago;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.DetalleComanda;
import com.appresso.backend.comandas.EstadoComanda;
import com.appresso.backend.comandas.EstadoMesa;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.fidelizacion.CanjeRecompensa;
import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.Promocion;
import com.appresso.backend.fidelizacion.Recompensa;
import com.appresso.backend.fidelizacion.TipoRecompensa;
import com.appresso.backend.inventario.Categoria;
import com.appresso.backend.inventario.Producto;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.metamodel.EntityType;

/**
 * Comprueba que el modelo de datos se puede mapear a tablas y que las reglas
 * de unicidad principales se cumplen. En las pruebas Hibernate crea el esquema
 * en H2 (ver src/test/resources/application.properties).
 */
@SpringBootTest
@Transactional
class ModeloDatosTest {

	private static final Set<String> ENTIDADES_ESPERADAS = Set.of(
			"Usuario", "DispositivoPush",
			"Categoria", "Producto", "MovimientoInventario",
			"Mesa", "Comanda", "DetalleComanda", "HistorialComanda",
			"Jornada", "Pago", "MovimientoCaja", "SolicitudRetiro", "InformeCierre",
			"ConfiguracionEstablecimiento",
			"Cliente", "Recompensa", "Promocion", "CanjeRecompensa", "Notificacion",
			"RegistroAuditoria");

	@Autowired
	private EntityManager em;

	@Autowired
	private Clock reloj;

	@Test
	void estanMapeadasTodasLasEntidades() {
		Set<String> mapeadas = em.getMetamodel().getEntities().stream()
				.map(EntityType::getName)
				.collect(Collectors.toSet());
		assertThat(mapeadas).isEqualTo(ENTIDADES_ESPERADAS);
	}

	@Test
	void guardaUnaComandaConSusDetalles() {
		Comanda comanda = nuevaComanda();
		DetalleComanda detalle = new DetalleComanda();
		detalle.setProducto(nuevoProducto());
		detalle.setCantidad(2);
		detalle.setPrecioUnitario(new BigDecimal("4500.00"));
		detalle.setObservaciones("sin azúcar");
		comanda.agregarDetalle(detalle);
		em.persist(comanda);
		em.flush();
		em.clear();

		Comanda guardada = em.find(Comanda.class, comanda.getId());
		assertThat(guardada.getEstado()).isEqualTo(EstadoComanda.PENDIENTE);
		assertThat(guardada.getDetalles()).hasSize(1);
		assertThat(guardada.getDetalles().get(0).getObservaciones()).isEqualTo("sin azúcar");
	}

	@Test
	void laComandaTieneCincoEstadosEnElOrdenDelRequisito() {
		assertThat(EstadoComanda.values()).containsExactly(
				EstadoComanda.PENDIENTE, EstadoComanda.ENVIADA, EstadoComanda.EN_PREPARACION,
				EstadoComanda.LISTA, EstadoComanda.CERRADA);
	}

	@Test
	void elNegocioUsaLaHoraDeColombia() {
		assertThat(reloj.getZone()).isEqualTo(ZoneId.of("America/Bogota"));

		// 8:00 p. m. del 1 de diciembre en Bogotá son las 01:00 UTC del 2 de diciembre.
		Instant ochoDeLaNoche = Instant.parse("2026-12-02T01:00:00Z");
		assertThat(ochoDeLaNoche.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 2));
		assertThat(ochoDeLaNoche.atZone(reloj.getZone()).toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 1));
	}

	@Test
	void unaMesaNuevaEstaDisponible() {
		Mesa mesa = new Mesa();
		mesa.setNombre("Mesa " + System.nanoTime());
		em.persist(mesa);
		em.flush();
		em.clear();

		assertThat(em.find(Mesa.class, mesa.getId()).getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
	}

	@Test
	void unPagoSeRegistraSiempreConQuienLoConfirmo() {
		Comanda comanda = nuevaComanda();
		em.persist(comanda);
		Jornada jornada = new Jornada();
		jornada.setAbiertaPor(comanda.getMesero());
		em.persist(jornada);
		Pago pago = nuevoPago(comanda, jornada);
		pago.setConfirmadoPor(null);

		assertThatThrownBy(() -> {
			em.persist(pago);
			em.flush();
		}).isInstanceOf(PersistenceException.class);
	}

	@Test
	void unaComandaSoloAdmiteUnPago() {
		Comanda comanda = nuevaComanda();
		em.persist(comanda);
		Jornada jornada = new Jornada();
		jornada.setAbiertaPor(comanda.getMesero());
		em.persist(jornada);

		em.persist(nuevoPago(comanda, jornada));
		em.flush();

		assertThatThrownBy(() -> {
			em.persist(nuevoPago(comanda, jornada));
			em.flush();
		}).isInstanceOf(PersistenceException.class);
	}

	@Test
	void elDocumentoDelClienteEsUnico() {
		em.persist(nuevoCliente("1234567890"));
		em.flush();

		assertThatThrownBy(() -> {
			em.persist(nuevoCliente("1234567890"));
			em.flush();
		}).isInstanceOf(PersistenceException.class);
	}

	@Test
	void unClienteNuevoEmpiezaConCeroPuntos() {
		Cliente cliente = nuevoCliente("111");
		em.persist(cliente);
		em.flush();
		em.clear();

		assertThat(em.find(Cliente.class, cliente.getId()).getPuntos()).isZero();
	}

	@Test
	void recompensasConElMismoUmbralPuedenCoexistirSiUnaEstaInactiva() {
		em.persist(nuevaRecompensa("Café gratis", 10, 10, true));
		em.persist(nuevaRecompensa("Café gratis (anterior)", 10, 8, false));
		em.flush();

		Long total = em.createQuery("select count(r) from Recompensa r where r.puntosMinimos = 10", Long.class)
				.getSingleResult();
		assertThat(total).isEqualTo(2);
	}

	@Test
	void unCanjeGuardaLosPuntosCobradosYUnaSolaOrigen() {
		Comanda comanda = nuevaComanda();
		em.persist(comanda);
		Cliente cliente = nuevoCliente("222");
		cliente.setPuntos(12);
		em.persist(cliente);
		Recompensa recompensa = nuevaRecompensa("Postre gratis", 10, 10, true);
		em.persist(recompensa);

		CanjeRecompensa canje = nuevoCanje(cliente, comanda);
		canje.setRecompensa(recompensa);
		em.persist(canje);
		em.flush();
		em.clear();
		assertThat(em.find(CanjeRecompensa.class, canje.getId()).getPuntosDescontados()).isEqualTo(10);

		// Sin recompensa ni promoción: no es válido.
		assertThatThrownBy(() -> em.persist(nuevoCanje(cliente, comanda)))
				.isInstanceOf(IllegalStateException.class);

		// Con ambas: tampoco.
		Promocion promocion = nuevaPromocion();
		em.persist(promocion);
		CanjeRecompensa doble = nuevoCanje(cliente, comanda);
		doble.setRecompensa(recompensa);
		doble.setPromocion(promocion);
		assertThatThrownBy(() -> em.persist(doble)).isInstanceOf(IllegalStateException.class);
	}

	// --- Ayudas para armar datos de prueba ---

	private Comanda nuevaComanda() {
		Usuario mesero = new Usuario();
		mesero.setNombre("Mesero Prueba");
		mesero.setCorreo("mesero" + System.nanoTime() + "@appresso.test");
		mesero.setContrasenaHash("hash-de-prueba");
		mesero.setRol(Rol.MESERO);
		em.persist(mesero);

		Mesa mesa = new Mesa();
		mesa.setNombre("Mesa " + System.nanoTime());
		em.persist(mesa);

		Comanda comanda = new Comanda();
		comanda.setMesa(mesa);
		comanda.setMesero(mesero);
		return comanda;
	}

	private Producto nuevoProducto() {
		Categoria categoria = new Categoria();
		categoria.setNombre("Bebidas " + System.nanoTime());
		em.persist(categoria);

		Producto producto = new Producto();
		producto.setCategoria(categoria);
		producto.setNombre("Café americano");
		producto.setPrecio(new BigDecimal("4500.00"));
		producto.setStockActual(20);
		em.persist(producto);
		return producto;
	}

	private Pago nuevoPago(Comanda comanda, Jornada jornada) {
		Pago pago = new Pago();
		pago.setComanda(comanda);
		pago.setJornada(jornada);
		pago.setValor(new BigDecimal("9000.00"));
		pago.setMedio(MedioPago.EFECTIVO);
		pago.setConfirmadoPor(comanda.getMesero());
		return pago;
	}

	private Cliente nuevoCliente(String documento) {
		Cliente cliente = new Cliente();
		cliente.setNombre("Ana");
		cliente.setApellido("Pérez");
		cliente.setDocumento(documento);
		cliente.setCelular("3000000000");
		cliente.setAceptoTratamientoDatos(true);
		return cliente;
	}

	private Recompensa nuevaRecompensa(String nombre, int puntosMinimos, int costoPuntos, boolean activa) {
		Recompensa r = new Recompensa();
		r.setNombre(nombre);
		r.setTipo(TipoRecompensa.DESCUENTO_VALOR);
		r.setValor(new BigDecimal("5000.00"));
		r.setPuntosMinimos(puntosMinimos);
		r.setCostoPuntos(costoPuntos);
		r.setActiva(activa);
		return r;
	}

	private Promocion nuevaPromocion() {
		Promocion p = new Promocion();
		p.setTitulo("2x1 en cappuccino");
		p.setCostoPuntos(6);
		p.setVigenteDesde(LocalDate.of(2026, 12, 1));
		p.setVigenteHasta(LocalDate.of(2026, 12, 7));
		return p;
	}

	private CanjeRecompensa nuevoCanje(Cliente cliente, Comanda comanda) {
		CanjeRecompensa c = new CanjeRecompensa();
		c.setCliente(cliente);
		c.setComanda(comanda);
		c.setUsuario(comanda.getMesero());
		c.setPuntosDescontados(10);
		c.setValorDescontado(new BigDecimal("5000.00"));
		return c;
	}

}
