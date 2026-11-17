package com.appresso.backend.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;

/**
 * Pruebas de punta a punta de la administración de usuarios (2.2.1): creación, edición,
 * desactivación y acceso no autorizado (401 sin sesión, 403 para roles que no son administrador).
 *
 * <p>Cada prueba corre dentro de una transacción que se deshace al terminar, así que no deja
 * cuentas ni registros de auditoría (esos registros no se pueden borrar).</p>
 */
@SpringBootTest
@Transactional
class UsuarioControllerTest {

	private static final String CLAVE = "Clave-Segura-1";

	@Autowired WebApplicationContext context;
	@Autowired UsuarioRepository usuarios;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;
	@Autowired UsuarioService servicio;

	MockMvc mvc;
	Usuario admin;

	@BeforeEach
	void preparar() {
		// Con la cadena de seguridad real (2.3.1), igual que en producción.
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		admin = crearCuenta(Rol.ADMINISTRADOR, true);
	}

	// ------------------------------------------------------------------ utilidades

	private Usuario crearCuenta(Rol rol, boolean activo) {
		Usuario u = new Usuario();
		u.setNombre("Cuenta " + rol);
		u.setCorreo(rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash(codificador.encode(CLAVE));
		u.setRol(rol);
		u.setActivo(activo);
		return usuarios.save(u);
	}

	private MockHttpServletRequestBuilder conSesion(MockHttpServletRequestBuilder peticion, Usuario quien) {
		return peticion.header("Authorization", "Bearer " + jwt.generar(quien));
	}

	private ResultActions enviar(MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
		return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
	}

	private static String cuerpoCrear(String nombre, String correo, String contrasena, String rol) {
		return """
				{"nombre":%s,"correo":%s,"contrasena":%s,"rol":%s}
				""".formatted(texto(nombre), texto(correo), texto(contrasena), texto(rol));
	}

	private static String cuerpoEditar(String nombre, String correo, String contrasena, String rol) {
		return cuerpoCrear(nombre, correo, contrasena, rol);
	}

	/** Texto JSON entre comillas, o {@code null} si no hay valor. */
	private static String texto(String valor) {
		return valor == null ? "null" : "\"" + valor + "\"";
	}

	private String correoNuevo() {
		return "nuevo." + UUID.randomUUID() + "@appresso.test";
	}

	// ------------------------------------------------------------------ acceso no autorizado

	/** Los seis endpoints del módulo, con un id cualquiera y un cuerpo válido. */
	private List<MockHttpServletRequestBuilder> todosLosEndpoints(Long id) {
		String cuerpo = cuerpoCrear("Alguien", correoNuevo(), CLAVE, "MESERO");
		return List.of(
				get("/api/usuarios"),
				get("/api/usuarios/" + id),
				json(post("/api/usuarios"), cuerpo),
				json(put("/api/usuarios/" + id), cuerpo),
				post("/api/usuarios/" + id + "/desactivar"),
				post("/api/usuarios/" + id + "/activar"));
	}

	@Test
	void sinSesionTodosLosEndpointsResponden401() throws Exception {
		for (MockHttpServletRequestBuilder peticion : todosLosEndpoints(admin.getId())) {
			enviar(peticion).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void conTokenInvalidoResponde401() throws Exception {
		enviar(get("/api/usuarios").header("Authorization", "Bearer esto.no.es.un.token"))
				.andExpect(status().isUnauthorized());
		enviar(get("/api/usuarios").header("Authorization", jwt.generar(admin))) // sin la palabra Bearer
				.andExpect(status().isUnauthorized());
	}

	@Test
	void losRolesQueNoSonAdministradorReciben403EnTodosLosEndpoints() throws Exception {
		Usuario objetivo = crearCuenta(Rol.MESERO, true);
		for (Rol rol : List.of(Rol.CAJERO, Rol.MESERO, Rol.COCINERO, Rol.CLIENTE)) {
			Usuario intruso = crearCuenta(rol, true);
			for (MockHttpServletRequestBuilder peticion : todosLosEndpoints(objetivo.getId())) {
				enviar(conSesion(peticion, intruso))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));
			}
		}
	}

	@Test
	void unIntrusoNoCambiaNingunDato() throws Exception {
		Usuario objetivo = crearCuenta(Rol.MESERO, true);
		Usuario intruso = crearCuenta(Rol.CAJERO, true);
		long cuentasAntes = usuarios.count();

		enviar(conSesion(json(post("/api/usuarios"), cuerpoCrear("X", correoNuevo(), CLAVE, "ADMINISTRADOR")), intruso))
				.andExpect(status().isForbidden());
		enviar(conSesion(post("/api/usuarios/" + objetivo.getId() + "/desactivar"), intruso))
				.andExpect(status().isForbidden());
		enviar(conSesion(json(put("/api/usuarios/" + objetivo.getId()),
				cuerpoEditar("Hackeado", objetivo.getCorreo(), null, "ADMINISTRADOR")), intruso))
				.andExpect(status().isForbidden());

		assertThat(usuarios.count()).isEqualTo(cuentasAntes);
		Usuario despues = usuarios.findById(objetivo.getId()).orElseThrow();
		assertThat(despues.isActivo()).isTrue();
		assertThat(despues.getRol()).isEqualTo(Rol.MESERO);
		assertThat(despues.getNombre()).isEqualTo("Cuenta MESERO");
	}

	@Test
	void unAdministradorDesactivadoPierdeElAccesoAunConSuTokenVigente() throws Exception {
		Usuario otroAdmin = crearCuenta(Rol.ADMINISTRADOR, true);
		String token = "Bearer " + jwt.generar(otroAdmin);
		enviar(get("/api/usuarios").header("Authorization", token)).andExpect(status().isOk());

		enviar(conSesion(post("/api/usuarios/" + otroAdmin.getId() + "/desactivar"), admin)).andExpect(status().isOk());

		enviar(get("/api/usuarios").header("Authorization", token)).andExpect(status().isUnauthorized());
	}

	@Test
	void elCambioDeRolSurteEfectoDeInmediato() throws Exception {
		Usuario mesero = crearCuenta(Rol.MESERO, true);
		String token = "Bearer " + jwt.generar(mesero); // el token dice MESERO
		enviar(get("/api/usuarios").header("Authorization", token)).andExpect(status().isForbidden());

		enviar(conSesion(json(put("/api/usuarios/" + mesero.getId()),
				cuerpoEditar(mesero.getNombre(), mesero.getCorreo(), null, "ADMINISTRADOR")), admin))
				.andExpect(status().isOk());
		enviar(get("/api/usuarios").header("Authorization", token)).andExpect(status().isOk());

		enviar(conSesion(json(put("/api/usuarios/" + mesero.getId()),
				cuerpoEditar(mesero.getNombre(), mesero.getCorreo(), null, "CAJERO")), admin))
				.andExpect(status().isOk());
		enviar(get("/api/usuarios").header("Authorization", token)).andExpect(status().isForbidden());
	}

	@Test
	void siempreQuedaAlMenosUnAdministradorActivo() {
		// Deja un único administrador activo (todo se deshace al terminar la prueba).
		usuarios.findAll().stream()
				.filter(u -> u.getRol() == Rol.ADMINISTRADOR && u.isActivo())
				.forEach(u -> u.setActivo(false));
		Usuario unico = crearCuenta(Rol.ADMINISTRADOR, true);
		// Simula a quien ya dejó de ser administrador por otra solicitud simultánea: pasó la
		// seguridad pero ya no cuenta como "otro administrador".
		Usuario exAdmin = crearCuenta(Rol.CAJERO, true);

		assertThatThrownBy(() -> servicio.desactivar(unico.getId(), exAdmin.getId()))
				.isInstanceOf(UsuarioService.ReglaUsuarioException.class)
				.hasMessage("Debe quedar al menos un administrador activo.");
		assertThatThrownBy(() -> servicio.editar(unico.getId(),
				new UsuarioEditarRequest(unico.getNombre(), unico.getCorreo(), null, Rol.MESERO), exAdmin.getId()))
				.isInstanceOf(UsuarioService.ReglaUsuarioException.class);

		assertThat(usuarios.findById(unico.getId()).orElseThrow().isActivo()).isTrue();
		assertThat(usuarios.findById(unico.getId()).orElseThrow().getRol()).isEqualTo(Rol.ADMINISTRADOR);
	}

	@Test
	void conOtroAdministradorActivoSiSePuedeDegradarAUno() throws Exception {
		Usuario otro = crearCuenta(Rol.ADMINISTRADOR, true);

		enviar(conSesion(json(put("/api/usuarios/" + otro.getId()),
				cuerpoEditar(otro.getNombre(), otro.getCorreo(), null, "MESERO")), admin))
				.andExpect(status().isOk());
	}

	@Test
	void editarSinCambiosNoRegistraAuditoria() throws Exception {
		Usuario mesero = crearCuenta(Rol.MESERO, true);
		long antes = auditoria.count();

		enviar(conSesion(json(put("/api/usuarios/" + mesero.getId()),
				cuerpoEditar(mesero.getNombre(), mesero.getCorreo(), null, "MESERO")), admin))
				.andExpect(status().isOk());

		assertThat(auditoria.count()).isEqualTo(antes);
	}

	// ------------------------------------------------------------------ listar y consultar

	@Test
	void elAdministradorListaLasCuentasSinExponerContrasenas() throws Exception {
		Usuario cajero = crearCuenta(Rol.CAJERO, true);
		enviar(conSesion(get("/api/usuarios"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].correo").value(hasItem(cajero.getCorreo())))
				.andExpect(content().string(not(containsString("contrasena"))))
				.andExpect(content().string(not(containsString("$2"))));
	}

	@Test
	void consultarUnaCuentaYUnaQueNoExiste() throws Exception {
		Usuario cajero = crearCuenta(Rol.CAJERO, true);
		enviar(conSesion(get("/api/usuarios/" + cajero.getId()), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(cajero.getId()))
				.andExpect(jsonPath("$.rol").value("CAJERO"))
				.andExpect(jsonPath("$.activo").value(true));
		enviar(conSesion(get("/api/usuarios/999999999"), admin))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").exists());
		enviar(conSesion(get("/api/usuarios/abc"), admin)).andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------ crear

	@Test
	void crearGuardaLaCuentaConContrasenaCifradaYLaAudita() throws Exception {
		String correo = correoNuevo();
		String cuerpo = enviar(conSesion(json(post("/api/usuarios"),
				cuerpoCrear("  Ana Mesera  ", correo.toUpperCase(), CLAVE, "MESERO")), admin))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/usuarios/")))
				.andExpect(jsonPath("$.nombre").value("Ana Mesera"))
				.andExpect(jsonPath("$.correo").value(correo.toUpperCase()))
				.andExpect(jsonPath("$.rol").value("MESERO"))
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(content().string(not(containsString(CLAVE))))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("contrasena");

		Usuario guardado = usuarios.findByCorreoIgnoreCase(correo).orElseThrow();
		assertThat(guardado.getContrasenaHash()).isNotEqualTo(CLAVE).startsWith("$2");
		assertThat(codificador.matches(CLAVE, guardado.getContrasenaHash())).isTrue();

		List<RegistroAuditoria> registros = auditoria.findAll();
		assertThat(registros).anySatisfy(r -> {
			assertThat(r.getTipoOperacion()).isEqualTo("USUARIO_CREADO");
			assertThat(r.getEntidadId()).isEqualTo(guardado.getId());
			assertThat(r.getUsuario().getId()).isEqualTo(admin.getId());
			assertThat(r.getDetalle()).doesNotContain(CLAVE);
		});
	}

	@Test
	void crearConDatosInvalidosResponde400ConUnMensajePorCampo() throws Exception {
		enviar(conSesion(json(post("/api/usuarios"), cuerpoCrear("", "no-es-correo", "corta", null)), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Revisa los datos ingresados."))
				.andExpect(jsonPath("$.campos.nombre").exists())
				.andExpect(jsonPath("$.campos.correo").exists())
				.andExpect(jsonPath("$.campos.contrasena").exists())
				.andExpect(jsonPath("$.campos.rol").exists());
	}

	@Test
	void crearConUnRolQueNoExisteOUnJsonRotoResponde400() throws Exception {
		enviar(conSesion(json(post("/api/usuarios"), cuerpoCrear("Ana", correoNuevo(), CLAVE, "SUPERUSUARIO")), admin))
				.andExpect(status().isBadRequest());
		enviar(conSesion(json(post("/api/usuarios"), "{esto no es json"), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La solicitud no tiene un formato válido."));
	}

	@Test
	void unaContrasenaDeMasDe72BytesSeRechaza() throws Exception {
		String larga = "ñ".repeat(40); // 40 caracteres, 80 bytes en UTF-8
		enviar(conSesion(json(post("/api/usuarios"), cuerpoCrear("Ana", correoNuevo(), larga, "MESERO")), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.contrasena").exists());
	}

	@Test
	void noSePermiteUnCorreoRepetidoSinImportarMayusculas() throws Exception {
		Usuario existente = crearCuenta(Rol.CAJERO, true);
		enviar(conSesion(json(post("/api/usuarios"),
				cuerpoCrear("Otra", existente.getCorreo().toUpperCase(), CLAVE, "MESERO")), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.campos.correo").exists());
	}

	// ------------------------------------------------------------------ editar

	@Test
	void editarCambiaNombreCorreoYRolYQuedaAuditado() throws Exception {
		Usuario cuenta = crearCuenta(Rol.MESERO, true);
		String correo = correoNuevo();
		enviar(conSesion(json(put("/api/usuarios/" + cuenta.getId()),
				cuerpoEditar("Nombre Nuevo", correo, null, "COCINERO")), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
				.andExpect(jsonPath("$.correo").value(correo))
				.andExpect(jsonPath("$.rol").value("COCINERO"));

		Usuario guardado = usuarios.findById(cuenta.getId()).orElseThrow();
		assertThat(guardado.getRol()).isEqualTo(Rol.COCINERO);
		assertThat(codificador.matches(CLAVE, guardado.getContrasenaHash())).isTrue(); // la contraseña no cambió
		assertThat(auditoria.findAll()).anySatisfy(r -> {
			assertThat(r.getTipoOperacion()).isEqualTo("USUARIO_EDITADO");
			assertThat(r.getEntidadId()).isEqualTo(cuenta.getId());
			assertThat(r.getDetalle()).contains("MESERO -> COCINERO");
		});
	}

	@Test
	void editarConContrasenaLaReemplazaYDesbloqueaLaCuenta() throws Exception {
		Usuario cuenta = crearCuenta(Rol.MESERO, true);
		cuenta.setIntentosFallidos(5);
		cuenta.setBloqueadoHasta(java.time.Instant.now().plusSeconds(600));

		enviar(conSesion(json(put("/api/usuarios/" + cuenta.getId()),
				cuerpoEditar(cuenta.getNombre(), cuenta.getCorreo(), "Otra-Clave-99", "MESERO")), admin))
				.andExpect(status().isOk());

		Usuario guardado = usuarios.findById(cuenta.getId()).orElseThrow();
		assertThat(codificador.matches("Otra-Clave-99", guardado.getContrasenaHash())).isTrue();
		assertThat(guardado.getIntentosFallidos()).isZero();
		assertThat(guardado.getBloqueadoHasta()).isNull();
	}

	@Test
	void editarPuedeConservarElPropioCorreoPeroNoUsarElDeOtro() throws Exception {
		Usuario a = crearCuenta(Rol.MESERO, true);
		Usuario b = crearCuenta(Rol.CAJERO, true);
		enviar(conSesion(json(put("/api/usuarios/" + a.getId()),
				cuerpoEditar("Mismo correo", a.getCorreo(), null, "MESERO")), admin))
				.andExpect(status().isOk());
		enviar(conSesion(json(put("/api/usuarios/" + a.getId()),
				cuerpoEditar("Correo ajeno", b.getCorreo(), null, "MESERO")), admin))
				.andExpect(status().isConflict());
	}

	@Test
	void editarValidaLosCamposYLaExistencia() throws Exception {
		Usuario cuenta = crearCuenta(Rol.MESERO, true);
		enviar(conSesion(json(put("/api/usuarios/" + cuenta.getId()), cuerpoEditar("", "mal", "x", null)), admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nombre").exists())
				.andExpect(jsonPath("$.campos.correo").exists())
				.andExpect(jsonPath("$.campos.contrasena").exists())
				.andExpect(jsonPath("$.campos.rol").exists());
		enviar(conSesion(json(put("/api/usuarios/999999999"), cuerpoEditar("A", correoNuevo(), null, "MESERO")), admin))
				.andExpect(status().isNotFound());
	}

	@Test
	void unAdministradorNoPuedeCambiarseElPropioRol() throws Exception {
		enviar(conSesion(json(put("/api/usuarios/" + admin.getId()),
				cuerpoEditar(admin.getNombre(), admin.getCorreo(), null, "MESERO")), admin))
				.andExpect(status().isConflict());
		assertThat(usuarios.findById(admin.getId()).orElseThrow().getRol()).isEqualTo(Rol.ADMINISTRADOR);

		// pero sí puede editar su nombre
		enviar(conSesion(json(put("/api/usuarios/" + admin.getId()),
				cuerpoEditar("Nombre nuevo", admin.getCorreo(), null, "ADMINISTRADOR")), admin))
				.andExpect(status().isOk());
	}

	// ------------------------------------------------------------------ desactivar y activar

	@Test
	void desactivarBloqueaElIngresoPeroConservaLaCuentaYLaAudita() throws Exception {
		Usuario cuenta = crearCuenta(Rol.MESERO, true);
		enviar(conSesion(post("/api/usuarios/" + cuenta.getId() + "/desactivar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));

		assertThat(usuarios.findById(cuenta.getId())).isPresent();
		assertThat(usuarios.findById(cuenta.getId()).orElseThrow().isActivo()).isFalse();
		assertThat(auditoria.findAll()).anySatisfy(r -> {
			assertThat(r.getTipoOperacion()).isEqualTo("USUARIO_DESACTIVADO");
			assertThat(r.getEntidadId()).isEqualTo(cuenta.getId());
		});
		// aparece en la lista, marcada como inactiva
		enviar(conSesion(get("/api/usuarios/" + cuenta.getId()), admin))
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	void desactivarDosVecesEsInofensivoYNoDuplicaLaAuditoria() throws Exception {
		Usuario cuenta = crearCuenta(Rol.MESERO, true);
		enviar(conSesion(post("/api/usuarios/" + cuenta.getId() + "/desactivar"), admin)).andExpect(status().isOk());
		enviar(conSesion(post("/api/usuarios/" + cuenta.getId() + "/desactivar"), admin)).andExpect(status().isOk());
		assertThat(auditoria.findAll().stream()
				.filter(r -> "USUARIO_DESACTIVADO".equals(r.getTipoOperacion()) && cuenta.getId().equals(r.getEntidadId()))
				.count()).isEqualTo(1);
	}

	@Test
	void unAdministradorNoPuedeDesactivarseASiMismo() throws Exception {
		enviar(conSesion(post("/api/usuarios/" + admin.getId() + "/desactivar"), admin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").exists());
		assertThat(usuarios.findById(admin.getId()).orElseThrow().isActivo()).isTrue();
	}

	@Test
	void activarReviveLaCuentaYLaDesbloquea() throws Exception {
		Usuario cuenta = crearCuenta(Rol.CAJERO, false);
		cuenta.setIntentosFallidos(5);
		cuenta.setBloqueadoHasta(java.time.Instant.now().plusSeconds(600));

		enviar(conSesion(post("/api/usuarios/" + cuenta.getId() + "/activar"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));

		Usuario guardado = usuarios.findById(cuenta.getId()).orElseThrow();
		assertThat(guardado.isActivo()).isTrue();
		assertThat(guardado.getIntentosFallidos()).isZero();
		assertThat(guardado.getBloqueadoHasta()).isNull();
	}

	@Test
	void desactivarYActivarUnaCuentaQueNoExisteResponde404() throws Exception {
		enviar(conSesion(post("/api/usuarios/999999999/desactivar"), admin)).andExpect(status().isNotFound());
		enviar(conSesion(post("/api/usuarios/999999999/activar"), admin)).andExpect(status().isNotFound());
	}

}
