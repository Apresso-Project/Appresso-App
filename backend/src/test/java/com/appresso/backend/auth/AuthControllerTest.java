package com.appresso.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Pruebas del inicio de sesión de punta a punta (controlador, servicio y base de datos H2).
 * <p>
 * Esta clase no es {@code @Transactional} a propósito: si lo fuera, la prueba compartiría la
 * transacción con el servicio y no detectaría que el contador de intentos fallidos debe guardarse
 * aunque el login termine en error. Por eso cada prueba crea sus usuarios y los borra al final.
 */
@SpringBootTest
class AuthControllerTest {

	private static final String CORREO = "login.prueba@appresso.test";
	private static final String CLAVE = "Clave-Correcta-1";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private PasswordEncoder codificador;
	@Autowired
	private JwtService jwt;

	private MockMvc mvc;
	private final List<Long> creados = new ArrayList<>();

	@BeforeEach
	void prepararMockMvc() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@AfterEach
	void borrarUsuarios() {
		creados.forEach(usuarios::deleteById);
		creados.clear();
	}

	// ---------------------------------------------------------------- inicio de sesión correcto

	@Test
	void loginValidoDevuelveTokenRolYNombre() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);

		String cuerpo = login(CORREO, CLAVE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("MESERO"))
				.andExpect(jsonPath("$.nombre").value(usuario.getNombre()))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(content().string(not(containsString("contrasena"))))
				.andExpect(content().string(not(containsString("$2"))))
				.andReturn().getResponse().getContentAsString();

		String token = JsonPath.read(cuerpo, "$.token");
		JWTClaimsSet claims = jwt.leer(token).orElseThrow();
		assertEquals(String.valueOf(usuario.getId()), claims.getSubject());
		assertEquals("MESERO", claims.getClaim("rol"));
	}

	@Test
	void elCorreoNoDistingueMayusculas() throws Exception {
		crearUsuario(CORREO, CLAVE, true);

		login("LOGIN.Prueba@APPRESSO.test", CLAVE).andExpect(status().isOk());
	}

	@Test
	void elCodificadorGuardaLasContrasenasConBCrypt() {
		String hash = codificador.encode(CLAVE);

		assertTrue(hash.startsWith("$2"), "debe ser un hash BCrypt");
		assertTrue(!hash.contains(CLAVE), "no debe contener la contraseña");
		assertTrue(codificador.matches(CLAVE, hash));
	}

	// ---------------------------------------------------------------- credenciales inválidas

	@Test
	void contrasenaIncorrectaDa401YSumaUnIntento() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);

		login(CORREO, "otra-clave")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));

		// El contador debe quedar guardado aunque el login haya terminado en error.
		assertEquals(1, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void correoInexistenteDa401ConElMismoMensaje() throws Exception {
		login("nadie@appresso.test", CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
	}

	@Test
	void cuentaInactivaDa401AunqueLaContrasenaSeaCorrecta() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, false);

		login(CORREO, CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));

		assertEquals(0, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void contrasenaDeMasDe72BytesDa401YNoUnError500() throws Exception {
		crearUsuario(CORREO, CLAVE, true);

		// 60 caracteres, pero 120 bytes en UTF-8: BCrypt no admite más de 72.
		login(CORREO, "ñ".repeat(60)).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- bloqueo por intentos fallidos

	@Test
	void alQuintoFalloLaCuentaSeBloqueaYElSextoIntentoDa423() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);

		for (int i = 1; i <= 5; i++) {
			login(CORREO, "mala").andExpect(status().isUnauthorized());
			assertEquals(i, recargar(usuario).getIntentosFallidos(), "tras el intento " + i);
		}
		assertNotNull(recargar(usuario).getBloqueadoHasta());

		// Ya bloqueada: ni siquiera la contraseña correcta entra.
		login(CORREO, CLAVE)
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.mensaje").value(containsString("15 minutos")));

		// Mientras dura el bloqueo no se siguen contando intentos.
		assertEquals(5, recargar(usuario).getIntentosFallidos());
	}

	@Test
	void elMensajeDeBloqueoIndicaLosMinutosQueFaltan() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);

		bloquearHasta(usuario, Instant.now().plusSeconds(90));
		login(CORREO, CLAVE).andExpect(status().isLocked())
				.andExpect(jsonPath("$.mensaje").value(containsString("2 minutos")));

		bloquearHasta(usuario, Instant.now().plusSeconds(30));
		login(CORREO, CLAVE).andExpect(status().isLocked())
				.andExpect(jsonPath("$.mensaje").value(containsString("1 minuto.")));
	}

	@Test
	void conElBloqueoVencidoLaContrasenaCorrectaEntraYReiniciaElContador() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);
		usuario.setIntentosFallidos(5);
		bloquearHasta(usuario, Instant.now().minusSeconds(5));

		login(CORREO, CLAVE).andExpect(status().isOk());

		Usuario actualizado = recargar(usuario);
		assertEquals(0, actualizado.getIntentosFallidos());
		assertNull(actualizado.getBloqueadoHasta());
	}

	@Test
	void conElBloqueoVencidoUnFalloEmpiezaElContadorDeCero() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);
		usuario.setIntentosFallidos(5);
		bloquearHasta(usuario, Instant.now().minusSeconds(5));

		login(CORREO, "mala").andExpect(status().isUnauthorized());

		Usuario actualizado = recargar(usuario);
		assertEquals(1, actualizado.getIntentosFallidos());
		assertNull(actualizado.getBloqueadoHasta());
	}

	@Test
	void unLoginCorrectoReiniciaLosFallosAcumulados() throws Exception {
		Usuario usuario = crearUsuario(CORREO, CLAVE, true);
		for (int i = 0; i < 3; i++) {
			login(CORREO, "mala").andExpect(status().isUnauthorized());
		}
		assertEquals(3, recargar(usuario).getIntentosFallidos());

		login(CORREO, CLAVE).andExpect(status().isOk());

		assertEquals(0, recargar(usuario).getIntentosFallidos());
	}

	// ---------------------------------------------------------------- validación de la solicitud

	@Test
	void camposVaciosDan400IndicandoCadaCampo() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"\",\"contrasena\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.correo").isNotEmpty())
				.andExpect(jsonPath("$.campos.contrasena").isNotEmpty());
	}

	@Test
	void correoSinFormatoValidoDa400() throws Exception {
		login("no-es-un-correo", CLAVE)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.correo").value("El correo no tiene un formato válido"));
	}

	@Test
	void contrasenaDeMasDe72CaracteresDa400() throws Exception {
		login(CORREO, "a".repeat(73))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.contrasena").isNotEmpty());
	}

	@Test
	void jsonMalFormadoYCuerpoAusenteDan400SinDetallesTecnicos() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("La solicitud no tiene un formato válido."));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------- apoyo

	private ResultActions login(String correo, String contrasena) throws Exception {
		String json = "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}";
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Usuario crearUsuario(String correo, String contrasena, boolean activo) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(correo);
		usuario.setContrasenaHash(codificador.encode(contrasena));
		usuario.setRol(Rol.MESERO);
		usuario.setActivo(activo);
		Usuario guardado = usuarios.save(usuario);
		creados.add(guardado.getId());
		return guardado;
	}

	private Usuario recargar(Usuario usuario) {
		return usuarios.findById(usuario.getId()).orElseThrow();
	}

	private void bloquearHasta(Usuario usuario, Instant hasta) {
		usuario.setBloqueadoHasta(hasta);
		usuarios.save(usuario);
	}

}
