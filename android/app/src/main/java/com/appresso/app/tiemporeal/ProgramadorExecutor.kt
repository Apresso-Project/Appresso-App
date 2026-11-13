package com.appresso.app.tiemporeal

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** [Programador] real: un solo hilo de fondo que no impide cerrar la app. */
class ProgramadorExecutor(
    private val ejecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "tiempo-real-programador").apply { isDaemon = true }
    },
) : Programador {

    override fun ahoraMs(): Long = System.nanoTime() / 1_000_000

    override fun programar(retrasoMs: Long, tarea: Runnable): Cancelable {
        val futuro = ejecutor.schedule(tarea, retrasoMs, TimeUnit.MILLISECONDS)
        return Cancelable { futuro.cancel(false) }
    }
}
