package com.appresso.backend.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.ComandaRepository;
import com.appresso.backend.comandas.EstadoComanda;
import com.appresso.backend.comandas.EstadoMesa;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.comandas.MesaRepository;
import com.appresso.backend.tiemporeal.PublicadorTiempoReal;
import com.appresso.backend.tiemporeal.TipoEvento;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Pruebas del registro de pagos (6.1.1): quién puede confirmar cada medio, un solo pago por
 * comanda, cierre de la comanda y, sobre todo, que los saldos de efectivo y transferencias no se
 * cruzan. Usan la cadena de seguridad real y se deshacen al terminar cada prueba.
 */
@SpringBootTest
@Transactional
class PagoControllerTest {

	private static final String CLAVE = "Clave-Segura-1";
	private static final BigDecimal TOTAL = new BigDecimal("12500.00");

	@MockitoBean
	PublicadorTiempoReal publicador;

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired MesaRepository mesas;
	@Autowired ComandaRepository comandas;
	@Autowired PagoRepository pagos;
	@Autowired JornadaRepository jornadas;
	@Autowired MovimientoCajaRepository movimientos;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario admin;
	Usuario cajero;
	Usuario mesero;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = cuenta(Rol.ADMINISTRADOR);
		cajero = cuenta(Rol.CAJERO);
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

	private Comanda comanda(EstadoComanda estado, BigDecimal total) {
		Mesa mesa = new Mesa();
		mesa.setNombre("Mesa " + UUID.randomUUID().toString().substring(0, 8));
		mesa.setEstado(EstadoMesa.OCUPADA);
		mesas.save(mesa);
		Comanda c = new Comanda();
		c.setMesa(mesa);
		c.setMesero(mesero);
		c.setEstado(estado);
		c.setSubtotal(total);
		c.setTotal(total);
		return comandas.save(c);
	}

	private Comanda comandaLista() {
		return comanda(EstadoComanda.LISTA, TOTAL);
	}

	private Jornada jornadaAbierta(String saldoInicial) {
		Jornada j = new Jornada();
		j.setEstado(EstadoJornada.ABIERTA);
		j.setFechaApertura(Instant.now());
		j.setSaldoInicialEfectivo(new BigDecimal(saldoInicial));
		j.setAbiertaPor(admin);
		return jornadas.save(j);
	}

	private ResultActions pagar(String medio, Long comandaId, Usuario quien) throws Exception {
		return pagar(medio, "{\"comandaId\":" + comandaId + "}", quien);
	}

	private ResultActions pagar(String medio, String cuerpo, Usuario quien) throws Exception {
		MockHttpServletRequestBuilder peticion = post("/api/pagos/" + medio)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		if (quien != null) {
			peticion = peticion.header("Authorization", "Bearer " + jwt.generar(quien));
		}
		return mvc.perform(peticion);
	}

	private BigDecimal saldo(String campo) throws Exception {
		String json = mvc.perform(get("/api/caja/saldos").header("Authorization", "Bearer " + jwt.generar(admin)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Number n = JsonPath.read(json, "$." + campo);
		return new BigDecimal(n.toString());
	}

	private List<MovimientoCaja> movimientosDe(Long comandaId) {
		return movimientos.findAll().stream()
				.filter(m -> m.getPago() != null && m.getPago().getComanda().getId().equals(comandaId)).toList();
	}

	private Long idDelPago(ResultActions respuesta) throws Exception {
		Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	private void assertSinPago(Comanda c, EstadoComanda estadoEsperado) {
		assertThat(pagos.findByComandaId(c.getId())).isEmpty();
		assertThat(movimientosDe(c.getId())).isEmpty();
		assertThat(comandas.findById(c.getId()).orElseThrow().getEstado()).isEqualTo(estadoEsperado);
	}

	// ------------------------------------------------------------------ registrar un pago

	@Test
	void elCajeroConfirmaUnPagoEnEfectivoConTodosSusDatos() throws Exception {
		Comanda c = comandaLista();
		Instant antes = Instant.now().minusSeconds(5);

		ResultActions r = pagar("efectivo", c.getId(), cajero)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comandaId").value(c.getId()))
				.andExpect(jsonPath("$.medio").value("EFECTIVO"))
				.andExpect(jsonPath("$.valor").value(12500.0))
				.andExpect(jsonPath("$.confirmadoPorId").value(cajero.getId()))
				.andExpect(jsonPath("$.comandaEstado").value("CERRADA"))
				.andExpect(jsonPath("$.fecha").exists())
				.andExpect(jsonPath("$.identificadorOperacion").exists());

		Pago pago = pagos.findById(idDelPago(r)).orElseThrow();
		assertThat(pago.getValor()).isEqualByComparingTo(TOTAL);
		assertThat(pago.getMedio()).isEqualTo(MedioPago.EFECTIVO);
		assertThat(pago.getConfirmadoPor().getId()).isEqualTo(cajero.getId());
		assertThat(pago.getFecha()).isAfter(antes);
	}

	@Test
	void alPagarLaComandaQuedaCerradaLaMesaLibreYSePublicaElCambio() throws Exception {
		Comanda c = comandaLista();

		pagar("efectivo", c.getId(), cajero).andExpect(status().isCreated());

		Comanda despues = comandas.findById(c.getId()).orElseThrow();
		assertThat(despues.getEstado()).isEqualTo(EstadoComanda.CERRADA);
		assertThat(despues.getMesa().getEstado()).isEqualTo(EstadoMesa.DISPONIBLE);
		verify(publicador).publicarComanda(eq(TipoEvento.COMANDA_ESTADO_CAMBIADO), any());
	}

	@Test
	void elPagoQuedaEnElLibroDeCajaYEnAuditoria() throws Exception {
		Comanda c = comandaLista();

		Long id = idDelPago(pagar("transferencia", c.getId(), cajero).andExpect(status().isCreated()));

		List<MovimientoCaja> libro = movimientosDe(c.getId());
		assertThat(libro).hasSize(1);
		assertThat(libro.get(0).getTipo()).isEqualTo(TipoMovimientoCaja.PAGO);
		assertThat(libro.get(0).getMedio()).isEqualTo(MedioPago.TRANSFERENCIA);
		assertThat(libro.get(0).getValor()).isEqualByComparingTo(TOTAL);
		assertThat(libro.get(0).getRegistradoPor().getId()).isEqualTo(cajero.getId());
		List<RegistroAuditoria> registros = auditoria.findAll().stream()
				.filter(r -> r.getTipoOperacion().equals("PAGO_REGISTRADO") && id.equals(r.getEntidadId())).toList();
		assertThat(registros).hasSize(1);
		assertThat(registros.get(0).getUsuario().getId()).isEqualTo(cajero.getId());
	}

	@Test
	void elValorLoCalculaElServidorYNoElCliente() throws Exception {
		Comanda c = comandaLista();

		pagar("efectivo", "{\"comandaId\":" + c.getId() + ",\"valor\":1}", cajero)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.valor").value(12500.0));
	}

	// ------------------------------------------------------------------ quién puede confirmar qué (RF-29, RF-30)

	@Test
	void elAdministradorConfirmaAmbosMedios() throws Exception {
		pagar("efectivo", comandaLista().getId(), admin).andExpect(status().isCreated());
		pagar("transferencia", comandaLista().getId(), admin).andExpect(status().isCreated());
	}

	@Test
	void elCajeroConfirmaTransferencias() throws Exception {
		pagar("transferencia", comandaLista().getId(), cajero).andExpect(status().isCreated());
	}

	@Test
	void elMeseroSoloConfirmaTransferencias() throws Exception {
		Comanda porTransferencia = comandaLista();
		Comanda enEfectivo = comandaLista();

		pagar("transferencia", porTransferencia.getId(), mesero)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.confirmadoPorId").value(mesero.getId()));
		pagar("efectivo", enEfectivo.getId(), mesero)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));

		assertSinPago(enEfectivo, EstadoComanda.LISTA);
	}

	@Test
	void cocineroYClienteNoConfirmanNingunPago() throws Exception {
		for (Rol rol : List.of(Rol.COCINERO, Rol.CLIENTE)) {
			Usuario otro = cuenta(rol);
			Comanda c = comandaLista();
			pagar("efectivo", c.getId(), otro).andExpect(status().isForbidden());
			pagar("transferencia", c.getId(), otro).andExpect(status().isForbidden());
			assertSinPago(c, EstadoComanda.LISTA);
		}
	}

	@Test
	void sinSesionResponde401() throws Exception {
		Comanda c = comandaLista();
		pagar("efectivo", c.getId(), null).andExpect(status().isUnauthorized());
		pagar("transferencia", c.getId(), null).andExpect(status().isUnauthorized());
		assertSinPago(c, EstadoComanda.LISTA);
	}

	// ------------------------------------------------------------------ una comanda, un solo pago (RF-28, RF-43)

	@Test
	void unaComandaYaPagadaNoAdmiteUnSegundoPagoDelMismoMedio() throws Exception {
		Comanda c = comandaLista();
		pagar("transferencia", c.getId(), mesero).andExpect(status().isCreated());

		pagar("transferencia", c.getId(), mesero)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("Esta comanda ya fue pagada."));

		assertThat(pagos.findAll().stream().filter(p -> p.getComanda().getId().equals(c.getId()))).hasSize(1);
		assertThat(movimientosDe(c.getId())).hasSize(1);
	}

	@Test
	void unaComandaPagadaNoSePuedePagarPorOtroMedioNiPorOtroUsuario() throws Exception {
		Comanda c = comandaLista();
		pagar("efectivo", c.getId(), cajero).andExpect(status().isCreated());

		pagar("transferencia", c.getId(), admin).andExpect(status().isConflict());
		pagar("efectivo", c.getId(), admin).andExpect(status().isConflict());

		assertThat(movimientosDe(c.getId())).hasSize(1);
	}

	@Test
	void elSegundoIntentoNoCambiaLosSaldos() throws Exception {
		Comanda c = comandaLista();
		pagar("efectivo", c.getId(), cajero).andExpect(status().isCreated());
		BigDecimal efectivo = saldo("efectivo");
		BigDecimal transferencias = saldo("transferencias");

		pagar("efectivo", c.getId(), cajero).andExpect(status().isConflict());
		pagar("transferencia", c.getId(), cajero).andExpect(status().isConflict());

		assertThat(saldo("efectivo")).isEqualByComparingTo(efectivo);
		assertThat(saldo("transferencias")).isEqualByComparingTo(transferencias);
	}

	@Test
	void reenviarElMismoIdentificadorDevuelveElPagoYaRegistradoSinSumarOtraVez() throws Exception {
		Comanda c = comandaLista();
		UUID operacion = UUID.randomUUID();
		String cuerpo = "{\"comandaId\":" + c.getId() + ",\"identificadorOperacion\":\"" + operacion + "\"}";

		Long primero = idDelPago(pagar("transferencia", cuerpo, mesero).andExpect(status().isCreated())
				.andExpect(jsonPath("$.identificadorOperacion").value(operacion.toString())));
		BigDecimal transferencias = saldo("transferencias");
		Long repetido = idDelPago(pagar("transferencia", cuerpo, mesero).andExpect(status().isOk()));

		assertThat(repetido).isEqualTo(primero);
		assertThat(movimientosDe(c.getId())).hasSize(1);
		assertThat(saldo("transferencias")).isEqualByComparingTo(transferencias);
	}

	@Test
	void elMismoIdentificadorNoSirveParaPagarOtraComanda() throws Exception {
		UUID operacion = UUID.randomUUID();
		pagar("transferencia", "{\"comandaId\":" + comandaLista().getId() + ",\"identificadorOperacion\":\""
				+ operacion + "\"}", mesero).andExpect(status().isCreated());
		Comanda otra = comandaLista();

		pagar("transferencia", "{\"comandaId\":" + otra.getId() + ",\"identificadorOperacion\":\"" + operacion
				+ "\"}", mesero).andExpect(status().isConflict());

		assertSinPago(otra, EstadoComanda.LISTA);
	}

	// ------------------------------------------------------------------ estado de la comanda

	@Test
	void soloSePagaUnaComandaListaYLasDemasSeRechazan() throws Exception {
		for (EstadoComanda estado : List.of(EstadoComanda.PENDIENTE, EstadoComanda.ENVIADA,
				EstadoComanda.EN_PREPARACION)) {
			Comanda c = comanda(estado, TOTAL);
			pagar("efectivo", c.getId(), cajero)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.mensaje").value("La comanda aún no está lista para pagar."));
			assertSinPago(c, estado);
		}
	}

	@Test
	void unaComandaCerradaSinPagoEsCanceladaYNoAdmitePagos() throws Exception {
		Comanda cancelada = comanda(EstadoComanda.CERRADA, TOTAL);

		pagar("efectivo", cancelada.getId(), cajero)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("La comanda fue cancelada y no admite pagos."));

		assertSinPago(cancelada, EstadoComanda.CERRADA);
	}

	@Test
	void unaComandaSinValorPorCobrarSeRechaza() throws Exception {
		Comanda gratis = comanda(EstadoComanda.LISTA, BigDecimal.ZERO);

		pagar("efectivo", gratis.getId(), cajero).andExpect(status().isConflict());

		assertSinPago(gratis, EstadoComanda.LISTA);
	}

	@Test
	void comandaInexistenteResponde404() throws Exception {
		pagar("efectivo", 99999999L, cajero).andExpect(status().isNotFound());
	}

	@Test
	void solicitudesInvalidasResponden400() throws Exception {
		pagar("efectivo", "{}", cajero)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.comandaId").exists());
		pagar("efectivo", "{esto no es json", cajero).andExpect(status().isBadRequest());
		pagar("efectivo", "{\"comandaId\":\"abc\"}", cajero).andExpect(status().isBadRequest());
		pagar("efectivo", "{\"comandaId\":1,\"identificadorOperacion\":\"no-es-uuid\"}", cajero)
				.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------ saldos separados (RF-31)

	@Test
	void unPagoEnEfectivoSoloSumaAlSaldoEnEfectivo() throws Exception {
		jornadaAbierta("50000.00");
		BigDecimal efectivoAntes = saldo("efectivo");
		BigDecimal transferenciasAntes = saldo("transferencias");
		assertThat(efectivoAntes).isEqualByComparingTo("50000.00");

		pagar("efectivo", comandaLista().getId(), cajero).andExpect(status().isCreated());

		assertThat(saldo("efectivo")).isEqualByComparingTo(efectivoAntes.add(TOTAL));
		assertThat(saldo("transferencias")).isEqualByComparingTo(transferenciasAntes);
	}

	@Test
	void unaTransferenciaConfirmadaSoloSumaAlSaldoDeTransferencias() throws Exception {
		jornadaAbierta("50000.00");
		BigDecimal efectivoAntes = saldo("efectivo");
		BigDecimal transferenciasAntes = saldo("transferencias");

		pagar("transferencia", comandaLista().getId(), mesero).andExpect(status().isCreated());

		assertThat(saldo("transferencias")).isEqualByComparingTo(transferenciasAntes.add(TOTAL));
		assertThat(saldo("efectivo")).isEqualByComparingTo(efectivoAntes);
	}

	@Test
	void variosPagosMezcladosCadaUnoVaASuSaldo() throws Exception {
		jornadaAbierta("0.00");
		BigDecimal transferenciasAntes = saldo("transferencias");

		pagar("efectivo", comandaLista().getId(), cajero).andExpect(status().isCreated());
		pagar("efectivo", comandaLista().getId(), admin).andExpect(status().isCreated());
		pagar("transferencia", comandaLista().getId(), mesero).andExpect(status().isCreated());
		pagar("transferencia", comandaLista().getId(), cajero).andExpect(status().isCreated());
		pagar("transferencia", comandaLista().getId(), admin).andExpect(status().isCreated());

		assertThat(saldo("efectivo")).isEqualByComparingTo(TOTAL.multiply(BigDecimal.valueOf(2)));
		assertThat(saldo("transferencias"))
				.isEqualByComparingTo(transferenciasAntes.add(TOTAL.multiply(BigDecimal.valueOf(3))));
	}

	@Test
	void unRetiroEnEfectivoSoloBajaElEfectivo() throws Exception {
		Jornada jornada = jornadaAbierta("10000.00");
		pagar("efectivo", comandaLista().getId(), cajero).andExpect(status().isCreated());
		pagar("transferencia", comandaLista().getId(), cajero).andExpect(status().isCreated());
		BigDecimal efectivoAntes = saldo("efectivo");
		BigDecimal transferenciasAntes = saldo("transferencias");

		MovimientoCaja retiro = new MovimientoCaja();
		retiro.setJornada(jornada);
		retiro.setTipo(TipoMovimientoCaja.RETIRO);
		retiro.setMedio(MedioPago.EFECTIVO);
		retiro.setValor(new BigDecimal("4000.00"));
		retiro.setConcepto("Retiro de prueba");
		retiro.setRegistradoPor(admin);
		movimientos.save(retiro);

		assertThat(saldo("efectivo")).isEqualByComparingTo(efectivoAntes.subtract(new BigDecimal("4000.00")));
		assertThat(saldo("transferencias")).isEqualByComparingTo(transferenciasAntes);
	}

	@Test
	void cadaPagoDejaMovimientosSoloDeSuMedio() throws Exception {
		Comanda enEfectivo = comandaLista();
		Comanda porTransferencia = comandaLista();

		pagar("efectivo", enEfectivo.getId(), cajero).andExpect(status().isCreated());
		pagar("transferencia", porTransferencia.getId(), mesero).andExpect(status().isCreated());

		assertThat(movimientosDe(enEfectivo.getId())).extracting(MovimientoCaja::getMedio)
				.containsExactly(MedioPago.EFECTIVO);
		assertThat(movimientosDe(porTransferencia.getId())).extracting(MovimientoCaja::getMedio)
				.containsExactly(MedioPago.TRANSFERENCIA);
	}

	@Test
	void unPagoRechazadoNoMueveNingunSaldo() throws Exception {
		jornadaAbierta("0.00");
		Comanda c = comandaLista();
		BigDecimal efectivo = saldo("efectivo");
		BigDecimal transferencias = saldo("transferencias");

		pagar("efectivo", c.getId(), mesero).andExpect(status().isForbidden());
		pagar("efectivo", comanda(EstadoComanda.ENVIADA, TOTAL).getId(), cajero).andExpect(status().isConflict());

		assertThat(saldo("efectivo")).isEqualByComparingTo(efectivo);
		assertThat(saldo("transferencias")).isEqualByComparingTo(transferencias);
	}

	@Test
	void soloElAdministradorConsultaLosSaldos() throws Exception {
		for (Rol rol : List.of(Rol.CAJERO, Rol.MESERO, Rol.COCINERO, Rol.CLIENTE)) {
			mvc.perform(get("/api/caja/saldos").header("Authorization", "Bearer " + jwt.generar(cuenta(rol))))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/caja/saldos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/caja/saldos").header("Authorization", "Bearer " + jwt.generar(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.efectivo").exists())
				.andExpect(jsonPath("$.transferencias").exists());
	}

	// ------------------------------------------------------------------ jornada

	@Test
	void elPagoSeRegistraEnLaJornadaAbierta() throws Exception {
		Jornada jornada = jornadaAbierta("0.00");

		Long id = idDelPago(pagar("efectivo", comandaLista().getId(), cajero).andExpect(status().isCreated())
				.andExpect(jsonPath("$.jornadaId").value(jornada.getId())));

		assertThat(pagos.findById(id).orElseThrow().getJornada().getId()).isEqualTo(jornada.getId());
	}

	@Test
	void sinJornadaAbiertaSeAbreUnaConSaldoInicialCero() throws Exception {
		jornadas.findAll().forEach(j -> j.setEstado(EstadoJornada.CERRADA));
		jornadas.flush();

		Long id = idDelPago(pagar("efectivo", comandaLista().getId(), cajero).andExpect(status().isCreated()));

		Jornada jornada = pagos.findById(id).orElseThrow().getJornada();
		assertThat(jornada.getEstado()).isEqualTo(EstadoJornada.ABIERTA);
		assertThat(jornada.getSaldoInicialEfectivo()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(auditoria.findAll().stream()
				.filter(r -> r.getTipoOperacion().equals("JORNADA_ABIERTA_AUTOMATICA")
						&& jornada.getId().equals(r.getEntidadId())))
				.hasSize(1);
	}

}
