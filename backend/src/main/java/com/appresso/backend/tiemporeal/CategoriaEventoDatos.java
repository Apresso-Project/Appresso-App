package com.appresso.backend.tiemporeal;

/**
 * Datos de un evento de categoría: cómo quedó después del cambio. Si {@code activa} es false,
 * los productos de esa categoría no deben mostrarse en el menú.
 */
public record CategoriaEventoDatos(Long categoriaId, String nombre, boolean activa) {

}
