package com.appresso.backend.comandas;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una comanda: la mesa, al menos un producto y, opcionalmente, el cliente y la
 * recompensa de descuento que se le aplica (el descuento exige cliente).
 */
public record ComandaCrearRequest(
		@NotNull(message = "La mesa es obligatoria") Long mesaId,
		@NotEmpty(message = "La comanda debe tener al menos un producto") @Valid List<Item> items,
		Long clienteId,
		Long recompensaId) {

	public record Item(
			@NotNull(message = "El producto es obligatorio") Long productoId,
			@Min(value = 1, message = "La cantidad debe ser al menos 1") int cantidad,
			@Size(max = 100, message = "Las observaciones no pueden superar 100 caracteres") String observaciones) {
	}

	List<ComandaService.ItemSolicitado> aItemsSolicitados() {
		return items.stream()
				.map(i -> new ComandaService.ItemSolicitado(i.productoId(), i.cantidad(), i.observaciones()))
				.toList();
	}

}
