package com.appresso.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appresso.app.tiemporeal.ConexionTiempoReal
import com.appresso.app.tiemporeal.EstadoConexion
import com.appresso.app.tiemporeal.MedidorLatencia
import com.appresso.app.ui.theme.AppressoTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Pantalla SOLO de depuración para probar el tiempo real a mano (SCRUM-31 y SCRUM-32):
 * conectar, ver el estado y los reintentos, ver los eventos y medir la latencia.
 * No forma parte de la app final (vive en src/debug).
 */
class DiagnosticoTiempoRealActivity : ComponentActivity() {

    private var conexion: ConexionTiempoReal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppressoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Pantalla(Modifier.padding(padding).padding(16.dp))
                }
            }
        }
    }

    override fun onDestroy() {
        conexion?.detener()
        super.onDestroy()
    }

    @Composable
    private fun Pantalla(modifier: Modifier) {
        // 10.0.2.2 es el PC visto desde el emulador. En un celular real: la IP del PC en la red Wi-Fi.
        var url by remember { mutableStateOf("ws://10.0.2.2:8080/ws/websocket") }
        var activa by remember { mutableStateOf<ConexionTiempoReal?>(null) }
        val medidor = remember { MedidorLatencia() }
        val lineas = remember { mutableStateListOf<String>() }
        var total by remember { mutableIntStateOf(0) }
        var resumen by remember { mutableStateOf("Sin datos") }

        val info by (activa?.estado ?: ConexionTiempoReal.SIN_CONEXION).collectAsState()

        LaunchedEffect(activa) {
            val c = activa ?: return@LaunchedEffect
            coroutineScope {
                launch {
                    c.eventos.collect { e ->
                        val ms = medidor.registrarEvento(e)
                        total++
                        lineas.add(0, "#${e.secuencia} ${e.tipo}  (${ms ?: "?"} ms)")
                        if (lineas.size > 40) lineas.removeAt(lineas.size - 1)
                        val p95 = medidor.percentil95()
                        resumen = "p95 = $p95 ms · ${"%.1f".format(medidor.porcentajeBajoLimite())} % bajo 3 s " +
                            "(${medidor.cantidad()} muestras)"
                    }
                }
                launch {
                    c.resincronizar.collect { motivo -> lineas.add(0, "↻ RESINCRONIZAR: $motivo") }
                }
            }
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Dirección del servidor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    conexion?.detener()
                    conexion = ConexionTiempoReal(applicationContext, url).also { it.iniciar() }
                    activa = conexion
                }) { Text("Conectar") }
                OutlinedButton(onClick = {
                    conexion?.detener()
                }) { Text("Desconectar") }
                OutlinedButton(onClick = {
                    medidor.reiniciar()
                    lineas.clear()
                    total = 0
                    resumen = "Sin datos"
                }) { Text("Limpiar") }
            }
            Text(
                when (info.estado) {
                    EstadoConexion.CONECTADO -> "🟢 Conectado"
                    EstadoConexion.CONECTANDO -> "🟡 Conectando…"
                    EstadoConexion.ESPERANDO_REINTENTO ->
                        "🔴 Sin conexión · reintento en ${(info.esperaMs ?: 0) / 1000.0} s"
                    EstadoConexion.DESCONECTADO -> "⚪ Desconectado"
                },
            )
            info.motivo?.let { Text("Motivo: $it") }
            Text("Eventos recibidos: $total")
            Text(resumen)
            Text("La latencia compara la hora del servidor con la del celular: ambos relojes deben estar en hora automática.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(lineas) { Text(it) }
            }
        }
    }
}
