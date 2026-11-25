package com.appresso.app.tiemporeal

import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Los tiempos de un cambio de cocina, en milisegundos (SCRUM-93). */
data class MuestraCocina(
    val tipo: String,
    /** De que el servidor publicó el evento a que este dispositivo lo recibió; null si el evento no trae fecha. */
    val llegadaMs: Long?,
    /** De que el dispositivo recibió el evento a que la lista nueva ya estaba en pantalla. Solo depende de este teléfono. */
    val actualizacionMs: Long,
    /** De que el servidor publicó el evento a que la lista nueva ya estaba en pantalla: **el plazo de 3 s**. */
    val totalMs: Long?,
)

/**
 * Mide el plazo de la vista de cocina (RF-18, RF-21, RF-26: menos de 3 s): cuánto pasa desde que el
 * servidor publica un cambio hasta que la lista actualizada se muestra en este dispositivo.
 *
 * Usa dos relojes: el del servidor (la `fecha` del evento) y el del teléfono. [llegadaMs] y [totalMs]
 * solo son confiables con la hora automática activada en el teléfono y en el servidor; [actualizacionMs]
 * usa solo el reloj del teléfono y es válido siempre. Una resincronización (reconexión) no trae fecha,
 * así que solo tiene [actualizacionMs].
 *
 * @param registro recibe una línea de texto por muestra; en la app va a `Log.i("Cocina", …)` para
 * leerla con `adb logcat -s Cocina`. Inyectable para probar sin Android.
 */
class MedidorCocina(
    private val limiteMs: Long = 3_000,
    private val reloj: () -> Instant = Instant::now,
    private val registro: (String) -> Unit = {},
) {
    private val muestras = ArrayList<MuestraCocina>()

    /** Llamar cuando la lista que ya refleja [cambio] está en el estado de la pantalla. */
    @Synchronized
    fun alMostrarse(cambio: CambioCocina): MuestraCocina {
        val ahora = reloj()
        val muestra = MuestraCocina(
            tipo = cambio.tipo,
            llegadaMs = cambio.fechaServidor?.let { Duration.between(it, cambio.recibidoEn).toMillis() },
            actualizacionMs = Duration.between(cambio.recibidoEn, ahora).toMillis(),
            totalMs = cambio.fechaServidor?.let { Duration.between(it, ahora).toMillis() },
        )
        muestras.add(muestra)
        registro(linea(muestra))
        return muestra
    }

    @Synchronized
    fun cantidad(): Int = muestras.size

    @Synchronized
    fun muestras(): List<MuestraCocina> = muestras.toList()

    /** Percentil 95 (rango más cercano) del tiempo total; null si aún no hay muestras con fecha del servidor. */
    @Synchronized
    fun percentil95(): Long? {
        val orden = muestras.mapNotNull { it.totalMs }.sorted()
        if (orden.isEmpty()) return null
        val indice = Math.ceil(0.95 * orden.size).toInt().coerceIn(1, orden.size) - 1
        return orden[indice]
    }

    /** El peor tiempo total; null si aún no hay muestras con fecha del servidor. */
    @Synchronized
    fun maximo(): Long? = muestras.mapNotNull { it.totalMs }.maxOrNull()

    /** Porcentaje (0–100) de cambios cuyo tiempo total fue menor al plazo; 0 si no hay muestras con fecha. */
    @Synchronized
    fun porcentajeBajoLimite(): Double {
        val totales = muestras.mapNotNull { it.totalMs }
        return if (totales.isEmpty()) 0.0 else 100.0 * totales.count { it < limiteMs } / totales.size
    }

    @Synchronized
    fun reiniciar() = muestras.clear()

    /** Una línea legible, la misma que sale en logcat. */
    @Synchronized
    fun resumen(): String {
        val total = muestras.count { it.totalMs != null }
        if (total == 0) return "Sin muestras con fecha del servidor (n=${muestras.size})"
        return String.format(
            Locale.ROOT,
            "n=%d, p95=%d ms, máximo=%d ms, bajo %d s: %.1f %%",
            total,
            percentil95(),
            maximo(),
            limiteMs / 1000,
            porcentajeBajoLimite(),
        )
    }

    private fun linea(m: MuestraCocina): String = String.format(
        Locale.ROOT,
        "evento=%s llegada=%s actualizacion=%d ms total=%s | %s",
        m.tipo,
        m.llegadaMs?.let { "$it ms" } ?: "n/d",
        m.actualizacionMs,
        m.totalMs?.let { "$it ms" } ?: "n/d",
        resumen(),
    )
}
