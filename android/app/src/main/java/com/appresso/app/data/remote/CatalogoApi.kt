package com.appresso.app.data.remote

import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.CategoriaCrearRequest
import com.appresso.app.data.catalogo.CategoriaEditarRequest
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ProductoCrearRequest
import com.appresso.app.data.catalogo.ProductoEditarRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogoApi {

    @GET("api/categorias")
    suspend fun listarCategorias(@Query("todas") todas: Boolean? = null): Response<List<Categoria>>

    @GET("api/categorias/{id}")
    suspend fun obtenerCategoria(@Path("id") id: Long): Response<Categoria>

    @POST("api/categorias")
    suspend fun crearCategoria(@Body solicitud: CategoriaCrearRequest): Response<Categoria>

    @PUT("api/categorias/{id}")
    suspend fun renombrarCategoria(
        @Path("id") id: Long,
        @Body solicitud: CategoriaEditarRequest,
    ): Response<Categoria>

    @POST("api/categorias/{id}/desactivar")
    suspend fun desactivarCategoria(@Path("id") id: Long): Response<Unit>

    @POST("api/categorias/{id}/activar")
    suspend fun activarCategoria(@Path("id") id: Long): Response<Unit>

    @GET("api/productos")
    suspend fun listarProductos(
        @Query("categoriaId") categoriaId: Long? = null,
        @Query("todos") todos: Boolean? = null,
    ): Response<List<Producto>>

    @GET("api/productos/{id}")
    suspend fun obtenerProducto(@Path("id") id: Long): Response<Producto>

    @POST("api/productos")
    suspend fun crearProducto(@Body solicitud: ProductoCrearRequest): Response<Producto>

    @PUT("api/productos/{id}")
    suspend fun editarProducto(
        @Path("id") id: Long,
        @Body solicitud: ProductoEditarRequest,
    ): Response<Producto>

    @POST("api/productos/{id}/desactivar")
    suspend fun desactivarProducto(@Path("id") id: Long): Response<Unit>

    @POST("api/productos/{id}/activar")
    suspend fun activarProducto(@Path("id") id: Long): Response<Unit>
}
