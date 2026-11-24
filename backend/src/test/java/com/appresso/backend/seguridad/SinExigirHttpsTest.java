package com.appresso.backend.seguridad;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * En desarrollo local ({@code EXIGIR_HTTPS} sin definir) el backend sigue respondiendo por HTTP,
 * y HSTS no se envía por HTTP (el estándar lo prohíbe).
 */
@SpringBootTest
class SinExigirHttpsTest {

	@Autowired
	private WebApplicationContext context;
	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void enLocalElLoginFuncionaPorHttp() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void porHttpNoSeEnviaHsts() throws Exception {
		mvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
	}

}
