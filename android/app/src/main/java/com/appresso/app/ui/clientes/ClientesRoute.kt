package com.appresso.app.ui.clientes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appresso.app.R

/** Conecta [ClientesScreen] con su [ClientesViewModel] y traduce los errores a texto. */
@Composable
fun ClientesRoute(
    viewModel: ClientesViewModel,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    ClientesScreen(
        estado = estado,
        error = estado.error?.let { textoDe(it) },
        onDocumentoChange = viewModel::onDocumentoChange,
        onBuscar = viewModel::buscar,
        onVolver = onVolver,
        modifier = modifier,
    )
}

@Composable
private fun textoDe(error: ErrorBusqueda): String = when (error) {
    ErrorBusqueda.DocumentoInvalido -> stringResource(R.string.registro_error_documento)
    is ErrorBusqueda.SinPermiso -> error.mensaje ?: stringResource(R.string.error_prohibido)
    ErrorBusqueda.SinConexion -> stringResource(R.string.login_error_no_connection)
    ErrorBusqueda.ErrorDelServidor -> stringResource(R.string.login_error_server)
}
