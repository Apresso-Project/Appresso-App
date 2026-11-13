package com.appresso.app.tiemporeal

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Abre WebSockets reales con OkHttp. Los latidos los maneja STOMP, no OkHttp. */
class FabricaOkHttp(
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // sin límite: la caída la detectan los latidos STOMP
        .build(),
) : FabricaConexion {

    override fun abrir(url: String, escucha: EscuchaConexion): Conexion {
        val socket = cliente.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = escucha.alAbrir()

                override fun onMessage(webSocket: WebSocket, text: String) = escucha.alRecibirTexto(text)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                    escucha.alCerrar()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = escucha.alCerrar()

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = escucha.alFallar(t)
            },
        )
        return object : Conexion {
            override fun enviar(texto: String): Boolean = socket.send(texto)

            override fun cerrar() {
                socket.close(1000, null)
            }
        }
    }
}
