package com.appresso.backend.fidelizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/**
 * Trazabilidad del consentimiento y supresión de datos personales (Ley 1581, SCRUM-101).
 * Cada prueba se revierte al terminar ({@code @Transactional}).
 */
@SpringBootTest
@Transactional
class ClienteSupresionTest {

	@Autowired WebApplicationContext context;
	@Autowired ClienteRepository clientes;
	@Autowired UsuarioRepository usuarios;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired JwtService jwt;
	@Autowired EntityManager em;

	MockMvc mvc;
	Usuario admin;
	String tokenAdmin;
	String tokenMesero;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = usuario(Rol.ADMINISTRADOR);
		tokenAdmin = jwt.generar(admin);
		tokenMesero = jwt.generar(usuario(Rol.MESERO));
	}

	// ---------------------------------------------------------------- trazabilidad del consentimiento

	@Test
	void elConsentimientoQuedaEnAuditoriaConLaVersionDelTextoYSinDatosPersonales() throws Exception {
		Long id = registrarPorApi("Ana", "Gomez", documentoUnico());

		RegistroAuditoria registro = auditoriaDe(id, ClienteService.OPERACION_CONSENTIMIENTO).get(0);

		assertThat(registro.getDetalle())
				.contains("Ley 1581")
				.contains(ClienteService.VERSION_TEXTO_AUTORIZACION)
				.doesNotContain("Ana")
				.doesNotContain("Gomez");
		assertThat(registro.getUsuario()).isNotNull();
		assertThat(registro.getFecha()).isNotNull();
	}

	@Test
	void elAdministradorConsultaElHistorialDeConsentimiento() throws Exception {
		Long id = registrarPorApi("Luis", "Rojas", documentoUnico());

		mvc.perform(get("/api/clientes/" + id + "/consentimiento").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].operacion").value(ClienteService.OPERACION_CONSENTIMIENTO))
				.andExpect(jsonPath("$[0].fecha").isNotEmpty())
				.andExpect(jsonPath("$[0].usuarioId").isNotEmpty());
	}

	@Test
	void elHistorialMuestraElConsentimientoYLaSupresionAunqueElClienteYaNoExista() throws Exception {
		Long id = registrarPorApi("Sofia", "Diaz", documentoUnico());
		solicitarEliminacion(id, tokenAdmin, "Solicitud por correo");

		mvc.perform(get("/api/clientes/" + id + "/consentimiento").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].operacion").value(ClienteService.OPERACION_CONSENTIMIENTO))
				.andExpect(jsonPath("$[1].operacion").value(ClienteService.OPERACION_SUPRESION));
	}

	@Test
	void elMeseroNoVeElHistorialDeConsentimiento() throws Exception {
		Long id = registrarPorApi("Eva", "Mora", documentoUnico());

		mvc.perform(get("/api/clientes/" + id + "/consentimiento").header("Authorization", "Bearer " + tokenMesero))
				.andExpect(status().isForbidden());
	}

	@Test
	void historialDeUnClienteQueNuncaExistioResponde404() throws Exception {
		mvc.perform(get("/api/clientes/999999/consentimiento").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- supresión

	@Test
	void sinHistorialComercialElClienteYSusNotificacionesSeEliminan() throws Exception {
		Cliente c = cliente();
		notificacionPara(c);

		solicitarEliminacion(c.getId(), tokenAdmin, null);

		assertThat(clientes.findById(c.getId())).isEmpty();
		assertThat(notificacionesDe(c.getId())).isZero();
		assertThat(auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle())
				.contains("datos eliminados");
	}

	@Test
	void conComandasLosDatosSeAnonimizanIncluidoElDocumentoYLaComandaSeConserva() throws Exception {
		Cliente c = cliente();
		Comanda comanda = comandaDe(c);

		solicitarEliminacion(c.getId(), tokenAdmin, "El titular lo pidió en caja");

		Cliente anonimo = clientes.findById(c.getId()).orElseThrow();
		assertThat(anonimo.getNombre()).isEqualTo(ClienteService.NOMBRE_SUPRIMIDO);
		assertThat(anonimo.getApellido()).isEqualTo(ClienteService.APELLIDO_SUPRIMIDO);
		assertThat(anonimo.getCelular()).isEqualTo(ClienteService.CELULAR_SUPRIMIDO);
		assertThat(anonimo.getDocumento()).isEqualTo(ClienteService.PREFIJO_DOCUMENTO_SUPRIMIDO + c.getId());
		assertThat(anonimo.isAceptoTratamientoDatos()).isFalse();
		assertThat(em.find(Comanda.class, comanda.getId()).getCliente().getId()).isEqualTo(c.getId());
		assertThat(auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle())
				.contains("anonimizados")
				.contains("El titular lo pidió en caja");
	}

	@Test
	void laCuentaDeUsuarioDelClienteSeDesactivaYAnonimiza() throws Exception {
		Usuario cuenta = usuario(Rol.CLIENTE);
		Cliente c = cliente();
		c.setUsuario(cuenta);
		clientes.save(c);

		solicitarEliminacion(c.getId(), tokenAdmin, null);

		Usuario despues = usuarios.findById(cuenta.getId()).orElseThrow();
		assertThat(despues.isActivo()).isFalse();
		assertThat(despues.getCorreo()).isEqualTo("eliminado-" + cuenta.getId() + "@appresso.invalid");
		assertThat(despues.getNombre()).isEqualTo(ClienteService.NOMBRE_SUPRIMIDO);
	}

	@Test
	void laAuditoriaDeSupresionNoGuardaDatosPersonales() throws Exception {
		Cliente c = cliente();
		comandaDe(c); // se anonimiza: el registro del cliente se conserva

		solicitarEliminacion(c.getId(), tokenAdmin, null);

		String detalle = auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle();
		assertThat(detalle).doesNotContain("Marta").doesNotContain("Lopez").doesNotContain(c.getCelular());
	}

	@Test
	void elDeleteDelAdministradorTambienSuprime() throws Exception {
		Cliente c = cliente();

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.delete("/api/clientes/" + c.getId()).header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isNoContent());

		assertThat(clientes.findById(c.getId())).isEmpty();
	}

	@Test
	void unMotivoDeMasDe500CaracteresSeRechaza() throws Exception {
		Cliente c = cliente();

		mvc.perform(post("/api/clientes/" + c.getId() + "/solicitar-eliminacion")
						.header("Authorization", "Bearer " + tokenAdmin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"motivo\": \"" + "x".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());

		assertThat(clientes.findById(c.getId())).isPresent();
	}

	@Test
	void suprimirUnClienteQueNoExisteResponde404() throws Exception {
		mvc.perform(post("/api/clientes/999999/solicitar-eliminacion").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- ayudas

	private Long registrarPorApi(String nombre, String apellido, String documento) throws Exception {
		String cuerpo = """
				{"nombre": "%s", "apellido": "%s", "documento": "%s", "celular": "3001112233",
				 "aceptoTratamientoDatos": true}
				""".formatted(nombre, apellido, documento);
		String respuesta = mvc.perform(post("/api/clientes")
						.header("Authorization", "Bearer " + tokenMesero)
						.contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
	}

	private void solicitarEliminacion(Long id, String token, String motivo) throws Exception {
		var peticion = post("/api/clientes/" + id + "/solicitar-eliminacion").header("Authorization", "Bearer " + token);
		if (motivo != null) {
			peticion.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"" + motivo + "\"}");
		}
		mvc.perform(peticion).andExpect(status().isNoContent());
	}

	private List<RegistroAuditoria> auditoriaDe(Long clienteId, String operacion) {
		return auditoria.findByEntidadAndEntidadIdOrderByFechaAscIdAsc(ClienteService.ENTIDAD, clienteId).stream()
				.filter(r -> operacion.equals(r.getTipoOperacion()))
				.toList();
	}

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre("Prueba " + rol);
		u.setCorreo(rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash("bcrypt-hash");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.save(u);
	}

	private Cliente cliente() {
		Cliente c = new Cliente();
		c.setNombre("Marta");
		c.setApellido("Lopez");
		c.setDocumento(documentoUnico());
		c.setCelular("3209998877");
		c.setAceptoTratamientoDatos(true);
		return clientes.save(c);
	}

	private static String documentoUnico() {
		return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
	}

	private void notificacionPara(Cliente c) {
		Notificacion n = new Notificacion();
		n.setCliente(c);
		n.setTipo(TipoNotificacion.values()[0]);
		n.setTitulo("Promo");
		n.setMensaje("2x1 en capuchinos");
		em.persist(n);
		em.flush();
	}

	private long notificacionesDe(Long clienteId) {
		return em.createQuery("select count(n) from Notificacion n where n.cliente.id = :id", Long.class)
				.setParameter("id", clienteId).getSingleResult();
	}

	private Comanda comandaDe(Cliente c) {
		Mesa mesa = new Mesa();
		mesa.setNombre("M-" + UUID.randomUUID().toString().substring(0, 8));
		em.persist(mesa);
		Comanda comanda = new Comanda();
		comanda.setMesa(mesa);
		comanda.setMesero(usuario(Rol.MESERO));
		comanda.setCliente(c);
		em.persist(comanda);
		em.flush();
		return comanda;
	}

}
