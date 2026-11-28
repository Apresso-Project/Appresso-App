package com.appresso.backend.caja;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/pagos/efectivo} y {@code /transferencia}. El valor no se envía: es el
 * total de la comanda, que calcula el servidor. {@code identificadorOperacion} es opcional: el
 * cliente lo genera una vez por intento de pago y lo reenvía si no supo si llegó (RNF-11), así un
 * reintento no se confunde con un segundo pago.
 */
public record PagoSolicitud(
		@NotNull(message = "Indica la comanda que se paga") Long comandaId,
		UUID identificadorOperacion) {
}
