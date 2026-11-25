package com.appresso.backend.comandas;

import java.util.EnumSet;
import java.util.Set;

import com.appresso.backend.usuarios.Rol;

/**
 * Quién puede mover una comanda a cada estado (RF-COMANDA-ESTADO, RF-25). Es la única fuente de
 * esta regla: {@link ComandaService} la consulta y {@code docs/api-comandas.md} la describe.
 *
 * <pre>
 * PENDIENTE → ENVIADA          mesero
 * ENVIADA → EN_PREPARACION     cocinero
 * EN_PREPARACION → LISTA       cocinero
 * LISTA → CERRADA              cajero (cobro en efectivo) o mesero (cobro por transferencia)
 * </pre>
 * El administrador puede ejecutar cualquiera. Solo se avanza un paso por vez, sin saltos ni
 * retrocesos. La cancelación (RF-63) y la validación del pago al cerrar son de las historias 4.2.2 y 6.x.
 */
public final class PoliticaTransiciones {

	private PoliticaTransiciones() {
	}

	/** Roles autorizados para dejar la comanda en {@code destino}; vacío si no se puede llegar a PENDIENTE. */
	public static Set<Rol> rolesPara(EstadoComanda destino) {
		return switch (destino) {
			case PENDIENTE -> EnumSet.noneOf(Rol.class);
			case ENVIADA -> EnumSet.of(Rol.MESERO, Rol.ADMINISTRADOR);
			case EN_PREPARACION, LISTA -> EnumSet.of(Rol.COCINERO, Rol.ADMINISTRADOR);
			case CERRADA -> EnumSet.of(Rol.CAJERO, Rol.MESERO, Rol.ADMINISTRADOR);
		};
	}

	/** El estado al que se puede avanzar desde {@code actual}, o null si ya está cerrada. */
	public static EstadoComanda siguiente(EstadoComanda actual) {
		EstadoComanda[] todos = EstadoComanda.values();
		return actual.ordinal() + 1 < todos.length ? todos[actual.ordinal() + 1] : null;
	}

	/**
	 * Valida la transición y el rol.
	 *
	 * @throws TransicionInvalidaException si se salta un estado, retrocede o el rol no corresponde
	 */
	public static void validar(EstadoComanda actual, EstadoComanda nuevo, Rol rol) {
		if (nuevo != siguiente(actual)) {
			throw new TransicionInvalidaException("Transición no permitida: " + actual + " → " + nuevo);
		}
		if (!rolesPara(nuevo).contains(rol)) {
			throw new TransicionInvalidaException("El rol " + rol + " no puede pasar la comanda a " + nuevo);
		}
	}

}
