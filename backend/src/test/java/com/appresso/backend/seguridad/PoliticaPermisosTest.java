package com.appresso.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.pattern.PathPatternParser;

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.seguridad.PoliticaPermisos.Regla;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Pruebas de rol × endpoint (SCRUM-45): cada regla de {@link PoliticaPermisos}, con cada uno de
 * los cinco roles.
 * <ul>
 * <li>Rol no permitido: debe responder <b>403</b>.</li>
 * <li>Rol permitido: no debe responder 401 ni 403. Si el endpoint aún no existe responde 404, y
 * eso está bien: lo que se prueba aquí es el permiso, no el endpoint.</li>
 * </ul>
 * Como la seguridad revisa el rol antes de llegar al controlador, estas pruebas valen desde ya y
 * siguen valiendo cuando cada módulo cree sus endpoints reales.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PoliticaPermisosTest {

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private JwtService jwt;

	private MockMvc mvc;
	private final Map<Rol, Usuario> usuarioPorRol = new EnumMap<>(Rol.class);
	private final Map<Rol, String> tokenPorRol = new EnumMap<>(Rol.class);

	@BeforeAll
	void crearUnUsuarioPorRol() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		for (Rol rol : Rol.values()) {
			Usuario u = new Usuario();
			u.setNombre("Matriz " + rol);
			u.setCorreo("matriz." + rol.name().toLowerCase() + "@appresso.test");
			u.setContrasenaHash("no-se-usa-en-esta-prueba");
			u.setRol(rol);
			Usuario guardado = usuarios.save(u);
			usuarioPorRol.put(rol, guardado);
			tokenPorRol.put(rol, jwt.generar(guardado));
		}
	}

	@AfterAll
	void borrarUsuarios() {
		usuarioPorRol.values().forEach(u -> usuarios.deleteById(u.getId()));
	}

	/** Todas las combinaciones regla × rol. */
	Stream<Arguments> reglaPorRol() {
		return PoliticaPermisos.REGLAS.stream()
				.flatMap(regla -> Arrays.stream(Rol.values()).map(rol -> Arguments.of(regla, rol)));
	}

	@ParameterizedTest(name = "{1} → {0}")
	@MethodSource("reglaPorRol")
	void cadaRolRecibeLoQueDiceLaPolitica(Regla regla, Rol rol) throws Exception {
		// Una regla para "cualquier método" se prueba con DELETE, para no caer en una regla GET anterior.
		HttpMethod metodo = regla.metodo() != null ? regla.metodo() : HttpMethod.DELETE;

		int estado = mvc.perform(request(metodo, regla.ejemplo())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPorRol.get(rol)))
				.andReturn().getResponse().getStatus();

		if (regla.roles().contains(rol)) {
			assertTrue(estado != 401 && estado != 403,
					rol + " debería poder usar " + metodo + " " + regla.ejemplo() + " pero recibió " + estado);
		} else {
			assertEquals(403, estado,
					rol + " NO debería poder usar " + metodo + " " + regla.ejemplo() + " (" + regla.requisitos() + ")");
		}
	}

	/** Evita errores de escritura en la política: cada ejemplo debe cumplir su propio patrón. */
	@Test
	void cadaEjemploCumpleSuPatron() {
		for (Regla regla : PoliticaPermisos.REGLAS) {
			boolean coincide = PathPatternParser.defaultInstance.parse(regla.patron())
					.matches(PathContainer.parsePath(regla.ejemplo()));
			assertTrue(coincide, "El ejemplo " + regla.ejemplo() + " no cumple el patrón " + regla.patron());
		}
	}

	@Test
	void sinTokenCualquierEndpointProtegidoResponde401() throws Exception {
		for (Regla regla : PoliticaPermisos.REGLAS) {
			HttpMethod metodo = regla.metodo() != null ? regla.metodo() : HttpMethod.DELETE;
			int estado = mvc.perform(request(metodo, regla.ejemplo())).andReturn().getResponse().getStatus();
			assertEquals(401, estado, "Sin token, " + metodo + " " + regla.ejemplo() + " debería responder 401");
		}
	}

}
