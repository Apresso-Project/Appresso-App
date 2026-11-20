package com.appresso.backend.fidelizacion;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reglas del número de documento del cliente (RF-50, RF-51, RF-01).
 * <p>
 * Se guarda y se busca <b>normalizado</b>: sin espacios, puntos ni guiones y en mayúsculas. Así
 * "1.234.567", "1234567" y "1 234 567" son el mismo documento, tanto al registrarse como al buscar
 * o al iniciar sesión. Se admiten letras porque la cédula de extranjería y el pasaporte las tienen.
 */
public final class DocumentoIdentidad {

	public static final String MENSAJE_INVALIDO = "El documento debe tener entre 5 y 20 letras o números.";

	private static final Pattern VALIDO = Pattern.compile("[A-Z0-9]{5,20}");
	private static final Pattern SEPARADORES = Pattern.compile("[\\s.\\-]");

	private DocumentoIdentidad() {
	}

	public static String normalizar(String documento) {
		if (documento == null) {
			return "";
		}
		return SEPARADORES.matcher(documento).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/** {@code normalizado} debe venir de {@link #normalizar}. */
	public static boolean esValido(String normalizado) {
		return VALIDO.matcher(normalizado).matches();
	}

}
