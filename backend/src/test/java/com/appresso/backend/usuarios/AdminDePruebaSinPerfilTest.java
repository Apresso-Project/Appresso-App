package com.appresso.backend.usuarios;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Sin el perfil {@code dev} no se crea ningún administrador, aunque la contraseña esté definida.
 * Es la garantía de que en un servidor, donde no se activa ese perfil, nunca aparece una cuenta
 * con una contraseña conocida.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"appresso.dev.admin-correo=sin.perfil@appresso.test",
		"appresso.dev.admin-contrasena=Clave-Que-No-Debe-Usarse-1" })
class AdminDePruebaSinPerfilTest {

	@Autowired
	private ApplicationContext contexto;
	@Autowired
	private UsuarioRepository usuarios;

	@Test
	void sinElPerfilDevElInicializadorNiSiquieraSeCarga() {
		assertTrue(contexto.getBeansOfType(AdminDePruebaInicializador.class).isEmpty());
	}

	@Test
	void sinElPerfilDevNoSeCreaNingunUsuario() {
		assertFalse(usuarios.findByCorreoIgnoreCase("sin.perfil@appresso.test").isPresent());
	}

}
