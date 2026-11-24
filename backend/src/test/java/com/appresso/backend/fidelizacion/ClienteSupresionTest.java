package com.appresso.backend.fidelizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.CodigoRecuperacion;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.comandas.Comanda;
import com.appresso.backend.comandas.Mesa;
import com.appresso.backend.usuarios.DispositivoPush;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

import jakarta.persistence.EntityManager;

/**
 * Trazabilidad del consentimiento y eliminación de los datos por el propio cliente (Ley 1581,
 * SCRUM-101), de punta a punta con la seguridad activa. Cada prueba se revierte al terminar.
 */
@SpringBootTest
@Transactional
class ClienteSupresionTest {

	private static final String CLAVE = "Clave-Cliente-1";

	@Autowired WebApplicationContext context;
	@Autowired ClienteRepository clientes;
	@Autowired UsuarioRepository usuarios;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;
	@Autowired EntityManager em;

	MockMvc mvc;
	String tokenAdmin;
	String tokenMesero;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		tokenAdmin = jwt.generar(usuario(Rol.ADMINISTRADOR));
		tokenMesero = jwt.generar(usuario(Rol.MESERO));
	}

	// ---------------------------------------------------------------- trazabilidad del consentimiento

	@Test
	void elConsentimientoQuedaEnAuditoriaConLaVersionDelTextoYSinDatosPersonales() throws Exception {
		Cliente c = registrarPorApi();

		RegistroAuditoria registro = auditoriaDe(c.getId(), ClienteService.OPERACION_CONSENTIMIENTO).get(0);

		assertThat(registro.getDetalle())
				.contains("Ley 1581")
				.contains(ClienteService.VERSION_TEXTO_AUTORIZACION)
				.doesNotContain("Marta", "Lopez", c.getDocumento(), c.getCelular(), c.getUsuario().getCorreo());
		assertThat(registro.getUsuario().getId()).isEqualTo(c.getUsuario().getId());
		assertThat(registro.getFecha()).isNotNull();
	}

	@Test
	void elAdministradorConsultaElHistorialDeConsentimiento() throws Exception {
		Cliente c = registrarPorApi();

		historial(c.getId(), tokenAdmin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].operacion").value(ClienteService.OPERACION_CONSENTIMIENTO))
				.andExpect(jsonPath("$[0].fecha").isNotEmpty())
				.andExpect(jsonPath("$[0].usuarioId").value(c.getUsuario().getId()));
	}

	@Test
	void elHistorialMuestraElConsentimientoYLaSupresionAunqueElClienteYaNoExista() throws Exception {
		Cliente c = registrarPorApi();
		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());
		assertThat(clientes.findById(c.getId())).isEmpty();

		historial(c.getId(), tokenAdmin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].operacion").value(ClienteService.OPERACION_CONSENTIMIENTO))
				.andExpect(jsonPath("$[1].operacion").value(ClienteService.OPERACION_SUPRESION));
	}

	@Test
	void soloElAdministradorVeElHistorialDeConsentimiento() throws Exception {
		Cliente c = cliente();

		historial(c.getId(), tokenMesero).andExpect(status().isForbidden());
		historial(c.getId(), tokenDe(c)).andExpect(status().isForbidden());
	}

	@Test
	void historialDeUnClienteQueNuncaExistioResponde404() throws Exception {
		historial(999999L, tokenAdmin).andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- eliminación por el propio cliente

	@Test
	void sinHistorialComercialSeBorranElClienteSusNotificacionesSusDispositivosYSusCodigos() throws Exception {
		Cliente c = cliente();
		Long cuentaId = c.getUsuario().getId();
		notificacionPara(c);
		dispositivoDe(c.getUsuario());
		codigoDe(c.getUsuario());

		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		em.flush();
		em.clear();
		assertThat(clientes.findById(c.getId())).isEmpty();
		assertThat(contar("Notificacion n where n.cliente.id", c.getId())).isZero();
		assertThat(contar("DispositivoPush n where n.usuario.id", cuentaId)).isZero();
		assertThat(contar("CodigoRecuperacion n where n.usuario.id", cuentaId)).isZero();
		assertThat(auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle())
				.contains("datos eliminados");
	}

	@Test
	void conComandasLosDatosSeAnonimizanIncluidoElDocumentoYLaComandaSeConserva() throws Exception {
		Cliente c = cliente();
		Comanda comanda = comandaDe(c);

		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		em.flush();
		em.clear();
		Cliente anonimo = clientes.findById(c.getId()).orElseThrow();
		assertThat(anonimo.getNombre()).isEqualTo(ClienteService.NOMBRE_SUPRIMIDO);
		assertThat(anonimo.getApellido()).isEqualTo(ClienteService.APELLIDO_SUPRIMIDO);
		assertThat(anonimo.getCelular()).isEqualTo(ClienteService.CELULAR_SUPRIMIDO);
		assertThat(anonimo.getDocumento()).isEqualTo(ClienteService.PREFIJO_DOCUMENTO_SUPRIMIDO + c.getId());
		assertThat(anonimo.isAceptoTratamientoDatos()).isFalse();
		assertThat(anonimo.getUsuario()).isNull();
		assertThat(em.find(Comanda.class, comanda.getId()).getCliente().getId()).isEqualTo(c.getId());
		assertThat(auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle())
				.contains("anonimizados");
	}

	@Test
	void laCuentaSeDesactivaYAnonimizaYNoQuedaNiElHashDeLaContrasena() throws Exception {
		Cliente c = cliente();
		Long cuentaId = c.getUsuario().getId();
		String hashAnterior = c.getUsuario().getContrasenaHash();

		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		em.flush();
		em.clear();
		Usuario despues = usuarios.findById(cuentaId).orElseThrow();
		assertThat(despues.isActivo()).isFalse();
		assertThat(despues.getCorreo()).isEqualTo("eliminado-" + cuentaId + "@appresso.invalid");
		assertThat(despues.getNombre()).isEqualTo(ClienteService.NOMBRE_SUPRIMIDO);
		assertThat(despues.getContrasenaHash()).startsWith("$2").isNotEqualTo(hashAnterior);
		assertThat(codificador.matches(CLAVE, despues.getContrasenaHash())).isFalse();
	}

	@Test
	void despuesDeEliminarElTokenYaNoSirveYNoSePuedeIniciarSesion() throws Exception {
		Cliente c = cliente();
		String token = tokenDe(c);
		String documento = c.getDocumento();
		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		mvc.perform(get("/api/clientes/yo").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"identificador\":\"" + documento + "\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conElMismoDocumentoYCorreoSePuedeVolverARegistrar() throws Exception {
		Cliente c = cliente();
		comandaDe(c); // aunque el registro anterior se conserve anónimo
		String documento = c.getDocumento();
		String correo = c.getUsuario().getCorreo();
		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		registrar(documento, correo).andExpect(status().isCreated());
	}

	@Test
	void laAuditoriaDeSupresionNoGuardaDatosPersonales() throws Exception {
		Cliente c = cliente();
		String documento = c.getDocumento();
		String correo = c.getUsuario().getCorreo();

		eliminarMisDatos(c, CLAVE).andExpect(status().isNoContent());

		assertThat(auditoriaDe(c.getId(), ClienteService.OPERACION_SUPRESION).get(0).getDetalle())
				.doesNotContain("Marta", "Lopez", documento, correo, "3209998877");
	}

	@Test
	void conLaContrasenaEquivocadaNoSeEliminaNadaYLaSesionSigueSirviendo() throws Exception {
		Cliente c = cliente();

		eliminarMisDatos(c, "otra-clave-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La contraseña no es correcta."))
				.andExpect(jsonPath("$.campos.contrasena").value("La contraseña no es correcta."));

		assertThat(clientes.findById(c.getId()).orElseThrow().getNombre()).isEqualTo("Marta");
		mvc.perform(get("/api/clientes/yo").header("Authorization", "Bearer " + tokenDe(c)))
				.andExpect(status().isOk());
	}

	@Test
	void sinContrasenaSePideYNoSeEliminaNada() throws Exception {
		Cliente c = cliente();

		eliminarMisDatos(c, "  ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Escribe tu contraseña para confirmar."));
		mvc.perform(post("/api/clientes/yo/eliminacion").header("Authorization", "Bearer " + tokenDe(c)))
				.andExpect(status().isBadRequest());

		assertThat(clientes.findById(c.getId())).isPresent();
	}

	@Test
	void elPersonalNoPuedeEliminarClientes() throws Exception {
		Cliente c = cliente();

		for (String token : List.of(tokenAdmin, tokenMesero)) {
			mvc.perform(delete("/api/clientes/" + c.getId()).header("Authorization", "Bearer " + token))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/clientes/" + c.getId() + "/eliminacion").header("Authorization", "Bearer " + token))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/clientes/yo/eliminacion").header("Authorization", "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON).content("{\"contrasena\":\"x\"}"))
					.andExpect(status().isForbidden());
		}

		assertThat(clientes.findById(c.getId())).isPresent();
	}

	// ---------------------------------------------------------------- ayudas

	private ResultActions registrar(String documento, String correo) throws Exception {
		return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre": "Marta", "apellido": "Lopez", "documento": "%s", "celular": "3209998877",
				 "correo": "%s", "contrasena": "%s", "aceptoTratamientoDatos": true}
				""".formatted(documento, correo, CLAVE)));
	}

	private Cliente registrarPorApi() throws Exception {
		String documento = documentoUnico();
		registrar(documento, UUID.randomUUID() + "@correo.test").andExpect(status().isCreated());
		return clientes.findByDocumento(documento).orElseThrow();
	}

	private ResultActions eliminarMisDatos(Cliente c, String contrasena) throws Exception {
		return mvc.perform(post("/api/clientes/yo/eliminacion").header("Authorization", "Bearer " + tokenDe(c))
				.contentType(MediaType.APPLICATION_JSON).content("{\"contrasena\":\"" + contrasena + "\"}"));
	}

	private ResultActions historial(Long clienteId, String token) throws Exception {
		return mvc.perform(get("/api/clientes/" + clienteId + "/consentimiento").header("Authorization", "Bearer " + token));
	}

	private String tokenDe(Cliente c) {
		return jwt.generar(c.getUsuario());
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
		u.setContrasenaHash(codificador.encode(CLAVE));
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private Cliente cliente() {
		Cliente c = new Cliente();
		c.setNombre("Marta");
		c.setApellido("Lopez");
		c.setDocumento(documentoUnico());
		c.setCelular("3209998877");
		c.setAceptoTratamientoDatos(true);
		c.setUsuario(usuario(Rol.CLIENTE));
		return clientes.saveAndFlush(c);
	}

	private static String documentoUnico() {
		return String.valueOf(1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L));
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

	private void dispositivoDe(Usuario cuenta) {
		DispositivoPush d = new DispositivoPush();
		d.setUsuario(cuenta);
		d.setTokenFcm(UUID.randomUUID().toString());
		em.persist(d);
		em.flush();
	}

	private void codigoDe(Usuario cuenta) {
		CodigoRecuperacion codigo = new CodigoRecuperacion();
		codigo.setUsuario(cuenta);
		codigo.setCodigoHash("hash");
		codigo.setFechaCreacion(Instant.now());
		codigo.setExpira(Instant.now().plusSeconds(900));
		em.persist(codigo);
		em.flush();
	}

	private long contar(String desde, Long id) {
		return em.createQuery("select count(n) from " + desde + " = :id", Long.class)
				.setParameter("id", id).getSingleResult();
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
