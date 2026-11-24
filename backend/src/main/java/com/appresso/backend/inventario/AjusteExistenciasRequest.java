package com.appresso.backend.inventario;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AjusteExistenciasRequest(
		@NotNull(message = "Selecciona el tipo de ajuste") TipoMovimientoInventario tipo,
		@NotNull(message = "Ingresa la cantidad")
		@Positive(message = "La cantidad debe ser mayor que cero")
		@Digits(integer = 10, fraction = 0, message = "La cantidad debe ser un número entero")
		@Max(value = Integer.MAX_VALUE, message = "La cantidad supera el máximo permitido")
		BigDecimal cantidad,
		@NotBlank(message = "Ingresa el concepto") @Size(max = 255, message = "El concepto no puede superar 255 caracteres")
		String concepto) {
}
