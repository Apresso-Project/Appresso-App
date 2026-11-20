package com.appresso.app.ui.catalogo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/**
 * Descarga las imágenes de producto desde su `imagenUrl` (el backend solo guarda la URL; ver
 * docs/api-catalogo.md). Usa su propio cliente HTTP, sin el token de sesión, porque la URL es de
 * un sitio externo y la sesión no debe salir de la app. Guarda las ya vistas en memoria.
 */
object CargadorImagen {

    private const val MAX_BYTES = 5L * 1024 * 1024
    private const val LADO_MAXIMO = 512

    private val cliente = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(24) {}

    /** La imagen, o null si la URL no sirve, no hay red o el archivo no es una imagen. */
    suspend fun cargar(url: String): ImageBitmap? {
        cache.get(url)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            try {
                val direccion = url.toHttpUrlOrNull() ?: return@withContext null
                cliente.newCall(Request.Builder().url(direccion).build()).execute().use { respuesta ->
                    val cuerpo = respuesta.body ?: return@withContext null
                    if (!respuesta.isSuccessful || cuerpo.contentLength() > MAX_BYTES) return@withContext null
                    val bytes = cuerpo.bytes()
                    if (bytes.size > MAX_BYTES) return@withContext null
                    decodificar(bytes)?.also { cache.put(url, it) }?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Reduce la imagen al decodificarla para no gastar memoria con fotos grandes. */
    private fun decodificar(bytes: ByteArray): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        var reduccion = 1
        while (medidas.outWidth / (reduccion * 2) >= LADO_MAXIMO && medidas.outHeight / (reduccion * 2) >= LADO_MAXIMO) {
            reduccion *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = reduccion }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
    }
}
