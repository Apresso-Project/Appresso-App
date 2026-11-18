package com.appresso.backend.inventario;

/** Una categoría tal como la ve la app. {@code totalProductos} cuenta todos, activos o no. */
public record CategoriaRespuesta(Long id, String nombre, boolean activa, long totalProductos) {

	public static CategoriaRespuesta de(Categoria c, long totalProductos) {
		return new CategoriaRespuesta(c.getId(), c.getNombre(), c.isActiva(), totalProductos);
	}

}
