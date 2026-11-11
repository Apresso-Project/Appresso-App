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

/**
 * Con el perfil {@code dev} y una contraseña definida, el backend crea el administrador de
 * prueba al arrancar. El arranque ya ocurrió cuando empieza cada prueba, así que aquí se
 * comprueba el resultado y se vuelve a ejecutar el inicializador a mano.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
		"appresso.dev.admin-correo=" + AdminDePruebaInicializadorTest.CORREO,
		"appresso.dev.admin-contrasena=" + AdminDePruebaInicializadorTest.CLAVE })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminDePruebaInicializadorTest {

	static final String CORREO = "admin.seeder@appresso.test";
	static final String CLAVE = "Clave-Del-Seeder-1";

	@Autowired
	private AdminDePruebaInicializador inicializador;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private PasswordEncoder codificador;

	@AfterAll
	void borrarAdmin() {
		usuarios.findByCorreoIgnoreCase(CORREO).ifPresent(usuarios::delete);
	}

	@Test
	void alArrancarCreaElAdministradorConLaContrasenaConfigurada() {
		Usuario admin = usuarios.findByCorreoIgnoreCase(CORREO).orElseThrow();

		assertEquals(Rol.ADMINISTRADOR, admin.getRol());
		assertTrue(admin.isActivo());
		assertEquals(0, admin.getIntentosFallidos());
		assertTrue(admin.getContrasenaHash().startsWith("$2"), "debe guardarse con BCrypt");
		assertTrue(codificador.matches(CLAVE, admin.getContrasenaHash()));
	}

	@Test
	void unSegundoArranqueNoDuplicaNiModificaLaCuentaExistente() {
		Usuario admin = usuarios.findByCorreoIgnoreCase(CORREO).orElseThrow();
		String hashOriginal = admin.getContrasenaHash();
		admin.setContrasenaHash(codificador.encode("clave-cambiada-a-mano"));
		usuarios.save(admin);
		long totalAntes = usuarios.count();

		try {
			inicializador.run(null);

			assertEquals(totalAntes, usuarios.count());
			Usuario despues = usuarios.findByCorreoIgnoreCase(CORREO).orElseThrow();
			assertTrue(codificador.matches("clave-cambiada-a-mano", despues.getContrasenaHash()),
					"no debe volver a poner la contraseña del .env");
		} finally {
			admin.setContrasenaHash(hashOriginal);
			usuarios.save(admin);
		}
	}

	@Test
	void conContrasenaVaciaNoCreaNada() {
		String correo = "vacia.seeder@appresso.test";

		new AdminDePruebaInicializador(usuarios, codificador, correo, "   ").run(null);

		assertFalse(usuarios.findByCorreoIgnoreCase(correo).isPresent());
	}

	@Test
	void conContrasenaQueSuperaElLimiteDeBCryptNoCreaNada() {
		String correo = "larga.seeder@appresso.test";

		new AdminDePruebaInicializador(usuarios, codificador, correo, "a".repeat(73)).run(null);
		// 40 caracteres, pero 80 bytes en UTF-8.
		new AdminDePruebaInicializador(usuarios, codificador, correo, "ñ".repeat(40)).run(null);

		assertFalse(usuarios.findByCorreoIgnoreCase(correo).isPresent());
	}

}
