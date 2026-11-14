package com.appresso.backend.seguridad;

import static com.appresso.backend.usuarios.Rol.ADMINISTRADOR;
import static com.appresso.backend.usuarios.Rol.CAJERO;
import static com.appresso.backend.usuarios.Rol.CLIENTE;
import static com.appresso.backend.usuarios.Rol.COCINERO;
import static com.appresso.backend.usuarios.Rol.MESERO;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;

import com.appresso.backend.usuarios.Rol;

/**
 * Política de permisos por rol (SCRUM-47): qué rol puede usar cada endpoint.
 * <p>
 * Es la única fuente de verdad: {@link SeguridadConfig} aplica estas reglas y
 * {@code PoliticaPermisosTest} prueba cada una con los cinco roles (SCRUM-45). La versión legible
 * está en {@code docs/permisos-por-rol.md}; si cambias una regla aquí, actualízala allá.
 * <p>
 * Reglas para mantenerla:
 * <ul>
 * <li><b>El orden importa:</b> gana la primera regla que coincide. Las rutas específicas van antes
 * que las generales ({@code /api/clientes/yo} antes de {@code /api/clientes/**}).</li>
 * <li>{@code metodo == null} significa cualquier método HTTP.</li>
 * <li>Una regla sin roles ({@link #NADIE}) cierra el resto de operaciones de un módulo: si alguien
 * agrega un endpoint sin declararlo aquí, responde 403 en vez de quedar abierto.</li>
 * <li>{@code ejemplo} es una ruta concreta que cumple el patrón; la usan las pruebas.</li>
 * </ul>
 * Las rutas son una propuesta acordada a partir de los requerimientos: quien implemente cada
 * módulo debe usar estas mismas rutas o actualizar la regla correspondiente.
 */
public final class PoliticaPermisos {

	public record Regla(HttpMethod metodo, String patron, String ejemplo, Set<Rol> roles, String requisitos) {

		public String[] nombresDeRoles() {
			return roles.stream().map(Rol::name).toArray(String[]::new);
		}

		/** Así aparece en los reportes de pruebas, por ejemplo "POST /api/comandas". */
		@Override
		public String toString() {
			return (metodo == null ? "CUALQUIERA" : metodo.name()) + " " + patron;
		}

	}

	private static final Set<Rol> NADIE = EnumSet.noneOf(Rol.class);

	private static Set<Rol> solo(Rol primero, Rol... resto) {
		return EnumSet.of(primero, resto);
	}

	private static Regla regla(HttpMethod metodo, String patron, String ejemplo, Set<Rol> roles, String requisitos) {
		return new Regla(metodo, patron, ejemplo, roles, requisitos);
	}

	private static final HttpMethod GET = HttpMethod.GET;
	private static final HttpMethod POST = HttpMethod.POST;
	private static final HttpMethod PUT = HttpMethod.PUT;
	private static final HttpMethod PATCH = HttpMethod.PATCH;
	private static final HttpMethod CUALQUIERA = null;

	public static final List<Regla> REGLAS = List.of(

			// ---------------------------------------------------------------- Usuarios (módulo 1)
			regla(CUALQUIERA, "/api/usuarios/**", "/api/usuarios", solo(ADMINISTRADOR), "RF-02"),

			// ---------------------------------------------------------------- Inventario (módulo 2)
			regla(GET, "/api/categorias/**", "/api/categorias", solo(ADMINISTRADOR, MESERO), "RF-06, RF-11"),
			regla(CUALQUIERA, "/api/categorias/**", "/api/categorias/1", solo(ADMINISTRADOR), "RF-06"),
			regla(GET, "/api/productos/**", "/api/productos", solo(ADMINISTRADOR, MESERO), "RF-06, RF-11, RF-13"),
			regla(CUALQUIERA, "/api/productos/**", "/api/productos/1", solo(ADMINISTRADOR), "RF-06, RF-09"),
			regla(CUALQUIERA, "/api/inventario/**", "/api/inventario/movimientos", solo(ADMINISTRADOR),
					"RF-07, RF-10, RF-14"),

			// ---------------------------------------------------------------- Comandas y cocina (módulos 3 y 4)
			regla(GET, "/api/mesas/**", "/api/mesas", solo(ADMINISTRADOR, MESERO), "RF-15"),
			regla(PATCH, "/api/comandas/*/estado", "/api/comandas/1/estado", solo(COCINERO), "RF-25"),
			regla(POST, "/api/comandas", "/api/comandas", solo(MESERO), "RF-15"),
			regla(POST, "/api/comandas/*/productos", "/api/comandas/1/productos", solo(MESERO), "RF-16, RF-20"),
			regla(POST, "/api/comandas/*/confirmar", "/api/comandas/1/confirmar", solo(MESERO), "RF-18"),
			regla(PUT, "/api/comandas/*/cliente", "/api/comandas/1/cliente", solo(MESERO), "RF-22"),
			regla(POST, "/api/comandas/*/recompensa", "/api/comandas/1/recompensa", solo(MESERO), "RF-58"),
			regla(GET, "/api/comandas/**", "/api/comandas", solo(ADMINISTRADOR, MESERO, CAJERO, COCINERO),
					"RF-19, RF-23"),
			regla(CUALQUIERA, "/api/comandas/**", "/api/comandas/1", NADIE, "—"),

			// ---------------------------------------------------------------- Caja y pagos (módulo 5)
			regla(POST, "/api/pagos/efectivo", "/api/pagos/efectivo", solo(CAJERO), "RF-29"),
			regla(POST, "/api/pagos/transferencia", "/api/pagos/transferencia", solo(MESERO, CAJERO),
					"RF-30, RF-41, RF-43"),
			regla(GET, "/api/pagos/**", "/api/pagos", solo(ADMINISTRADOR, CAJERO), "RF-38, RF-39"),
			regla(CUALQUIERA, "/api/pagos/**", "/api/pagos/1", NADIE, "—"),
			regla(GET, "/api/caja/qr", "/api/caja/qr", solo(MESERO, CAJERO), "RF-40"),
			regla(GET, "/api/caja/saldos", "/api/caja/saldos", solo(ADMINISTRADOR), "RF-39"),
			regla(GET, "/api/caja/movimientos", "/api/caja/movimientos", solo(ADMINISTRADOR, CAJERO), "RF-38, RF-39"),
			regla(POST, "/api/caja/ingresos", "/api/caja/ingresos", solo(ADMINISTRADOR), "RF-32"),
			regla(POST, "/api/caja/retiros", "/api/caja/retiros", solo(ADMINISTRADOR), "RF-33"),
			regla(CUALQUIERA, "/api/caja/**", "/api/caja/otra", NADIE, "—"),
			regla(PATCH, "/api/solicitudes-retiro/*/decision", "/api/solicitudes-retiro/1/decision",
					solo(ADMINISTRADOR), "RF-36"),
			regla(POST, "/api/solicitudes-retiro", "/api/solicitudes-retiro", solo(CAJERO), "RF-34"),
			regla(GET, "/api/solicitudes-retiro/**", "/api/solicitudes-retiro", solo(ADMINISTRADOR, CAJERO), "RF-36"),
			regla(CUALQUIERA, "/api/solicitudes-retiro/**", "/api/solicitudes-retiro/1", NADIE, "—"),
			regla(POST, "/api/jornadas/cierre", "/api/jornadas/cierre", solo(CAJERO), "RF-44"),
			regla(PATCH, "/api/jornadas/*/base", "/api/jornadas/1/base", solo(ADMINISTRADOR), "RF-47"),
			regla(POST, "/api/jornadas", "/api/jornadas", solo(ADMINISTRADOR, CAJERO), "RF-49"),
			regla(GET, "/api/jornadas/**", "/api/jornadas", solo(ADMINISTRADOR, CAJERO), "RF-45, RF-48"),
			regla(CUALQUIERA, "/api/jornadas/**", "/api/jornadas/1", NADIE, "—"),

			// ---------------------------------------------------------------- Fidelización (módulo 6)
			regla(GET, "/api/clientes/yo", "/api/clientes/yo", solo(CLIENTE), "RF-55"),
			regla(POST, "/api/clientes", "/api/clientes", solo(ADMINISTRADOR, MESERO), "RF-50"),
			regla(GET, "/api/clientes/**", "/api/clientes/1234567890", solo(ADMINISTRADOR, MESERO), "RF-51, RF-57"),
			regla(CUALQUIERA, "/api/clientes/**", "/api/clientes/1", NADIE, "—"),
			regla(GET, "/api/recompensas/**", "/api/recompensas", solo(ADMINISTRADOR, MESERO, CLIENTE),
					"RF-56, RF-57, RF-58"),
			regla(CUALQUIERA, "/api/recompensas/**", "/api/recompensas/1", solo(ADMINISTRADOR), "RF-54, RF-60"),
			regla(GET, "/api/promociones/**", "/api/promociones", solo(ADMINISTRADOR, CLIENTE), "RF-62"),
			regla(CUALQUIERA, "/api/promociones/**", "/api/promociones/1", solo(ADMINISTRADOR), "RF-60"));

	private PoliticaPermisos() {
	}

}
