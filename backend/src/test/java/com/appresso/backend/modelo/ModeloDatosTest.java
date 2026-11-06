package com.appresso.backend.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.appresso.backend.caja.EstadoPago;
import com.appresso.backend.caja.Jornada;
import com.appresso.backend.caja.MedioPago;
import com.appresso.backend.caja.Pago;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.DetalleComanda;
import com.appresso.backend.comandas.EstadoComanda;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.fidelizacion.Cliente;
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
		assertThat(guardada.getEstado()).isEqualTo(EstadoComanda.BORRADOR);
		assertThat(guardada.getDetalles()).hasSize(1);
		assertThat(guardada.getDetalles().get(0).getObservaciones()).isEqualTo("sin azúcar");
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
		pago.setEstado(EstadoPago.CONFIRMADO);
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

}
