package com.appresso.backend.inventario;

import java.util.List;

/** Una categoría del menú con sus productos disponibles. Nunca llega vacía. */
public record MenuCategoriaRespuesta(Long id, String nombre, List<MenuProductoRespuesta> productos) {
}
