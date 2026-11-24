package com.appresso.backend.fidelizacion;

/**
 * Lo que el personal ve de un cliente al buscarlo (RF-51, RF-57) y lo que el cliente ve de sí mismo
 * (RF-55): solo lo necesario para identificarlo y sus puntos. El celular, el correo y los datos del
 * consentimiento no salen de aquí (RNF-09).
 */
public record ClienteRespuesta(Long id, String nombre, String apellido, String documento, int puntos) {

	public static ClienteRespuesta desde(Cliente c) {
		return new ClienteRespuesta(c.getId(), c.getNombre(), c.getApellido(), c.getDocumento(), c.getPuntos());
	}

}
