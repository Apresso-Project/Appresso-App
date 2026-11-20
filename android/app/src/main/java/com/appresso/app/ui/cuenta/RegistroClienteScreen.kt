package com.appresso.app.ui.cuenta

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appresso.app.R
import com.appresso.app.ui.login.CampoTexto
import com.appresso.app.ui.theme.AppressoTheme
import com.appresso.app.ui.theme.Caramel
import com.appresso.app.ui.theme.Espresso
import com.appresso.app.ui.theme.Ink
import com.appresso.app.ui.theme.Muted
import com.appresso.app.ui.theme.Success
import com.appresso.app.ui.theme.White

/**
 * Formulario con el que el cliente crea su cuenta (RF-50). Solo dibuja: el estado y las decisiones
 * son de [RegistroClienteViewModel]. [errorDe] da el texto de error de cada campo y [errorGeneral]
 * el mensaje bajo el formulario; ambos ya traducidos por [RegistroClienteRoute].
 */
@Composable
fun RegistroClienteScreen(
    estado: RegistroUiState,
    errorDe: (CampoRegistro) -> String?,
    errorGeneral: String?,
    onNombreChange: (String) -> Unit,
    onApellidoChange: (String) -> Unit,
    onDocumentoChange: (String) -> Unit,
    onCelularChange: (String) -> Unit,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onAceptoChange: (Boolean) -> Unit,
    onEnviar: () -> Unit,
    onVolver: () -> Unit,
    onIrAlLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MarcoCuenta(
        titulo = stringResource(R.string.registro_titulo),
        subtitulo = stringResource(R.string.registro_subtitulo),
        onVolver = onVolver,
        volverHabilitado = !estado.cargando,
        modifier = modifier,
    ) {
        if (estado.registrado) {
            RegistroExitoso(mensaje = estado.mensajeExito, onIrAlLogin = onIrAlLogin)
        } else {
            Formulario(
                estado = estado,
                errorDe = errorDe,
                errorGeneral = errorGeneral,
                onNombreChange = onNombreChange,
                onApellidoChange = onApellidoChange,
                onDocumentoChange = onDocumentoChange,
                onCelularChange = onCelularChange,
                onCorreoChange = onCorreoChange,
                onContrasenaChange = onContrasenaChange,
                onAceptoChange = onAceptoChange,
                onEnviar = onEnviar,
            )
        }
    }
}

@Composable
private fun Formulario(
    estado: RegistroUiState,
    errorDe: (CampoRegistro) -> String?,
    errorGeneral: String?,
    onNombreChange: (String) -> Unit,
    onApellidoChange: (String) -> Unit,
    onDocumentoChange: (String) -> Unit,
    onCelularChange: (String) -> Unit,
    onCorreoChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onAceptoChange: (Boolean) -> Unit,
    onEnviar: () -> Unit,
) {
    var mostrar by rememberSaveable { mutableStateOf(false) }
    val siguiente = KeyboardOptions(imeAction = ImeAction.Next)

    CampoTexto(
        etiqueta = stringResource(R.string.registro_nombre),
        placeholder = stringResource(R.string.registro_nombre_placeholder),
        valor = estado.nombre,
        onValorChange = onNombreChange,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.NOMBRE),
        teclado = siguiente.copy(capitalization = KeyboardCapitalization.Words),
    )
    CampoTexto(
        etiqueta = stringResource(R.string.registro_apellido),
        placeholder = stringResource(R.string.registro_apellido_placeholder),
        valor = estado.apellido,
        onValorChange = onApellidoChange,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.APELLIDO),
        teclado = siguiente.copy(capitalization = KeyboardCapitalization.Words),
    )
    CampoTexto(
        etiqueta = stringResource(R.string.registro_documento),
        placeholder = stringResource(R.string.registro_documento_placeholder),
        valor = estado.documento,
        onValorChange = onDocumentoChange,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.DOCUMENTO),
        // Texto y no número: la cédula de extranjería y el pasaporte pueden tener letras.
        teclado = siguiente.copy(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters),
    )
    CampoTexto(
        etiqueta = stringResource(R.string.registro_celular),
        placeholder = stringResource(R.string.registro_celular_placeholder),
        valor = estado.celular,
        onValorChange = onCelularChange,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.CELULAR),
        teclado = siguiente.copy(keyboardType = KeyboardType.Phone),
    )
    CampoTexto(
        etiqueta = stringResource(R.string.registro_correo),
        placeholder = stringResource(R.string.registro_correo_placeholder),
        valor = estado.correo,
        onValorChange = onCorreoChange,
        icono = R.drawable.ic_mail,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.CORREO),
        teclado = siguiente.copy(keyboardType = KeyboardType.Email),
    )
    Text(
        text = stringResource(R.string.registro_correo_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
    )
    CampoTexto(
        etiqueta = stringResource(R.string.registro_contrasena),
        placeholder = stringResource(R.string.registro_contrasena_placeholder),
        valor = estado.contrasena,
        onValorChange = onContrasenaChange,
        icono = R.drawable.ic_lock,
        soloLectura = estado.cargando,
        error = errorDe(CampoRegistro.CONTRASENA),
        transformacion = if (mostrar) VisualTransformation.None else PasswordVisualTransformation(),
        teclado = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailing = {
            Text(
                text = stringResource(if (mostrar) R.string.login_hide_password else R.string.login_show_password),
                style = MaterialTheme.typography.labelSmall,
                color = Caramel,
                modifier = Modifier
                    .clickable(role = Role.Button) { mostrar = !mostrar }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 4.dp),
            )
        },
    )

    Autorizacion(
        acepto = estado.acepto,
        onAceptoChange = onAceptoChange,
        habilitado = !estado.cargando,
        error = errorDe(CampoRegistro.ACEPTO),
    )

    if (errorGeneral != null) MensajeError(errorGeneral)

    BotonCuenta(
        texto = stringResource(R.string.registro_enviar),
        textoCargando = stringResource(R.string.registro_enviando),
        onClick = onEnviar,
        cargando = estado.cargando,
    )
}

/** El texto de la autorización (Ley 1581 de 2012) y la casilla para aceptarla. */
@Composable
private fun Autorizacion(acepto: Boolean, onAceptoChange: (Boolean) -> Unit, habilitado: Boolean, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.cliente_autorizacion_titulo),
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.cliente_autorizacion_texto),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .border(1.dp, Espresso.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = acepto, enabled = habilitado, role = Role.Checkbox, onValueChange = onAceptoChange),
        ) {
            Checkbox(
                checked = acepto,
                onCheckedChange = null, // la fila entera es el control
                enabled = habilitado,
                colors = CheckboxDefaults.colors(checkedColor = Espresso, uncheckedColor = Espresso, checkmarkColor = White),
            )
            Text(
                text = stringResource(R.string.cliente_checkbox_aceptacion),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (error != null) MensajeError(error)
    }
}

@Composable
private fun RegistroExitoso(mensaje: String?, onIrAlLogin: () -> Unit) {
    Text(
        text = mensaje ?: stringResource(R.string.registro_exito),
        style = MaterialTheme.typography.titleMedium,
        color = Success,
    )
    Text(
        text = stringResource(R.string.registro_exito_ingreso),
        style = MaterialTheme.typography.bodyMedium,
        color = Ink,
    )
    BotonCuenta(
        texto = stringResource(R.string.cuenta_ir_login),
        textoCargando = stringResource(R.string.cuenta_ir_login),
        onClick = onIrAlLogin,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1200)
@Composable
private fun RegistroClienteScreenPreview() {
    AppressoTheme {
        RegistroClienteScreen(
            estado = RegistroUiState(nombre = "Ana", documento = "123"),
            errorDe = { if (it == CampoRegistro.DOCUMENTO) "El documento debe tener entre 5 y 20 letras o números." else null },
            errorGeneral = "Completa todos los datos y acepta el tratamiento de datos personales.",
            onNombreChange = {}, onApellidoChange = {}, onDocumentoChange = {}, onCelularChange = {},
            onCorreoChange = {}, onContrasenaChange = {}, onAceptoChange = {}, onEnviar = {}, onVolver = {},
            onIrAlLogin = {},
        )
    }
}
