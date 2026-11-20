package com.appresso.app.data.catalogo

/**
 * Catálogo de productos y categorías. Existe como interfaz para poder sustituirla en las pruebas.
 * Nunca lanza por errores de red o del servidor: los devuelve como [ResultadoCatalogo]. Las
 * operaciones que modifican devuelven el objeto ya actualizado, para refrescar la lista sin
 * volver a pedirla.
 *
 * Nada se borra: categorías y productos solo se desactivan y se reactivan.
 */
interface CatalogoRepository {

    /**
     * Categorías ordenadas por nombre. [incluirInactivas] (solo administrador) agrega las
     * desactivadas; con `false` es el menú que ve el mesero.
     */
    suspend fun listarCategorias(incluirInactivas: Boolean = false): ResultadoCatalogo<List<Categoria>>

    suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria>

    suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria>

    /** Idempotente. Los productos de la categoría salen del menú; no cambian de estado. */
    suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Categoria>

    /** Idempotente. Sus productos activos vuelven al menú. */
    suspend fun activarCategoria(id: Long): ResultadoCatalogo<Categoria>

    /**
     * Productos ordenados por nombre, de una categoría o de todas. [incluirFueraDelMenu] (solo
     * administrador) agrega los desactivados y los de categorías desactivadas.
     */
    suspend fun listarProductos(
        categoriaId: Long? = null,
        incluirFueraDelMenu: Boolean = false,
    ): ResultadoCatalogo<List<Producto>>

    suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto>

    suspend fun crearProducto(datos: DatosProducto): ResultadoCatalogo<Producto>

    suspend fun editarProducto(id: Long, datos: DatosProducto): ResultadoCatalogo<Producto>

    suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Producto>

    suspend fun activarProducto(id: Long): ResultadoCatalogo<Producto>
}
