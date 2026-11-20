package com.appresso.app.data.catalogo

interface CatalogoRepository {

    suspend fun listarCategorias(todas: Boolean = true): ResultadoCatalogo<List<Categoria>>

    suspend fun obtenerCategoria(id: Long): ResultadoCatalogo<Categoria>

    suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria>

    suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria>

    suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Unit>

    suspend fun activarCategoria(id: Long): ResultadoCatalogo<Unit>

    suspend fun listarProductos(categoriaId: Long? = null, todos: Boolean = true): ResultadoCatalogo<List<Producto>>

    suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto>

    suspend fun crearProducto(solicitud: ProductoCrearRequest): ResultadoCatalogo<Producto>

    suspend fun editarProducto(id: Long, solicitud: ProductoEditarRequest): ResultadoCatalogo<Producto>

    suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Unit>

    suspend fun activarProducto(id: Long): ResultadoCatalogo<Unit>
}
