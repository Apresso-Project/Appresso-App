package com.appresso.app.ui.usuarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.usuarios.Usuario
import com.appresso.app.ui.PantallaProvisional
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

/**
 * Lista de usuarios (SCRUM-56). PROVISIONAL: el diseño de Figma de esta pantalla todavía no
 * existe; solo se construyó para poder llegar al formulario (57) y a la desactivación (58).
 * Cuando haya diseño, se rehace esta pantalla y se conserva el ViewModel.
 */
@Composable
fun UsuariosScreen(
    estado: UsuariosUiState,
    onNuevo: () -> Unit,
    onEditar: (Usuario) -> Unit,
    onDesactivar: (Usuario) -> Unit,
    onActivar: (Usuario) -> Unit,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaProvisional(titulo = stringResource(R.string.users_title), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNuevo,
                colors = ButtonDefaults.buttonColors(containerColor = Espresso),
            ) { Text(stringResource(R.string.users_new)) }
            OutlinedButton(onClick = onVolver) { Text(stringResource(R.string.users_back)) }
        }

        estado.errorAccion?.let {
            Text(textoDe(it), color = Error, style = MaterialTheme.typography.bodyMedium)
        }

        when {
            estado.cargando && estado.usuarios.isEmpty() -> {
                val descripcion = stringResource(R.string.users_loading)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = descripcion })
            }

            estado.errorLista != null && estado.usuarios.isEmpty() -> {
                Text(textoDe(estado.errorLista), color = Error, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onReintentar) { Text(stringResource(R.string.users_retry)) }
            }

            estado.usuarios.isEmpty() ->
                Text(stringResource(R.string.users_empty), color = Ink, style = MaterialTheme.typography.bodyLarge)

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(estado.usuarios, key = { it.id }) { usuario ->
                    TarjetaUsuario(
                        usuario = usuario,
                        ocupado = estado.procesandoId == usuario.id,
                        onEditar = { onEditar(usuario) },
                        onDesactivar = { onDesactivar(usuario) },
                        onActivar = { onActivar(usuario) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaUsuario(
    usuario: Usuario,
    ocupado: Boolean,
    onEditar: () -> Unit,
    onDesactivar: () -> Unit,
    onActivar: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(usuario.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(usuario.correo, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(etiquetaDe(usuario.rol), style = MaterialTheme.typography.labelLarge, color = Espresso)
                // El estado va también en texto, no solo en color.
                Text(
                    text = stringResource(if (usuario.activo) R.string.users_status_active else R.string.users_status_inactive),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (usuario.activo) Success else Error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEditar, enabled = !ocupado) { Text(stringResource(R.string.users_edit)) }
                if (usuario.activo) {
                    TextButton(onClick = onDesactivar, enabled = !ocupado) {
                        Text(stringResource(R.string.users_deactivate), color = Error)
                    }
                } else {
                    TextButton(onClick = onActivar, enabled = !ocupado) { Text(stringResource(R.string.users_activate)) }
                }
                if (ocupado) CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
