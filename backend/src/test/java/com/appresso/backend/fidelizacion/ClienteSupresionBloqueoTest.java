package com.appresso.backend.fidelizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
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

import com.appresso.backend.auth.JwtService;
import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.appresso.backend.usuarios.UsuarioRepository;

/**
 * Los intentos fallidos al confirmar la eliminación cuentan como los del inicio de sesión (RNF-26).
 * No es {@code @Transactional} a propósito: comprueba que el contador se guarda aunque la solicitud
 * se rechace. Borra sus datos al terminar.
 */
@SpringBootTest
class ClienteSupresionBloqueoTest {

	private static final String CLAVE = "Clave-Cliente-1";

	@Autowired WebApplicationContext context;
	@Autowired ClienteRepository clientes;
	@Autowired UsuarioRepository usuarios;
	@Autowired PasswordEncoder codificador;
	@Autowired JwtService jwt;

	MockMvc mvc;
	Usuario cuenta;
	Cliente cliente;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		Usuario u = new Usuario();
		u.setNombre("Marta");
		u.setCorreo(UUID.randomUUID() + "@correo.test");
		u.setContrasenaHash(codificador.encode(CLAVE));
		u.setRol(Rol.CLIENTE);
		u.setActivo(true);
		cuenta = usuarios.save(u);
		Cliente c = new Cliente();
		c.setNombre("Marta");
		c.setApellido("Lopez");
		c.setDocumento("SUP" + cuenta.getId());
		c.setCelular("3209998877");
		c.setAceptoTratamientoDatos(true);
		c.setUsuario(cuenta);
		cliente = clientes.save(c);
	}

	@AfterEach
	void limpiar() {
		clientes.deleteById(cliente.getId());
		usuarios.deleteById(cuenta.getId());
	}

	@Test
	void alQuintoFalloLaCuentaSeBloqueaYNiLaContrasenaCorrectaElimina() throws Exception {
		for (int i = 0; i < 5; i++) {
			eliminar("otra-clave-1").andExpect(status().isBadRequest());
		}
		assertThat(usuarios.findById(cuenta.getId()).orElseThrow().getIntentosFallidos()).isEqualTo(5);

		eliminar(CLAVE)
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.mensaje").value(Matchers.startsWith("Cuenta bloqueada temporalmente")));

		assertThat(clientes.findById(cliente.getId()).orElseThrow().getNombre()).isEqualTo("Marta");
		assertThat(usuarios.findById(cuenta.getId()).orElseThrow().isActivo()).isTrue();
	}

	private ResultActions eliminar(String contrasena) throws Exception {
		return mvc.perform(post("/api/clientes/yo/eliminacion").header("Authorization", "Bearer " + jwt.generar(cuenta))
				.contentType(MediaType.APPLICATION_JSON).content("{\"contrasena\":\"" + contrasena + "\"}"));
	}

}
