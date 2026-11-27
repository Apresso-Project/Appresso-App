package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.appresso.backend.usuarios.Rol;

/** SCRUM-94/96: quién puede cada transición y que no hay saltos ni retrocesos (RF-COMANDA-ESTADO). */
class PoliticaTransicionesTest {

	private static final EstadoComanda[] ESTADOS = EstadoComanda.values();

	@Test
	void hayCincoEstadosEnEsteOrden() {
		assertThat(ESTADOS).containsExactly(EstadoComanda.PENDIENTE, EstadoComanda.ENVIADA,
				EstadoComanda.EN_PREPARACION, EstadoComanda.LISTA, EstadoComanda.CERRADA);
	}

	@Test
	void cadaTransicionLaHaceSuRolYElAdministrador() {
		assertThat(PoliticaTransiciones.rolesPara(EstadoComanda.ENVIADA)).containsExactlyInAnyOrder(Rol.MESERO, Rol.ADMINISTRADOR);
		assertThat(PoliticaTransiciones.rolesPara(EstadoComanda.EN_PREPARACION)).containsExactlyInAnyOrder(Rol.COCINERO, Rol.ADMINISTRADOR);
		assertThat(PoliticaTransiciones.rolesPara(EstadoComanda.LISTA)).containsExactlyInAnyOrder(Rol.COCINERO, Rol.ADMINISTRADOR);
		assertThat(PoliticaTransiciones.rolesPara(EstadoComanda.CERRADA)).containsExactlyInAnyOrder(Rol.CAJERO, Rol.MESERO, Rol.ADMINISTRADOR);
		assertThat(PoliticaTransiciones.rolesPara(EstadoComanda.PENDIENTE)).isEmpty();
	}

	@Test
	void conUnPasoAdelanteSoloPasanLosRolesAutorizados() {
		for (int i = 0; i < ESTADOS.length - 1; i++) {
			EstadoComanda actual = ESTADOS[i];
			EstadoComanda nuevo = ESTADOS[i + 1];
			Set<Rol> permitidos = PoliticaTransiciones.rolesPara(nuevo);
			for (Rol rol : Rol.values()) {
				if (permitidos.contains(rol)) {
					assertThatCode(() -> PoliticaTransiciones.validar(actual, nuevo, rol)).doesNotThrowAnyException();
				} else {
					assertThatThrownBy(() -> PoliticaTransiciones.validar(actual, nuevo, rol))
							.as("%s no debería pasar %s → %s", rol, actual, nuevo)
							.isInstanceOf(TransicionInvalidaException.class);
				}
			}
		}
	}

	@Test
	void ningunRolSaltaRetrocedeOSeQuedaEnElMismoEstado() {
		for (EstadoComanda actual : ESTADOS) {
			for (EstadoComanda nuevo : ESTADOS) {
				if (nuevo == PoliticaTransiciones.siguiente(actual)) {
					continue;
				}
				for (Rol rol : EnumSet.allOf(Rol.class)) {
					assertThatThrownBy(() -> PoliticaTransiciones.validar(actual, nuevo, rol))
							.as("%s: %s → %s", rol, actual, nuevo)
							.isInstanceOf(TransicionInvalidaException.class);
				}
			}
		}
	}

	@Test
	void despuesDeCerradaNoHaySiguiente() {
		assertThat(PoliticaTransiciones.siguiente(EstadoComanda.CERRADA)).isNull();
		assertThat(PoliticaTransiciones.siguiente(EstadoComanda.PENDIENTE)).isEqualTo(EstadoComanda.ENVIADA);
	}

}
