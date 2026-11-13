package com.appresso.app.tiemporeal

/**
 * Cuánto esperar antes de reintentar la conexión (retroceso exponencial, SCRUM-30).
 *
 * Espera `min(maxMs, baseMs × 2^intento)` con una variación aleatoria de ±[jitter]:
 * 1 s, 2 s, 4 s, 8 s, 16 s, 30 s, 30 s… El `intento` empieza en 0 y el cliente lo reinicia
 * cuando logra conectar. La variación evita que todos los dispositivos reconecten a la vez
 * cuando el servidor vuelve.
 */
class PoliticaReintento(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val jitter: Double = 0.2,
    private val azar: () -> Double = { Math.random() },
) {
    fun siguienteEsperaMs(intento: Int): Long {
        val exponencial = baseMs * (1L shl intento.coerceIn(0, 20))
        val tope = minOf(maxMs, exponencial)
        val factor = 1.0 + jitter * (2.0 * azar() - 1.0)
        return (tope * factor).toLong()
    }
}
