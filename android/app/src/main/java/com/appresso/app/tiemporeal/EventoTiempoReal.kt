package com.appresso.app.tiemporeal

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.time.Instant

/** Tópicos del servidor (ver docs/tiempo-real.md). */
object Topicos {
    const val COMANDAS = "/topic/comandas"
    const val CATALOGO = "/topic/catalogo"
    val TODOS = setOf(COMANDAS, CATALOGO)
}

/** Sobre común de todos los mensajes del servidor (`EventoTiempoReal` en el backend). */
data class EventoTiempoReal(
    val id: String,
    val secuencia: Long,
    val tipo: String,
    val fecha: String,
    val datos: JsonObject,
) {
    /** Datos si el evento es de comandas (`COMANDA_*`). */
    fun comanda(): DatosComanda? = if (tipo.startsWith("COMANDA_")) GSON.fromJson(datos, DatosComanda::class.java) else null

    /** Datos si el evento es del catálogo (`PRODUCTO_*`, `EXISTENCIAS_*`). */
    fun producto(): DatosProducto? = if (tipo.startsWith("COMANDA_")) null else GSON.fromJson(datos, DatosProducto::class.java)

    companion object {
        private val GSON = Gson()

        /** Devuelve `null` si el texto no es un evento válido (se ignora, no se cae la conexión). */
        fun desdeJson(json: String): EventoTiempoReal? = try {
            val o = JsonParser.parseString(json).asJsonObject
            EventoTiempoReal(
                id = o.get("id").asString,
                secuencia = o.get("secuencia").asLong,
                tipo = o.get("tipo").asString,
                fecha = o.get("fecha").asString,
                datos = o.getAsJsonObject("datos") ?: JsonObject(),
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class DatosComanda(
    val comandaId: Long?,
    val mesaId: Long?,
    val estado: String?,
    val estadoAnterior: String?,
    val total: BigDecimal?,
    val itemsAgregados: List<ItemAgregado>?,
)

data class ItemAgregado(val productoId: Long?, val cantidad: Int, val observaciones: String?)

data class DatosProducto(
    val productoId: Long?,
    val categoriaId: Long?,
    val nombre: String?,
    val precio: BigDecimal?,
    val stockActual: Int,
    val umbralMinimo: Int,
    val activo: Boolean,
)

/**
 * Mide cuánto tarda un evento en llegar (SCRUM-31): meta, 95 % de los eventos en menos de 3 s.
 * Compara `fecha` (reloj del servidor) con la hora del dispositivo, así que ambos relojes deben
 * estar sincronizados (hora automática activada).
 */
class MedidorLatencia(private val limiteMs: Long = 3_000) {
    private val muestras = ArrayList<Long>()

    @Synchronized
    fun registrar(latenciaMs: Long) {
        muestras.add(latenciaMs)
    }

    /** Registra y devuelve la latencia de un evento, o `null` si su fecha no se puede leer. */
    fun registrarEvento(evento: EventoTiempoReal, ahora: Instant = Instant.now()): Long? {
        val enviado = try {
            Instant.parse(evento.fecha)
        } catch (e: Exception) {
            return null
        }
        val ms = ahora.toEpochMilli() - enviado.toEpochMilli()
        registrar(ms)
        return ms
    }

    @Synchronized
    fun cantidad(): Int = muestras.size

    /** Percentil 95 por rango más cercano; `null` si aún no hay muestras. */
    @Synchronized
    fun percentil95(): Long? {
        if (muestras.isEmpty()) return null
        val orden = muestras.sorted()
        val indice = Math.ceil(0.95 * orden.size).toInt().coerceIn(1, orden.size) - 1
        return orden[indice]
    }

    /** Porcentaje (0–100) de eventos que llegaron en menos de [limiteMs]. */
    @Synchronized
    fun porcentajeBajoLimite(): Double =
        if (muestras.isEmpty()) 0.0 else 100.0 * muestras.count { it < limiteMs } / muestras.size

    @Synchronized
    fun reiniciar() = muestras.clear()
}
