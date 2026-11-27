package com.appresso.backend.comandas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

import com.appresso.backend.fidelizacion.Recompensa;

/**
 * Cálculo de importes de una comanda (RF-17). Es lógica pura, sin base de datos: todo se hace con
 * {@link BigDecimal} a 2 decimales y redondeo HALF_UP, para que el resultado coincida exactamente
 * con el cálculo manual.
 *
 * <ul>
 * <li>subtotal de una línea = cantidad × precio unitario guardado;</li>
 * <li>subtotal de la comanda = suma de los subtotales de sus líneas;</li>
 * <li>descuento autorizado: {@code DESCUENTO_PORCENTAJE} = subtotal × valor / 100; {@code DESCUENTO_VALOR}
 * = valor, sin pasar del subtotal (el total nunca es negativo);</li>
 * <li>total = subtotal − descuento.</li>
 * </ul>
 */
public final class CalculadoraComanda {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	public record Importes(BigDecimal subtotal, BigDecimal descuento, BigDecimal total) {
	}

	private CalculadoraComanda() {
	}

	public static BigDecimal subtotalLinea(BigDecimal precioUnitario, int cantidad) {
		return precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
	}

	/** Importes de las líneas con el descuento de {@code recompensa} (puede ser null: sin descuento). */
	public static Importes calcular(Collection<DetalleComanda> detalles, Recompensa recompensa) {
		BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
		for (DetalleComanda d : detalles) {
			subtotal = subtotal.add(subtotalLinea(d.getPrecioUnitario(), d.getCantidad()));
		}
		BigDecimal descuento = descuento(subtotal, recompensa);
		return new Importes(subtotal, descuento, subtotal.subtract(descuento));
	}

	public static BigDecimal descuento(BigDecimal subtotal, Recompensa recompensa) {
		BigDecimal cero = BigDecimal.ZERO.setScale(2);
		if (recompensa == null) {
			return cero;
		}
		BigDecimal bruto = switch (recompensa.getTipo()) {
			case DESCUENTO_PORCENTAJE -> subtotal.multiply(recompensa.getValor()).divide(CIEN, 2, RoundingMode.HALF_UP);
			case DESCUENTO_VALOR -> recompensa.getValor().setScale(2, RoundingMode.HALF_UP);
			case PRODUCTO_GRATIS -> cero;
		};
		return bruto.min(subtotal).max(cero);
	}

	/**
	 * Un descuento es autorizado si la recompensa es de tipo descuento, está activa, está vigente hoy
	 * y el cliente tiene los puntos mínimos (RF-54, RF-58). Si no, se rechaza (400, campo
	 * {@code recompensaId}) y la comanda no se crea ni cambia su total.
	 */
	public static void validarAutorizado(Recompensa r, int puntosDelCliente, LocalDate hoy) {
		if (r.getTipo() == null || r.getTipo() == com.appresso.backend.fidelizacion.TipoRecompensa.PRODUCTO_GRATIS) {
			throw rechazo("La recompensa no es un descuento");
		}
		if (!r.isActiva()) {
			throw rechazo("La recompensa no está activa");
		}
		if ((r.getVigenteDesde() != null && hoy.isBefore(r.getVigenteDesde()))
				|| (r.getVigenteHasta() != null && hoy.isAfter(r.getVigenteHasta()))) {
			throw rechazo("La recompensa está vencida o aún no está vigente");
		}
		if (puntosDelCliente < r.getPuntosMinimos()) {
			throw rechazo("El cliente no tiene los puntos mínimos para esta recompensa");
		}
	}

	private static SolicitudComandaInvalidaException rechazo(String mensaje) {
		return new SolicitudComandaInvalidaException("recompensaId", mensaje);
	}

}
