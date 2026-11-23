package com.appresso.app.tiemporeal

import java.time.DateTimeException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Algo que obliga a la pantalla de cocina a volver a pedir la lista (RF-26).
 *
 * @property fechaServidor cuándo lo publicó el servidor (`fecha` del evento); null si no viene de un
 * evento (por ejemplo, al reconectar) o no se pudo leer.
 * @property recibidoEn cuándo lo recibió este dispositivo. Con los dos se mide el plazo de 3 s
 * (SCRUM-93, [MedidorCocina]).
 */
data class CambioCocina(val tipo: String, val fechaServidor: Instant?, val recibidoEn: Instant) {
    companion object {
        /** Reconexión o salto en la secuencia: pudo perderse algo, hay que releer la lista. */
        const val RESINCRONIZACION = "RESINCRONIZACION"
    }
}

/**
 * Los eventos que cambian lo que ve cocina: `COMANDA_ESTADO_CAMBIADO` (aparece al enviarse, cambia de
 * estado, desaparece al cerrarse) y `COMANDA_MODIFICADA` (productos agregados). `COMANDA_CREADA` se
 * descarta: nace `PENDIENTE` y aún no está en la lista de cocina. Los eventos del catálogo también.
 */
fun Flow<EventoTiempoReal>.cambiosDeCocina(reloj: () -> Instant = Instant::now): Flow<CambioCocina> =
    filter { it.afectaACocina() }.map { CambioCocina(it.tipo, leerFecha(it.fecha), reloj()) }

/** Cada aviso de reconexión o de secuencia saltada, como un cambio sin fecha. */
fun Flow<String>.comoResincronizacion(reloj: () -> Instant = Instant::now): Flow<CambioCocina> =
    map { CambioCocina(CambioCocina.RESINCRONIZACION, null, reloj()) }

internal fun EventoTiempoReal.afectaACocina(): Boolean =
    tipo == "COMANDA_ESTADO_CAMBIADO" || tipo == "COMANDA_MODIFICADA"

private fun leerFecha(texto: String): Instant? = try {
    Instant.parse(texto)
} catch (e: DateTimeException) {
    null
}
