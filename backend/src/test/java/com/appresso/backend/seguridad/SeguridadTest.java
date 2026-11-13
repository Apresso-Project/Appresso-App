package com.appresso.backend.seguridad;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Pruebas del filtro JWT y del control de acceso (SCRUM-43 y SCRUM-44).
 * <p>
 * Como aún no existen los endpoints de los módulos, la prueba agrega dos de mentira
 * ({@link EndpointsDePrueba}) para comprobar las reglas: uno bajo {@code /api/usuarios} (solo
 * administrador) y otro que solo exige sesión. Cuando existan los endpoints reales, SCRUM-45 los
 * prueba con cada rol.
 */
@SpringBootTest
@Import(SeguridadTest.EndpointsDePrueba.class)
class SeguridadTest {

	private static final String SOLO_ADMIN = "/api/usuarios/prueba-acceso";
	private static final String CON_SESION = "/api/prueba-sesion";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private JwtService jwt;

	private MockMvc mvc;
	private final List<Long> creados = new ArrayList<>();

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@AfterEach
	void borrarUsuarios() {
		creados.forEach(usuarios::deleteById);
		creados.clear();
	}

	// ------------------------------------------------------------------------- públicos

	@Test
	void healthNoPideToken() throws Exception {
		mvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void loginNoPideToken() throws Exception {
		// Cuerpo vacío: el login responde 400 por validación, no 401 por falta de token.
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------------- 401

	@Test
	void sinTokenResponde401ConMensaje() throws Exception {
		mvc.perform(get(CON_SESION))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value(SeguridadConfig.MENSAJE_401));
	}

	@Test
	void tokenAlteradoResponde401() throws Exception {
		String token = jwt.generar(crearUsuario(Rol.ADMINISTRADOR, true));
		String alterado = token.substring(0, token.length() - 2) + "xx";
		mvc.perform(get(CON_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer " + alterado))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void encabezadoSinBearerResponde401() throws Exception {
		String token = jwt.generar(crearUsuario(Rol.ADMINISTRADOR, true));
		mvc.perform(get(CON_SESION).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioDesactivadoResponde401AunqueSuTokenSeaValido() throws Exception {
		String token = jwt.generar(crearUsuario(Rol.ADMINISTRADOR, false));
		mvc.perform(get(CON_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------------- por rol

	@ParameterizedTest
	@EnumSource(Rol.class)
	void cualquierRolConSesionEntraALoQueSoloPideSesion(Rol rol) throws Exception {
		mvc.perform(get(CON_SESION).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
				.andExpect(status().isOk());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
	void otrosRolesReciben403EnUsuarios(Rol rol) throws Exception {
		mvc.perform(get(SOLO_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(rol)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensaje").value(SeguridadConfig.MENSAJE_403));
	}

	@Test
	void administradorEntraAUsuarios() throws Exception {
		mvc.perform(get(SOLO_ADMIN).header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMINISTRADOR)))
				.andExpect(status().isOk());
	}

	@Test
	void sinTokenEnUsuariosResponde401YNo403() throws Exception {
		mvc.perform(get(SOLO_ADMIN)).andExpect(status().isUnauthorized());
	}

	// ------------------------------------------------------------------------- ayudas

	private String bearer(Rol rol) {
		return "Bearer " + jwt.generar(crearUsuario(rol, true));
	}

	private Usuario crearUsuario(Rol rol, boolean activo) {
		Usuario u = new Usuario();
		u.setNombre("Prueba " + rol);
		u.setCorreo("seguridad." + rol.name().toLowerCase() + "." + creados.size() + "@appresso.test");
		u.setContrasenaHash("no-se-usa-en-esta-prueba");
		u.setRol(rol);
		u.setActivo(activo);
		Usuario guardado = usuarios.save(u);
		creados.add(guardado.getId());
		return guardado;
	}

	@TestConfiguration
	static class EndpointsDePrueba {

		@Bean
		ControladorDePrueba controladorDePrueba() {
			return new ControladorDePrueba();
		}

	}

	@RestController
	static class ControladorDePrueba {

		@GetMapping(SOLO_ADMIN)
		String soloAdmin() {
			return "ok";
		}

		@GetMapping(CON_SESION)
		String conSesion() {
			return "ok";
		}

	}

}
