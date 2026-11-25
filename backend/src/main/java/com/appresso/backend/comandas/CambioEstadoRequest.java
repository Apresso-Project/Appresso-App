package com.appresso.backend.comandas;

import jakarta.validation.constraints.NotNull;

/** Estado al que se quiere mover la comanda. */
public record CambioEstadoRequest(@NotNull(message = "El estado es obligatorio") EstadoComanda estado) {
}
