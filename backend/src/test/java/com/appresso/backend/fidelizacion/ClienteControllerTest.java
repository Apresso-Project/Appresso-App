package com.appresso.backend.fidelizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auditoria.RegistroAuditoria;
import com.appresso.backend.auditoria.RegistroAuditoriaRepository;
import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Registro del cliente desde la app (RF-50), búsqueda por documento (RF-51) y consulta de la propia
 * cuenta (RF-55), de punta a punta con la seguridad activa. Cada prueba se revierte al terminar.
 */
@SpringBootTest
@Transactional
class ClienteControllerTest {

	private static final String CLAVE = "Clave-Cliente-1";

	@Autowired WebApplicationContext context;
	@Autowired ClienteRepository clientes;
	@Autowired UsuarioRepository usuarios;
	@Autowired RegistroAuditoriaRepository auditoria;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	// ---------------------------------------------------------------- registro (RF-50)

	@Test
	void unRegistroCompletoCreaLaCuentaDelClienteConCeroPuntos() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, true))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mensaje").value(ClienteService.MENSAJE_REGISTRO_EXITOSO))
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.puntos").value(0))
				.andExpect(content().string(not(containsString(CLAVE))));

		Cliente cliente = clientes.findByDocumento("1234567890").orElseThrow();
		assertThat(cliente.getPuntos()).isZero();
		assertThat(cliente.isAceptoTratamientoDatos()).isTrue();
		assertThat(cliente.getFechaAceptacionDatos()).isNotNull();

		Usuario cuenta = cliente.getUsuario();
		assertThat(cuenta.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(cuenta.getCorreo()).isEqualTo("ana@correo.test");
		assertThat(cuenta.getContrasenaHash()).startsWith("$2").doesNotContain(CLAVE);
		assertThat(codificador.matches(CLAVE, cuenta.getContrasenaHash())).isTrue();
	}

	@Test
	void elRegistroDejaTrazaDelConsentimientoSinCopiarDatosPersonales() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, true)).andExpect(status().isCreated());
		Cliente cliente = clientes.findByDocumento("1234567890").orElseThrow();

		List<RegistroAuditoria> registros = auditoria.findAll().stream()
				.filter(r -> "CONSENTIMIENTO_DATOS_1581".equals(r.getTipoOperacion()))
				.filter(r -> cliente.getId().equals(r.getEntidadId()))
				.toList();
		assertThat(registros).hasSize(1);
		assertThat(registros.get(0).getDetalle()).contains("Ley 1581").doesNotContain("1234567890", "Ana", "Pérez");
	}

	@Test
	void conLaCuentaCreadaElClienteIniciaSesionConSuDocumento() throws Exception {
		registrar(datos("1.234.567.890", "ana@correo.test", CLAVE, true)).andExpect(status().isCreated());

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"identificador\":\"1234567890\",\"contrasena\":\"" + CLAVE + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("CLIENTE"));
	}

	@Test
	void sinAceptarElTratamientoDeDatosNoSeRegistra() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, false))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Completa todos los datos y acepta el tratamiento de datos personales."))
				.andExpect(jsonPath("$.campos.aceptoTratamientoDatos").isNotEmpty());

		assertThat(clientes.existsByDocumento("1234567890")).isFalse();
		assertThat(usuarios.findByCorreoIgnoreCase("ana@correo.test")).isEmpty();
	}

	@Test
	void conUnDatoFaltanteNoSeRegistraYSeIndicaElCampo() throws Exception {
		String sinNombre = datos("1234567890", "ana@correo.test", CLAVE, true).replace("\"nombre\":\"Ana\"", "\"nombre\":\"\"");

		registrar(sinNombre)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Completa todos los datos y acepta el tratamiento de datos personales."))
				.andExpect(jsonPath("$.campos.nombre").value("Ingresa tu nombre."));
	}

	@Test
	void unaContrasenaFueraDe8A72CaracteresSeRechaza() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", "corta", true))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La contraseña debe tener entre 8 y 72 caracteres."));

		registrar(datos("1234567890", "ana@correo.test", "a".repeat(73), true))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La contraseña debe tener entre 8 y 72 caracteres."));

		// 40 caracteres pero 80 bytes: BCrypt no los admite.
		registrar(datos("1234567890", "ana@correo.test", "ñ".repeat(40), true))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.contrasena").value("La contraseña debe tener entre 8 y 72 caracteres."));
	}

	@Test
	void unCorreoSinFormatoValidoSeRechaza() throws Exception {
		registrar(datos("1234567890", "ana-sin-arroba", CLAVE, true))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("Ingresa un correo electrónico válido."));
	}

	@Test
	void unDocumentoConFormatoInvalidoSeRechaza() throws Exception {
		registrar(datos("12", "ana@correo.test", CLAVE, true))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.documento").value(DocumentoIdentidad.MENSAJE_INVALIDO));
	}

	@Test
	void unDocumentoYaRegistradoSeRechazaAunqueVengaConPuntos() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, true)).andExpect(status().isCreated());

		registrar(datos("1.234.567.890", "otra@correo.test", CLAVE, true))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("Este documento ya está registrado."))
				.andExpect(jsonPath("$.campos.documento").isNotEmpty());
	}

	@Test
	void unCorreoYaUsadoPorCualquierCuentaSeRechazaSinDistinguirMayusculas() throws Exception {
		Usuario mesero = crearPersonal(Rol.MESERO);

		registrar(datos("1234567890", mesero.getCorreo().toUpperCase(), CLAVE, true))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value("Este correo ya está registrado."))
				.andExpect(jsonPath("$.campos.correo").isNotEmpty());
	}

	@Test
	void unCuerpoIlegibleDa400SinDetallesTecnicos() throws Exception {
		registrar("{no es json")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La solicitud no tiene un formato válido."));
	}

	@Test
	void elPersonalNoRegistraNiEliminaClientes() throws Exception {
		for (Rol rol : List.of(Rol.ADMINISTRADOR, Rol.MESERO)) {
			String token = token(crearPersonal(rol));
			mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content(datos("1234567890", "ana@correo.test", CLAVE, true)))
					.andExpect(status().isForbidden());
			mvc.perform(delete("/api/clientes/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isForbidden());
		}
	}

	// ---------------------------------------------------------------- búsqueda por documento (RF-51)

	@Test
	void elMeseroYElAdministradorEncuentranAlClienteConSuNombreYPuntos() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, true)).andExpect(status().isCreated());

		for (Rol rol : List.of(Rol.MESERO, Rol.ADMINISTRADOR)) {
			buscar("1234567890", token(crearPersonal(rol)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.nombre").value("Ana"))
					.andExpect(jsonPath("$.apellido").value("Pérez"))
					.andExpect(jsonPath("$.documento").value("1234567890"))
					.andExpect(jsonPath("$.puntos").value(0))
					// Solo lo necesario (RNF-09): ni celular, ni correo, ni datos del consentimiento.
					.andExpect(jsonPath("$.celular").doesNotExist())
					.andExpect(jsonPath("$.correo").doesNotExist())
					.andExpect(jsonPath("$.aceptoTratamientoDatos").doesNotExist());
		}
	}

	@Test
	void unDocumentoNoRegistradoResponde404ClienteNoEncontrado() throws Exception {
		buscar("5555555555", token(crearPersonal(Rol.MESERO)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").value("Cliente no encontrado"));
	}

	@Test
	void losRolesSinPermisoNoBuscanClientes() throws Exception {
		for (Rol rol : List.of(Rol.CAJERO, Rol.COCINERO, Rol.CLIENTE)) {
			buscar("1234567890", token(crearPersonal(rol)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));
		}
	}

	@Test
	void yaNoHayListaCompletaDeClientes() throws Exception {
		// GET /api/clientes no existe: los datos de clientes solo se consultan por documento.
		mvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(crearPersonal(Rol.ADMINISTRADOR))))
				.andExpect(status().is4xxClientError());
	}

	// ---------------------------------------------------------------- la cuenta propia (RF-55)

	@Test
	void elClienteConsultaSusPropiosDatosYPuntos() throws Exception {
		registrar(datos("1234567890", "ana@correo.test", CLAVE, true)).andExpect(status().isCreated());
		Usuario cuenta = clientes.findByDocumento("1234567890").orElseThrow().getUsuario();

		mvc.perform(get("/api/clientes/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(cuenta)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documento").value("1234567890"))
				.andExpect(jsonPath("$.puntos").value(0));
	}

	@Test
	void elPersonalNoUsaLaRutaDelCliente() throws Exception {
		mvc.perform(get("/api/clientes/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(crearPersonal(Rol.ADMINISTRADOR))))
				.andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------------- apoyo

	private static String datos(String documento, String correo, String contrasena, boolean acepta) {
		return """
				{"nombre":"Ana","apellido":"Pérez","documento":"%s","celular":"300 123 4567",
				 "correo":"%s","contrasena":"%s","aceptoTratamientoDatos":%s}
				""".formatted(documento, correo, contrasena, acepta);
	}

	private ResultActions registrar(String json) throws Exception {
		return mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions buscar(String documento, String token) throws Exception {
		return mvc.perform(get("/api/clientes/" + documento).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private Usuario crearPersonal(Rol rol) {
		Usuario u = new Usuario();
		u.setNombre("Prueba " + rol);
		u.setCorreo(rol.name().toLowerCase() + "." + UUID.randomUUID() + "@appresso.test");
		u.setContrasenaHash("no-se-usa");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.save(u);
	}

	private String token(Usuario usuario) {
		return jwt.generar(usuario);
	}

}
