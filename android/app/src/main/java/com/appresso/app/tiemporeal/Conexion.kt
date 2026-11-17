package com.appresso.app.tiemporeal

/** Un WebSocket abierto. La implementación real usa OkHttp ([FabricaOkHttp]); las pruebas usan una falsa. */
interface Conexion {
    fun enviar(texto: String): Boolean
    fun cerrar()
}

/** Avisos que el WebSocket entrega al cliente STOMP (pueden llegar desde cualquier hilo). */
interface EscuchaConexion {
    fun alAbrir()
    fun alRecibirTexto(texto: String)
    fun alCerrar()
    fun alFallar(causa: Throwable)
}

/** Abre una conexión nueva en cada intento. */
interface FabricaConexion {
    fun abrir(url: String, escucha: EscuchaConexion): Conexion
}

/** Cancela una tarea programada. */
fun interface Cancelable {
    fun cancelar()
}

/** Ejecuta tareas más tarde. Se separa para poder simular el tiempo en las pruebas. */
interface Programador {
    fun ahoraMs(): Long
    fun programar(retrasoMs: Long, tarea: Runnable): Cancelable
}
