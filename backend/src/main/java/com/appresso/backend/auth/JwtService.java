package com.appresso.backend.auth;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.appresso.backend.usuarios.Usuario;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Emite y lee los tokens de sesión (JWT firmado con HS256).
 * <p>
 * El token lleva el id del usuario ({@code sub}), su rol ({@code rol}) y las fechas de emisión y
 * vencimiento. No lleva datos sensibles: cualquiera que tenga el token puede leer su contenido,
 * pero solo el servidor puede firmarlo.
 */
@Service
public class JwtService {

	static final String EMISOR = "appresso";
	static final String CLAIM_ROL = "rol";

	private final byte[] clave;
	private final Duration vigencia;
	private final Clock reloj;

	public JwtService(JwtProperties propiedades, Clock reloj) {
		this.clave = propiedades.secret().getBytes(StandardCharsets.UTF_8);
		this.vigencia = Duration.ofMinutes(propiedades.expiracionMinutos());
		this.reloj = reloj;
	}

	public String generar(Usuario usuario) {
		Instant ahora = reloj.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(EMISOR)
				.subject(String.valueOf(usuario.getId()))
				.claim(CLAIM_ROL, usuario.getRol().name())
				.issueTime(Date.from(ahora))
				.expirationTime(Date.from(ahora.plus(vigencia)))
				.build();
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(clave));
			return jwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("No se pudo firmar el token de sesión", e);
		}
	}

	/**
	 * Devuelve el contenido del token si la firma es válida, el emisor es este servidor y no ha
	 * vencido. En cualquier otro caso (token alterado, mal formado o vencido) devuelve vacío.
	 */
	public Optional<JWTClaimsSet> leer(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(clave))) {
				return Optional.empty();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date vencimiento = claims.getExpirationTime();
			if (!EMISOR.equals(claims.getIssuer()) || vencimiento == null
					|| !vencimiento.toInstant().isAfter(reloj.instant())) {
				return Optional.empty();
			}
			return Optional.of(claims);
		} catch (ParseException | JOSEException e) {
			return Optional.empty();
		}
	}

}
