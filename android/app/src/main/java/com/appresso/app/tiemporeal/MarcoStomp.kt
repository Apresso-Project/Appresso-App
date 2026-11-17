package com.appresso.app.tiemporeal

/** Un mensaje STOMP: comando, cabeceras y cuerpo (por ejemplo `SUBSCRIBE`, `MESSAGE`). */
data class MarcoStomp(
    val comando: String,
    val cabeceras: Map<String, String> = emptyMap(),
    val cuerpo: String = "",
)

/**
 * Convierte [MarcoStomp] a texto y de vuelta (STOMP 1.2).
 *
 * Spring envía un marco por mensaje de WebSocket. Un mensaje que solo trae saltos de línea es un
 * latido y [decodificar] devuelve `null`.
 */
object CodecoStomp {

    private const val FIN = '\u0000'

    fun codificar(marco: MarcoStomp): String {
        // CONNECT y CONNECTED no escapan las cabeceras (STOMP 1.2).
        val escapar = marco.comando != "CONNECT" && marco.comando != "CONNECTED"
        val sb = StringBuilder(marco.comando).append('\n')
        for ((nombre, valor) in marco.cabeceras) {
            sb.append(if (escapar) escapar(nombre) else nombre)
                .append(':')
                .append(if (escapar) escapar(valor) else valor)
                .append('\n')
        }
        return sb.append('\n').append(marco.cuerpo).append(FIN).toString()
    }

    fun decodificar(texto: String): MarcoStomp? {
        val limpio = texto.trimStart('\n', '\r')
        if (limpio.isEmpty()) return null // latido

        val finCabeceras = limpio.indexOf("\n\n").let { n ->
            val rn = limpio.indexOf("\r\n\r\n")
            if (n == -1) rn else if (rn == -1) n else minOf(n, rn)
        }
        val textoCabeceras = if (finCabeceras == -1) limpio.substringBefore(FIN) else limpio.substring(0, finCabeceras)
        val lineas = textoCabeceras.split('\n').map { it.trimEnd('\r') }
        val comando = lineas.first().trim()
        if (comando.isEmpty()) return null

        val escapa = comando != "CONNECTED" && comando != "CONNECT"
        val cabeceras = LinkedHashMap<String, String>()
        for (linea in lineas.drop(1)) {
            if (linea.isEmpty()) continue
            val i = linea.indexOf(':')
            if (i < 0) continue
            val nombre = linea.substring(0, i)
            val valor = linea.substring(i + 1)
            // Si una cabecera se repite, vale la primera (STOMP 1.2).
            cabeceras.putIfAbsent(
                if (escapa) desescapar(nombre) else nombre,
                if (escapa) desescapar(valor) else valor,
            )
        }

        val cuerpo = if (finCabeceras == -1) {
            ""
        } else {
            val inicio = finCabeceras + if (limpio.startsWith("\r\n", finCabeceras)) 4 else 2
            limpio.substring(inicio).substringBefore(FIN)
        }
        return MarcoStomp(comando, cabeceras, cuerpo)
    }

    private fun escapar(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            ':' -> append("\\c")
            else -> append(c)
        }
    }

    private fun desescapar(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    'c' -> sb.append(':')
                    '\\' -> sb.append('\\')
                    else -> sb.append(c).append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
