package com.appresso.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Cream
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.White

/**
 * Pantalla de inicio de sesión con correo y contraseña. No registra cuentas nuevas.
 *
 * No guarda lo que se escribe ni decide nada: recibe el estado ([correo], [contrasena],
 * [errorMessage], [isLoading]) y avisa de lo que hace el usuario. Quien la usa ([LoginRoute],
 * con su ViewModel) es el dueño del estado, así la contraseña no queda en el estado guardado de
 * Android y lo escrito sobrevive a rotar la pantalla o a un fallo de conexión.
 */
@Composable
fun LoginScreen(
    correo: String,
    contrasena: String,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onEnviar: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isLoading: Boolean = false,
) {
    var mostrar by rememberSaveable { mutableStateOf(false) }
    val camposLlenos = correo.isNotBlank() && contrasena.isNotBlank()
    val enviar = { if (camposLlenos && !isLoading) onEnviar() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Espresso)
            .clipToBounds(),
    ) {
        // Círculos decorativos del encabezado
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-70).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Caramel.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = 40.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Caramel.copy(alpha = 0.10f)),
        )

        Column(Modifier.fillMaxSize()) {
            // Encabezado
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge,
                    color = Cream,
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .widthIn(max = 256.dp),
                )
            }

            // Formulario
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Cream),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Espresso,
                    )

                    CampoTexto(
                        etiqueta = stringResource(R.string.login_email_label),
                        placeholder = stringResource(R.string.login_email_placeholder),
                        valor = correo,
                        onValorChange = onCorreoChange,
                        icono = R.drawable.ic_mail,
                        soloLectura = isLoading,
                        teclado = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    CampoTexto(
                        etiqueta = stringResource(R.string.login_password_label),
                        placeholder = stringResource(R.string.login_password_placeholder),
                        valor = contrasena,
                        onValorChange = onContrasenaChange,
                        icono = R.drawable.ic_lock,
                        soloLectura = isLoading,
                        transformacion = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
                        teclado = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        acciones = KeyboardActions(onDone = { enviar() }),
                        trailing = {
                            Text(
                                text = stringResource(
                                    if (mostrar) R.string.login_hide_password else R.string.login_show_password
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Caramel,
                                modifier = Modifier
                                    .clickable(role = Role.Button) { mostrar = !mostrar }
                                    .minimumInteractiveComponentSize()
                                    .padding(horizontal = 4.dp),
                            )
                        },
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            // Los lectores de pantalla (TalkBack) anuncian el error al aparecer.
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    Button(
                        onClick = enviar,
                        enabled = camposLlenos,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Espresso,
                            contentColor = Cream,
                            disabledContainerColor = Espresso.copy(alpha = 0.4f),
                            disabledContentColor = Cream.copy(alpha = 0.8f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                    ) {
                        if (isLoading) {
                            // Sustituye al texto sin cambiar el tamaño del botón.
                            val descripcion = stringResource(R.string.login_loading)
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics { contentDescription = descripcion },
                                color = Cream,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.login_submit),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoTexto(
    etiqueta: String,
    placeholder: String,
    valor: String,
    onValorChange: (String) -> Unit,
    icono: Int,
    teclado: KeyboardOptions,
    modifier: Modifier = Modifier,
    soloLectura: Boolean = false,
    transformacion: VisualTransformation = VisualTransformation.None,
    acciones: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
        )
        OutlinedTextField(
            value = valor,
            onValueChange = onValorChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // Mientras se espera la respuesta no se puede editar, pero el campo se ve igual.
            readOnly = soloLectura,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Muted)
            },
            leadingIcon = {
                Icon(painterResource(icono), contentDescription = null, tint = Color.Unspecified)
            },
            trailingIcon = trailing,
            visualTransformation = transformacion,
            keyboardOptions = teclado,
            keyboardActions = acciones,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Caramel,
                unfocusedBorderColor = Espresso.copy(alpha = 0.2f),
                cursorColor = Caramel,
            ),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginScreenPreview() {
    AppressoTheme {
        LoginScreen(
            correo = "", contrasena = "",
            onCorreoChange = {}, onContrasenaChange = {}, onEnviar = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginScreenErrorPreview() {
    AppressoTheme {
        LoginScreen(
            correo = "ana@appresso.test", contrasena = "secreta",
            onCorreoChange = {}, onContrasenaChange = {}, onEnviar = {},
            errorMessage = "Credenciales inválidas",
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginScreenLoadingPreview() {
    AppressoTheme {
        LoginScreen(
            correo = "ana@appresso.test", contrasena = "secreta",
            onCorreoChange = {}, onContrasenaChange = {}, onEnviar = {},
            isLoading = true,
        )
    }
}
