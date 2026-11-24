package com.appresso.app.tiemporeal

/**
 * Dirección del WebSocket a partir de la URL del backend (`BuildConfig.API_BASE_URL`), para no
 * configurar el servidor dos veces: `http://10.0.2.2:8080/` → `ws://10.0.2.2:8080/ws/websocket` y
 * `https://…` → `wss://…`. La app usa el WebSocket puro, no SockJS (ver docs/tiempo-real.md).
 */
fun urlTiempoReal(urlBase: String): String {
    val base = urlBase.trim().trimEnd('/')
    val sinEsquema = when {
        base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
        base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
        else -> base
    }
    return "$sinEsquema/ws/websocket"
}
