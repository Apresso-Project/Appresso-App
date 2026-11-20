package com.appresso.backend.seguridad;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTPS en todas las comunicaciones, como en producción ({@code EXIGIR_HTTPS=true}) (8.4.1, SCRUM-100).
 * {@code secure(true)} simula una solicitud HTTPS (o HTTP interno detrás de un proxy TLS).
 */
@SpringBootTest
@TestPropertySource(properties = "appresso.seguridad.exigir-https=true")
class ExigirHttpsTest {

	@Autowired
	private WebApplicationContext context;
	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void elLoginPorHttpSeRechazaCon426AntesDeLeerLasCredenciales() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo\":\"a@appresso.test\",\"contrasena\":\"x\"}"))
				.andExpect(status().is(FiltroExigirHttps.UPGRADE_REQUIRED))
				.andExpect(jsonPath("$.mensaje").value(FiltroExigirHttps.MENSAJE));
	}

	@Test
	void unEndpointProtegidoPorHttpResponde426YNo401() throws Exception {
		mvc.perform(get("/api/comandas")).andExpect(status().is(FiltroExigirHttps.UPGRADE_REQUIRED));
	}

	@Test
	void elWebSocketSinCifrarTambienSeRechaza() throws Exception {
		mvc.perform(get("/ws/info")).andExpect(status().is(FiltroExigirHttps.UPGRADE_REQUIRED));
	}

	@Test
	void porHttpsLaSolicitudSigueNormal() throws Exception {
		mvc.perform(post("/api/auth/login").secure(true).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()); // validación del login: ya pasó el filtro
		mvc.perform(get("/api/comandas").secure(true)).andExpect(status().isUnauthorized());
	}

	@Test
	void porHttpsSeEnviaHsts() throws Exception {
		mvc.perform(get("/api/health").secure(true))
				.andExpect(status().isOk())
				.andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
				.andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
	}

	@Test
	void elHealthDelActuatorSiguePorHttpParaElBalanceador() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().is(not(FiltroExigirHttps.UPGRADE_REQUIRED)));
	}

}
