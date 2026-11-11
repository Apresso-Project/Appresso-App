package com.appresso.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.appresso.backend.usuarios.Rol;
import com.appresso.backend.usuarios.Usuario;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

class JwtServiceTest {

	private static final String CLAVE = "clave-de-prueba-con-mas-de-treinta-y-dos-caracteres";
	private static final long VIGENCIA_MINUTOS = 30;
	private static final Instant AHORA = Instant.parse("2026-11-11T12:00:00Z");

	private final JwtService servicio = servicio(CLAVE, AHORA);

	@Test
	void eltokenGeneradoSeLeeYTraeLosDatosDelUsuario() {
		JWTClaimsSet claims = servicio.leer(servicio.generar(usuario())).orElseThrow();

		assertEquals("7", claims.getSubject());
		assertEquals("MESERO", claims.getClaim("rol"));
		assertEquals("appresso", claims.getIssuer());
		assertEquals(AHORA, claims.getIssueTime().toInstant());
		assertEquals(AHORA.plusSeconds(VIGENCIA_MINUTOS * 60), claims.getExpirationTime().toInstant());
	}

	@Test
	void elTokenNoLlevaLaContrasenaNiElCorreo() throws Exception {
		String token = servicio.generar(usuario());

		String contenido = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

		assertTrue(!contenido.contains("hash") && !contenido.contains("@"), contenido);
	}

	@Test
	void unTokenVencidoSeRechaza() {
		String token = servicio.generar(usuario());

		JwtService mastarde = servicio(CLAVE, AHORA.plusSeconds(VIGENCIA_MINUTOS * 60 + 1));

		assertTrue(mastarde.leer(token).isEmpty());
	}

	@Test
	void unTokenSigueVigenteJustoAntesDeVencer() {
		String token = servicio.generar(usuario());

		JwtService casiVencido = servicio(CLAVE, AHORA.plusSeconds(VIGENCIA_MINUTOS * 60 - 1));

		assertTrue(casiVencido.leer(token).isPresent());
	}

	@Test
	void unTokenConElContenidoAlteradoSeRechaza() {
		String[] partes = servicio.generar(usuario()).split("\\.");
		String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		assertTrue(contenido.contains("MESERO"));

		// Quien intente subirse de rol cambiando el contenido deja la firma sin validez.
		String alterado = contenido.replace("MESERO", "ADMINISTRADOR");
		String token = partes[0] + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(alterado.getBytes(StandardCharsets.UTF_8))
				+ "." + partes[2];

		assertTrue(servicio.leer(token).isEmpty());
	}

	@Test
	void unTokenFirmadoConOtraClaveSeRechaza() {
		JwtService ajeno = servicio("otra-clave-distinta-con-mas-de-treinta-y-dos-caracteres", AHORA);

		assertTrue(servicio.leer(ajeno.generar(usuario())).isEmpty());
	}

	@Test
	void unTokenDeOtroEmisorSeRechaza() throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("otro").subject("7")
				.expirationTime(Date.from(AHORA.plusSeconds(600))).build();

		assertTrue(servicio.leer(firmar(claims)).isEmpty());
	}

	@Test
	void unTokenSinVencimientoSeRechaza() throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("appresso").subject("7").build();

		assertTrue(servicio.leer(firmar(claims)).isEmpty());
	}

	@Test
	void unTokenSinFirmaSeRechaza() {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("appresso").subject("7")
				.expirationTime(Date.from(AHORA.plusSeconds(600))).build();

		assertTrue(servicio.leer(new PlainJWT(claims).serialize()).isEmpty());
	}

	@Test
	void textoQueNoEsUnTokenSeRechazaSinLanzarErrores() {
		assertTrue(servicio.leer("no-es-un-token").isEmpty());
		assertTrue(servicio.leer("").isEmpty());
		assertTrue(servicio.leer("a.b.c").isEmpty());
	}

	// ---------------------------------------------------------------- apoyo

	private static JwtService servicio(String clave, Instant ahora) {
		return new JwtService(new JwtProperties(clave, VIGENCIA_MINUTOS), Clock.fixed(ahora, ZoneOffset.UTC));
	}

	private static Usuario usuario() {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo("usuario@appresso.test");
		usuario.setContrasenaHash("hash-que-no-debe-salir");
		usuario.setRol(Rol.MESERO);
		ReflectionTestUtils.setField(usuario, "id", 7L);
		return usuario;
	}

	private static String firmar(JWTClaimsSet claims) throws Exception {
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(CLAVE.getBytes(StandardCharsets.UTF_8)));
		return jwt.serialize();
	}

}
