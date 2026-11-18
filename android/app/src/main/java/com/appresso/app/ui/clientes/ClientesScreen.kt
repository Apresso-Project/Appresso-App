package com.appresso.app.ui.clientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appresso.app.R
import com.appresso.app.data.clientes.Cliente
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

@Composable
fun ClientesScreen(
    state: ClientesUiState,
    onVolver: () -> Unit,
    onBusquedaCambio: (String) -> Unit,
    onAbrirRegistro: () -> Unit,
    onCerrarRegistro: () -> Unit,
    onRegistrar: (nombre: String, apellido: String, documento: String, celular: String, acepto: Boolean) -> Unit,
    onAbrirEliminacion: (Cliente) -> Unit,
    onCerrarEliminacion: () -> Unit,
    onConfirmarEliminacion: (Cliente, String?) -> Unit,
    onLimpiarMensajes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.mensajeExito, state.errorGeneral) {
        val mensaje = state.mensajeExito ?: state.errorGeneral
        if (mensaje != null) {
            snackbarHostState.showSnackbar(mensaje)
            onLimpiarMensajes()
        }
    }

    if (state.mostrarDialogoRegistro) {
        ClienteRegistroDialog(
            guardando = state.guardando,
            error = state.errorFormulario,
            onDismiss = onCerrarRegistro,
            onRegistrar = onRegistrar,
        )
    }

    state.clienteAEliminar?.let { cliente ->
        EliminarDatosDialog(
            cliente = cliente,
            onDismiss = onCerrarEliminacion,
            onConfirmar = { motivo -> onConfirmarEliminacion(cliente, motivo) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CabeceraClientes(
                onVolver = onVolver,
                onNuevoCliente = onAbrirRegistro,
            )
        },
        containerColor = Cream,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.busqueda,
                onValueChange = onBusquedaCambio,
                label = { Text(stringResource(R.string.clientes_buscar_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            val filtrados = if (state.busqueda.isBlank()) {
                state.clientes
            } else {
                state.clientes.filter {
                    it.documento.contains(state.busqueda.trim(), ignoreCase = true) ||
                        "${it.nombre} ${it.apellido}".contains(state.busqueda.trim(), ignoreCase = true)
                }
            }

            when {
                state.cargando && state.clientes.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Espresso)
                    }
                }

                filtrados.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.busqueda.isNotBlank()) {
                                stringResource(R.string.clientes_sin_resultados)
                            } else {
                                stringResource(R.string.clientes_vacio)
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filtrados, key = { it.id }) { cliente ->
                            TarjetaCliente(
                                cliente = cliente,
                                onSolicitarEliminacion = { onAbrirEliminacion(cliente) },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabeceraClientes(
    onVolver: () -> Unit,
    onNuevoCliente: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Espresso)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onVolver) {
                    Text("←", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(R.string.clientes_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Cream,
                )
            }

            Button(
                onClick = onNuevoCliente,
                colors = ButtonDefaults.buttonColors(containerColor = Caramel),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("+ Nuevo", color = White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TarjetaCliente(
    cliente: Cliente,
    onSolicitarEliminacion: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${cliente.nombre} ${cliente.apellido}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Caramel.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cliente_puntos_formato, cliente.puntos),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Caramel,
                    )
                }
            }

            Text(
                text = "Documento: ${cliente.documento} • Celular: ${cliente.celular}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )

            // Distintivo de cumplimiento legal Ley 1581
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Success.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "✓ Consentimiento Ley 1581 registrado",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Success,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onSolicitarEliminacion,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cliente_solicitar_eliminacion),
                        style = MaterialTheme.typography.labelSmall,
                        color = Error,
                    )
                }
            }
        }
    }
}
