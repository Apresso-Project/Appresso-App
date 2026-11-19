package com.appresso.app.ui.usuarios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.data.usuarios.Usuario
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Error
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

/**
 * Lista de usuarios (SCRUM-56). Misma cabecera espresso y cuerpo crema que el login.
 * El estado y las acciones vienen de [UsuariosViewModel]; aquí solo se dibuja.
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
    /** Id de quien inició sesión: su cuenta no ofrece desactivarse. `null` si no se conoce. */
    miId: Long? = null,
) {
    MarcoUsuarios(titulo = stringResource(R.string.users_title), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNuevo,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Espresso, contentColor = Cream),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.users_new), style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = onVolver,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Espresso),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.users_back), style = MaterialTheme.typography.titleMedium) }
        }

        estado.errorAccion?.let { MensajeConBorde(textoDe(it)) }

        when {
            estado.cargando && estado.usuarios.isEmpty() -> Centrado {
                val descripcion = stringResource(R.string.users_loading)
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = descripcion },
                )
            }

            estado.errorLista != null && estado.usuarios.isEmpty() -> Centrado {
                MensajeConBorde(textoDe(estado.errorLista))
                OutlinedButton(
                    onClick = onReintentar,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Espresso),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = Espresso),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.users_retry), style = MaterialTheme.typography.titleMedium) }
            }

            estado.usuarios.isEmpty() -> Centrado {
                Text(
                    stringResource(R.string.users_empty),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(estado.usuarios, key = { it.id }) { usuario ->
                    TarjetaUsuario(
                        usuario = usuario,
                        ocupado = estado.procesandoId == usuario.id,
                        esPropia = usuario.id == miId,
                        onEditar = { onEditar(usuario) },
                        onDesactivar = { onDesactivar(usuario) },
                        onActivar = { onActivar(usuario) },
                    )
                }
            }
        }
    }
}

/** Cabecera espresso con el título y cuerpo crema de esquinas superiores redondeadas. */
@Composable
private fun MarcoUsuarios(
    titulo: String,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier
        .fillMaxSize()
        .background(Espresso)) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = Cream.copy(alpha = 0.7f),
            )
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineMedium,
                color = Cream,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Cream)
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenido,
        )
    }
}

/** Contenido centrado en el espacio que queda bajo los botones (cargando, vacío y error). */
@Composable
private fun ColumnScope.Centrado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = contenido,
    )
}

/** Mensaje de error en tarjeta blanca con borde rojo; el texto va en color de tinta para leerse bien. */
@Composable
private fun MensajeConBorde(texto: String) {
    Text(
        text = texto,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.5.dp, Error, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun TarjetaUsuario(
    usuario: Usuario,
    ocupado: Boolean,
    esPropia: Boolean,
    onEditar: () -> Unit,
    onDesactivar: () -> Unit,
    onActivar: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, forma, ambientColor = Ink.copy(alpha = 0.08f), spotColor = Ink.copy(alpha = 0.08f))
            .clip(forma)
            .background(White),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(usuario.nombre, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(usuario.correo, style = MaterialTheme.typography.bodyMedium, color = Muted)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = etiquetaDe(usuario.rol),
                    style = MaterialTheme.typography.labelSmall,
                    color = Espresso,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Cream)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (esPropia) {
                    Text(
                        text = stringResource(R.string.users_own_account),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier
                            .border(1.dp, Muted, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                // El estado va también en texto, no solo en color.
                val color = if (usuario.activo) Success else Error
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Text(
                        text = stringResource(if (usuario.activo) R.string.users_status_active else R.string.users_status_inactive),
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cream),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            AccionTexto(R.string.users_edit, Espresso, !ocupado, onEditar)
            if (usuario.activo && esPropia) {
                // Un administrador no puede desactivar su propia cuenta: no se ofrece.
            } else if (usuario.activo) {
                AccionTexto(R.string.users_deactivate, Error, !ocupado, onDesactivar)
            } else {
                AccionTexto(R.string.users_activate, Success, !ocupado, onActivar)
            }
            if (ocupado) {
                CircularProgressIndicator(
                    color = Caramel,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun AccionTexto(texto: Int, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = habilitado,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.4f)),
        modifier = Modifier.heightIn(min = 44.dp),
    ) { Text(stringResource(texto), style = MaterialTheme.typography.titleMedium) }
}
