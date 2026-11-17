package com.appresso.app.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Situaciones de red que deben avisarse al usuario sin importar la pantalla. */
enum class EventoRed { SIN_CONEXION, PROHIBIDO }

/**
 * Canal global: el interceptor publica y la UI raíz escucha.
 * No lleva textos: los pone la UI desde strings.xml.
 */
class BusEventosRed {
    private val _eventos = MutableSharedFlow<EventoRed>(extraBufferCapacity = 8)
    val eventos: SharedFlow<EventoRed> = _eventos.asSharedFlow()

    fun emitir(evento: EventoRed) {
        _eventos.tryEmit(evento)
    }
}