package com.appresso.backend.fidelizacion;

/** La contraseña con la que el cliente confirma que quiere eliminar sus datos (es irreversible). */
public record EliminarMisDatosRequest(String contrasena) {

	@Override
	public String toString() {
		return "EliminarMisDatosRequest[contrasena=***]";
	}

}
