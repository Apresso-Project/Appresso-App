package com.appresso.app.data.catalogo

import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

class CatalogoRepositoryRemoto(private val api: CatalogoApi) : CatalogoRepository {

    override suspend fun listarCategorias(todas: Boolean): ResultadoCatalogo<List<Categoria>> =
        ejecutar({ api.listarCategorias(if (todas) true else null) }) { it }

    override suspend fun obtenerCategoria(id: Long): ResultadoCatalogo<Categoria> =
        ejecutar({ api.obtenerCategoria(id) }) { it }

    override suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria> =
        ejecutar({ api.crearCategoria(CategoriaCrearRequest(nombre)) }) { it }

    override suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria> =
        ejecutar({ api.renombrarCategoria(id, CategoriaEditarRequest(nombre)) }) { it }

    override suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Unit> =
        ejecutar({ api.desactivarCategoria(id) }) { Unit }

    override suspend fun activarCategoria(id: Long): ResultadoCatalogo<Unit> =
        ejecutar({ api.activarCategoria(id) }) { Unit }

    override suspend fun listarProductos(categoriaId: Long?, todos: Boolean): ResultadoCatalogo<List<Producto>> =
        ejecutar({ api.listarProductos(categoriaId, if (todos) true else null) }) { it }

    override suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto> =
        ejecutar({ api.obtenerProducto(id) }) { it }

    override suspend fun crearProducto(solicitud: ProductoCrearRequest): ResultadoCatalogo<Producto> =
        ejecutar({ api.crearProducto(solicitud) }) { it }

    override suspend fun editarProducto(id: Long, solicitud: ProductoEditarRequest): ResultadoCatalogo<Producto> =
        ejecutar({ api.editarProducto(id, solicitud) }) { it }

    override suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Unit> =
        ejecutar({ api.desactivarProducto(id) }) { Unit }

    override suspend fun activarProducto(id: Long): ResultadoCatalogo<Unit> =
        ejecutar({ api.activarProducto(id) }) { Unit }

    private suspend fun <T, R> ejecutar(
        llamada: suspend () -> Response<T>,
        transformar: (T) -> R,
    ): ResultadoCatalogo<R> =
        try {
            traducir(llamada(), transformar)
        } catch (e: IOException) {
            ResultadoCatalogo.SinConexion
        } catch (e: SerializationException) {
            ResultadoCatalogo.ErrorDelServidor
        }

    private fun <T, R> traducir(respuesta: Response<T>, transformar: (T) -> R): ResultadoCatalogo<R> = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null && respuesta.code() != 204) {
                ResultadoCatalogo.ErrorDelServidor
            } else {
                @Suppress("UNCHECKED_CAST")
                ResultadoCatalogo.Exito(if (cuerpo != null) transformar(cuerpo) else Unit as R)
            }
        }

        respuesta.code() == 400 -> {
            val error = leerError(respuesta)
            ResultadoCatalogo.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
        }

        respuesta.code() == 403 -> ResultadoCatalogo.SinPermiso(leerError(respuesta)?.mensaje)

        respuesta.code() == 404 -> ResultadoCatalogo.NoEncontrado(leerError(respuesta)?.mensaje)

        respuesta.code() == 409 -> {
            val error = leerError(respuesta)
            ResultadoCatalogo.Conflicto(error?.mensaje, error?.campos.orEmpty())
        }

        else -> ResultadoCatalogo.ErrorDelServidor
    }

    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: Exception) {
            null
        }
}
