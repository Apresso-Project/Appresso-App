package com.appresso.backend.usuarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.appresso.backend.fidelizacion.Cliente;
import com.appresso.backend.fidelizacion.ClienteRepository;

/**
 * Con el perfil {@code dev} y una contraseña definida, al arrancar se crea un usuario de prueba
 * por cada rol que no es administrador (SCRUM-51).
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "appresso.dev.usuarios-contrasena=" + UsuariosDePruebaInicializadorTest.CLAVE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsuariosDePruebaInicializadorTest {

	static final String CLAVE = "Clave-Usuarios-Prueba-1";

	@Autowired
	private UsuariosDePruebaInicializador inicializador;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private ClienteRepository clientes;
	@Autowired
	private PasswordEncoder codificador;

	@AfterAll
	void borrarUsuarios() {
		// El registro del cliente apunta a su cuenta: se borra primero.
		clientes.findByDocumento(UsuariosDePruebaInicializador.DOCUMENTO_CLIENTE).ifPresent(clientes::delete);
		UsuariosDePruebaInicializador.USUARIOS.values()
				.forEach(datos -> usuarios.findByCorreoIgnoreCase(datos[0]).ifPresent(usuarios::delete));
	}

	@Test
	void alArrancarCreaUnUsuarioActivoPorCadaRolConLaContrasenaConfigurada() {
		UsuariosDePruebaInicializador.USUARIOS.forEach((rol, datos) -> {
			Usuario usuario = usuarios.findByCorreoIgnoreCase(datos[0]).orElseThrow();

			assertEquals(rol, usuario.getRol());
			assertTrue(usuario.isActivo());
			assertTrue(codificador.matches(CLAVE, usuario.getContrasenaHash()), datos[0]);
		});
	}

	@Test
	void hayUnUsuarioParaCadaRolQueNoEsAdministrador() {
		for (Rol rol : Rol.values()) {
			assertEquals(rol != Rol.ADMINISTRADOR, UsuariosDePruebaInicializador.USUARIOS.containsKey(rol), rol.name());
		}
	}

	@Test
	void elClienteDePruebaTieneSuRegistroConDocumentoParaIniciarSesion() {
		Usuario cuenta = usuarios.findByCorreoIgnoreCase(UsuariosDePruebaInicializador.USUARIOS.get(Rol.CLIENTE)[0])
				.orElseThrow();
		Cliente cliente = clientes.findByDocumento(UsuariosDePruebaInicializador.DOCUMENTO_CLIENTE).orElseThrow();

		assertEquals(cuenta.getId(), cliente.getUsuario().getId());
		assertTrue(cliente.isAceptoTratamientoDatos());
	}

	@Test
	void unSegundoArranqueNoDuplicaUsuarios() {
		long totalAntes = usuarios.count();

		inicializador.run(null);

		assertEquals(totalAntes, usuarios.count());
	}

	@Test
	void conContrasenaVaciaNoCreaNada() {
		borrarUsuarios();
		try {
			new UsuariosDePruebaInicializador(usuarios, clientes, codificador, "  ").run(null);

			UsuariosDePruebaInicializador.USUARIOS.values()
					.forEach(datos -> assertFalse(usuarios.findByCorreoIgnoreCase(datos[0]).isPresent()));
		} finally {
			inicializador.run(null); // los deja como estaban para las demás pruebas
		}
	}

}
