package com.appresso.app.data.catalogo

import com.appresso.app.data.remote.CatalogoApi
import com.appresso.app.data.remote.CategoriaBody
import com.appresso.app.data.remote.CategoriaDto
import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.ProductoCrearBody
import com.appresso.app.data.remote.ProductoDto
import com.appresso.app.data.remote.ProductoEditarBody
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Habla con `/api/categorias` y `/api/productos` y traduce cada respuesta del contrato
 * (docs/api-catalogo.md) a un [ResultadoCatalogo]. El token y el cierre de sesión ante un 401 los
 * maneja el cliente HTTP compartido.
 */
class CatalogoRepositoryRemoto(private val api: CatalogoApi) : CatalogoRepository {

    // ---------------------------------------------------------------- categorías

    override suspend fun listarCategorias(incluirInactivas: Boolean): ResultadoCatalogo<List<Categoria>> =
        ejecutar({ api.listarCategorias(todas = if (incluirInactivas) true else null) }) { lista ->
            lista.map { it.aCategoria() }
        }

    override suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria> =
        ejecutar({ api.crearCategoria(CategoriaBody(nombre)) }) { it.aCategoria() }

    override suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria> =
        ejecutar({ api.renombrarCategoria(id, CategoriaBody(nombre)) }) { it.aCategoria() }

    override suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Categoria> =
        ejecutar({ api.desactivarCategoria(id) }) { it.aCategoria() }

    override suspend fun activarCategoria(id: Long): ResultadoCatalogo<Categoria> =
        ejecutar({ api.activarCategoria(id) }) { it.aCategoria() }

    // ---------------------------------------------------------------- productos

    override suspend fun listarProductos(
        categoriaId: Long?,
        incluirFueraDelMenu: Boolean,
    ): ResultadoCatalogo<List<Producto>> =
        ejecutar({ api.listarProductos(categoriaId, todos = if (incluirFueraDelMenu) true else null) }) { lista ->
            lista.map { it.aProducto() }
        }

    override suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto> =
        ejecutar({ api.obtenerProducto(id) }) { it.aProducto() }

    override suspend fun crearProducto(datos: DatosProducto): ResultadoCatalogo<Producto> =
        ejecutar({
            api.crearProducto(
                ProductoCrearBody(
                    nombre = datos.nombre,
                    precio = datos.precio,
                    categoriaId = datos.categoriaId,
                    descripcion = datos.descripcion.sinVacio(),
                    imagenUrl = datos.imagenUrl.sinVacio(),
                    existenciasIniciales = datos.existenciasIniciales,
                    umbralMinimo = datos.umbralMinimo,
                ),
            )
        }) { it.aProducto() }

    override suspend fun editarProducto(id: Long, datos: DatosProducto): ResultadoCatalogo<Producto> =
        ejecutar({
            api.editarProducto(
                id,
                ProductoEditarBody(
                    nombre = datos.nombre,
                    precio = datos.precio,
                    categoriaId = datos.categoriaId,
                    descripcion = datos.descripcion.sinVacio(),
                    imagenUrl = datos.imagenUrl.sinVacio(),
                    umbralMinimo = datos.umbralMinimo,
                ),
            )
        }) { it.aProducto() }

    override suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Producto> =
        ejecutar({ api.desactivarProducto(id) }) { it.aProducto() }

    override suspend fun activarProducto(id: Long): ResultadoCatalogo<Producto> =
        ejecutar({ api.activarProducto(id) }) { it.aProducto() }

    // ---------------------------------------------------------------- traducción

    private suspend fun <T, R> ejecutar(
        llamada: suspend () -> Response<T>,
        transformar: (T) -> R,
    ): ResultadoCatalogo<R> =
        try {
            traducir(llamada(), transformar)
        } catch (e: IOException) {
            ResultadoCatalogo.SinConexion
        } catch (e: SerializationException) {
            // Respondió 2xx con un cuerpo ilegible.
            ResultadoCatalogo.ErrorDelServidor
        }

    private fun <T, R> traducir(respuesta: Response<T>, transformar: (T) -> R): ResultadoCatalogo<R> = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null) {
                ResultadoCatalogo.ErrorDelServidor
            } else {
                ResultadoCatalogo.Exito(transformar(cuerpo))
            }
        }

        respuesta.code() == 400 -> {
            val error = leerError(respuesta)
            ResultadoCatalogo.DatosInvalidos(error?.mensaje, error?.campos.orEmpty())
        }

        respuesta.code() == 401 -> ResultadoCatalogo.SesionInvalida

        respuesta.code() == 403 -> ResultadoCatalogo.SinPermiso(leerError(respuesta)?.mensaje)

        respuesta.code() == 404 -> ResultadoCatalogo.NoEncontrado

        respuesta.code() == 409 -> {
            val error = leerError(respuesta)
            ResultadoCatalogo.Conflicto(error?.mensaje, error?.campos.orEmpty())
        }

        else -> ResultadoCatalogo.ErrorDelServidor
    }

    /** Si el cuerpo falta o no es JSON válido devuelve null en vez de fallar. */
    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}

/** Un texto en blanco se envía como «no indicado» (ausente). */
private fun String?.sinVacio(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun CategoriaDto.aCategoria() = Categoria(id, nombre, activa, totalProductos)

private fun ProductoDto.aProducto() = Producto(
    id = id,
    categoriaId = categoriaId,
    categoriaNombre = categoriaNombre,
    categoriaActiva = categoriaActiva,
    nombre = nombre,
    descripcion = descripcion,
    precio = precio,
    imagenUrl = imagenUrl,
    stockActual = stockActual,
    umbralMinimo = umbralMinimo,
    activo = activo,
)
