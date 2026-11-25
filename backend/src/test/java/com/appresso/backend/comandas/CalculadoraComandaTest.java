package com.appresso.backend.comandas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.appresso.backend.comandas.CalculadoraComanda.Importes;
import com.appresso.backend.fidelizacion.Recompensa;
import com.appresso.backend.fidelizacion.TipoRecompensa;

/**
 * SCRUM-96: cálculos de la comanda (RF-17). Son pruebas sin base de datos; cada resultado se compara
 * con el cálculo manual hecho aquí en el comentario.
 */
class CalculadoraComandaTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 25);

	private static DetalleComanda linea(String precio, int cantidad) {
		DetalleComanda d = new DetalleComanda();
		d.setPrecioUnitario(new BigDecimal(precio));
		d.setCantidad(cantidad);
		return d;
	}

	private static Recompensa recompensa(TipoRecompensa tipo, String valor) {
		Recompensa r = new Recompensa();
		r.setTipo(tipo);
		r.setValor(new BigDecimal(valor));
		r.setActiva(true);
		r.setPuntosMinimos(100);
		return r;
	}

	@Test
	void elSubtotalDeUnaLineaEsCantidadPorPrecio() {
		// 3 × 4.500,50 = 13.501,50
		assertThat(CalculadoraComanda.subtotalLinea(new BigDecimal("4500.50"), 3)).isEqualByComparingTo("13501.50");
	}

	@Test
	void elSubtotalEsLaSumaDeLasLineasYElTotalLoIgualaSinDescuento() {
		// 2 × 4.500 = 9.000;  1 × 12.000 = 12.000;  3 × 2.500,25 = 7.500,75  →  28.500,75
		Importes i = CalculadoraComanda.calcular(
				List.of(linea("4500.00", 2), linea("12000.00", 1), linea("2500.25", 3)), null);
		assertThat(i.subtotal()).isEqualByComparingTo("28500.75");
		assertThat(i.descuento()).isEqualByComparingTo("0.00");
		assertThat(i.total()).isEqualByComparingTo("28500.75");
	}

	@Test
	void unDescuentoPorPorcentajeSeAplicaSobreElSubtotal() {
		// 20 % de 28.500,75 = 5.700,15  →  total 22.800,60
		Importes i = CalculadoraComanda.calcular(
				List.of(linea("4500.00", 2), linea("12000.00", 1), linea("2500.25", 3)),
				recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "20"));
		assertThat(i.descuento()).isEqualByComparingTo("5700.15");
		assertThat(i.total()).isEqualByComparingTo("22800.60");
	}

	@Test
	void elPorcentajeSeRedondeaAMedioHaciaArriba() {
		// 3 × 3.333,33 = 9.999,99;  15 % = 1.499,9985  →  1.500,00;  total 8.499,99
		Importes i = CalculadoraComanda.calcular(List.of(linea("3333.33", 3)),
				recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "15"));
		assertThat(i.subtotal()).isEqualByComparingTo("9999.99");
		assertThat(i.descuento()).isEqualByComparingTo("1500.00");
		assertThat(i.total()).isEqualByComparingTo("8499.99");
	}

	@Test
	void unDescuentoDeValorFijoRestaEsaCantidad() {
		// 2 × 9.000 = 18.000;  descuento 5.000  →  13.000
		Importes i = CalculadoraComanda.calcular(List.of(linea("9000.00", 2)),
				recompensa(TipoRecompensa.DESCUENTO_VALOR, "5000"));
		assertThat(i.descuento()).isEqualByComparingTo("5000.00");
		assertThat(i.total()).isEqualByComparingTo("13000.00");
	}

	@Test
	void elDescuentoNuncaDejaElTotalNegativo() {
		Importes i = CalculadoraComanda.calcular(List.of(linea("3000.00", 1)),
				recompensa(TipoRecompensa.DESCUENTO_VALOR, "10000"));
		assertThat(i.descuento()).isEqualByComparingTo("3000.00");
		assertThat(i.total()).isEqualByComparingTo("0.00");
	}

	@Test
	void sinLineasTodoEsCero() {
		Importes i = CalculadoraComanda.calcular(List.of(), null);
		assertThat(i.subtotal()).isEqualByComparingTo("0");
		assertThat(i.total()).isEqualByComparingTo("0");
	}

	@Test
	void totalEsSiempreSubtotalMenosDescuento() {
		Recompensa r = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "7.5");
		for (int cantidad = 1; cantidad <= 12; cantidad++) {
			Importes i = CalculadoraComanda.calcular(List.of(linea("1999.99", cantidad), linea("750.10", 1)), r);
			assertThat(i.total()).isEqualByComparingTo(i.subtotal().subtract(i.descuento()));
		}
	}

	// ---------- descuento autorizado ----------

	@Test
	void unaRecompensaActivaVigenteYConPuntosSuficientesEsAutorizada() {
		Recompensa r = recompensa(TipoRecompensa.DESCUENTO_VALOR, "1000");
		r.setVigenteDesde(HOY.minusDays(5));
		r.setVigenteHasta(HOY);
		assertThatCode(() -> CalculadoraComanda.validarAutorizado(r, 100, HOY)).doesNotThrowAnyException();
	}

	@Test
	void unaRecompensaSinFechasNuncaVence() {
		Recompensa r = recompensa(TipoRecompensa.DESCUENTO_VALOR, "1000");
		assertThatCode(() -> CalculadoraComanda.validarAutorizado(r, 500, HOY)).doesNotThrowAnyException();
	}

	@Test
	void unaRecompensaVencidaOAunNoVigenteSeRechaza() {
		Recompensa vencida = recompensa(TipoRecompensa.DESCUENTO_VALOR, "1000");
		vencida.setVigenteHasta(HOY.minusDays(1));
		assertThatThrownBy(() -> CalculadoraComanda.validarAutorizado(vencida, 500, HOY))
				.isInstanceOf(SolicitudComandaInvalidaException.class)
				.hasMessageContaining("vencida");

		Recompensa futura = recompensa(TipoRecompensa.DESCUENTO_VALOR, "1000");
		futura.setVigenteDesde(HOY.plusDays(1));
		assertThatThrownBy(() -> CalculadoraComanda.validarAutorizado(futura, 500, HOY))
				.isInstanceOf(SolicitudComandaInvalidaException.class);
	}

	@Test
	void unaRecompensaInactivaOQueNoEsDescuentoOSinPuntosSeRechaza() {
		Recompensa inactiva = recompensa(TipoRecompensa.DESCUENTO_VALOR, "1000");
		inactiva.setActiva(false);
		assertThatThrownBy(() -> CalculadoraComanda.validarAutorizado(inactiva, 500, HOY))
				.isInstanceOf(SolicitudComandaInvalidaException.class);

		Recompensa gratis = recompensa(TipoRecompensa.PRODUCTO_GRATIS, "0");
		assertThatThrownBy(() -> CalculadoraComanda.validarAutorizado(gratis, 500, HOY))
				.isInstanceOf(SolicitudComandaInvalidaException.class);

		Recompensa exigente = recompensa(TipoRecompensa.DESCUENTO_PORCENTAJE, "10");
		assertThatThrownBy(() -> CalculadoraComanda.validarAutorizado(exigente, 99, HOY))
				.isInstanceOf(SolicitudComandaInvalidaException.class)
				.satisfies(e -> assertThat(((SolicitudComandaInvalidaException) e).getCampo()).isEqualTo("recompensaId"));
	}

}
