package com.appresso.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.White

/**
 * Campo de texto con la etiqueta encima, con el estilo del inicio de sesión. Lo usan también el
 * registro del cliente y la recuperación de contraseña.
 *
 * @param error si no es null, el campo se marca en rojo y el texto se muestra debajo.
 */
@Composable
internal fun CampoTexto(
    etiqueta: String,
    placeholder: String,
    valor: String,
    onValorChange: (String) -> Unit,
    teclado: KeyboardOptions,
    modifier: Modifier = Modifier,
    icono: Int? = null,
    soloLectura: Boolean = false,
    error: String? = null,
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
            isError = error != null,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Muted)
            },
            leadingIcon = icono?.let {
                { Icon(painterResource(it), contentDescription = null, tint = Color.Unspecified) }
            },
            trailingIcon = trailing,
            supportingText = error?.let { { Text(it) } },
            visualTransformation = transformacion,
            keyboardOptions = teclado,
            keyboardActions = acciones,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                errorContainerColor = White,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                errorTextColor = Ink,
                focusedBorderColor = Caramel,
                unfocusedBorderColor = Espresso.copy(alpha = 0.2f),
                cursorColor = Caramel,
            ),
        )
    }
}
