package com.appresso.backend.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

	@Test
	void sinClaveElBackendNoArrancaYElMensajeDiceQueHacer() {
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtProperties(null, 30));

		assertTrue(error.getMessage().contains("JWT_SECRET"));
	}

	@Test
	void unaClaveVaciaOCortaSeRechaza() {
		assertThrows(IllegalStateException.class, () -> new JwtProperties("", 30));
		assertThrows(IllegalStateException.class, () -> new JwtProperties("cambia_esta_clave", 30));
		assertThrows(IllegalStateException.class, () -> new JwtProperties("a".repeat(31), 30));
	}

	@Test
	void unaClaveDe32CaracteresOMasSeAcepta() {
		assertDoesNotThrow(() -> new JwtProperties("a".repeat(32), 30));
	}

	@Test
	void laVigenciaDebeSerPositiva() {
		assertThrows(IllegalStateException.class, () -> new JwtProperties("a".repeat(32), 0));
		assertThrows(IllegalStateException.class, () -> new JwtProperties("a".repeat(32), -5));
	}

}
