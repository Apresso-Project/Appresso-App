package com.appresso.backend.inventario;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un producto (RF-06). {@code existenciasIniciales} y {@code umbralMinimo}
 * son opcionales y valen 0 si faltan.
 */
public record ProductoCrearRequest(
		@NotBlank(message = "El nombre es obligatorio")
		@Size(max = 120, message = "El nombre no puede superar 120 caracteres")
		String nombre,

		@Size(max = 255, message = "La descripción no puede superar 255 caracteres")
		String descripcion,

		@NotNull(message = "El precio es obligatorio")
		@DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
		@Digits(integer = 12, fraction = 2, message = "El precio admite hasta 12 enteros y 2 decimales")
		BigDecimal precio,

		@NotNull(message = "La categoría es obligatoria")
		Long categoriaId,

		@Size(max = 500, message = "La dirección de la imagen no puede superar 500 caracteres")
		@Pattern(regexp = "^$|https?://\\S+", message = "La imagen debe ser una dirección http o https")
		String imagenUrl,

		@Min(value = 0, message = "Las existencias iniciales no pueden ser negativas")
		@Max(value = 1_000_000, message = "Las existencias iniciales no pueden superar 1.000.000")
		Integer existenciasIniciales,

		@Min(value = 0, message = "El umbral mínimo no puede ser negativo")
		@Max(value = 1_000_000, message = "El umbral mínimo no puede superar 1.000.000")
		Integer umbralMinimo) {

}
