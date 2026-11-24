package com.appresso.app.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.R
import com.appresso.app.data.catalogo.InventarioRepository
import com.appresso.app.data.catalogo.MovimientoInventario
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.catalogo.TipoAjuste
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AjusteUi(
    val producto: Producto,
    val tipo: TipoAjuste = TipoAjuste.ENTRADA_MANUAL,
    val cantidad: String = "",
    val concepto: String = "",
    val guardando: Boolean = false,
    val error: Int? = null,
    val mensajeServidor: String? = null,
)

data class HistorialInventarioUi(
    val producto: String = "",
    val desde: String = "",
    val hasta: String = "",
    val movimientos: List<MovimientoInventario> = emptyList(),
    val cargando: Boolean = false,
    val error: Int? = null,
)

class InventarioViewModel(private val repositorio: InventarioRepository) : ViewModel() {
    private val _ajuste = MutableStateFlow<AjusteUi?>(null)
    val ajuste = _ajuste.asStateFlow()

    private val _historial = MutableStateFlow(HistorialInventarioUi())
    val historial = _historial.asStateFlow()

    fun abrirAjuste(producto: Producto) {
        if (_ajuste.value?.guardando != true) _ajuste.value = AjusteUi(producto)
    }

    fun cerrarAjuste() {
        if (_ajuste.value?.guardando != true) _ajuste.value = null
    }

    fun editarAjuste(cambio: (AjusteUi) -> AjusteUi) {
        _ajuste.update { actual ->
            actual?.let {
                if (it.guardando) it else cambio(it).copy(error = null, mensajeServidor = null)
            }
        }
    }

    fun guardarAjuste(alGuardar: (Producto) -> Unit) {
        val formulario = _ajuste.value ?: return
        if (formulario.guardando) return
        val cantidad = formulario.cantidad.trim().toIntOrNull()
        val concepto = formulario.concepto.trim()
        if (cantidad == null || cantidad <= 0) {
            _ajuste.value = formulario.copy(error = R.string.inv_cantidad_invalida)
            return
        }
        if (concepto.isEmpty() || concepto.length > 255) {
            _ajuste.value = formulario.copy(error = R.string.inv_concepto_invalido)
            return
        }
        _ajuste.value = formulario.copy(guardando = true, error = null, mensajeServidor = null)
        viewModelScope.launch {
            when (val r = repositorio.ajustar(formulario.producto.id, formulario.tipo, cantidad, concepto)) {
                is ResultadoCatalogo.Exito -> {
                    _ajuste.value = null
                    alGuardar(r.valor)
                }
                else -> {
                    val mensaje = when (r) {
                        is ResultadoCatalogo.DatosInvalidos -> r.campos.values.firstOrNull() ?: r.mensaje
                        is ResultadoCatalogo.Conflicto -> r.mensaje
                        else -> null
                    }
                    _ajuste.value = formulario.copy(error = r.errorInventario(), mensajeServidor = mensaje)
                }
            }
        }
    }

    fun editarHistorial(cambio: (HistorialInventarioUi) -> HistorialInventarioUi) {
        _historial.update { if (it.cargando) it else cambio(it).copy(error = null) }
    }

    fun cargarHistorial() {
        val filtro = _historial.value
        if (filtro.cargando) return
        val desde: LocalDate?
        val hasta: LocalDate?
        try {
            desde = filtro.desde.trim().takeIf(String::isNotEmpty)?.let(LocalDate::parse)
            hasta = filtro.hasta.trim().takeIf(String::isNotEmpty)?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            _historial.value = filtro.copy(error = R.string.inv_fecha_invalida)
            return
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            _historial.value = filtro.copy(error = R.string.inv_rango_invalido)
            return
        }
        _historial.value = filtro.copy(cargando = true, error = null)
        viewModelScope.launch {
            _historial.value = when (val r = repositorio.historial(filtro.producto, desde, hasta)) {
                is ResultadoCatalogo.Exito -> filtro.copy(movimientos = r.valor, error = null)
                else -> filtro.copy(error = r.errorInventario())
            }
        }
    }

    companion object {
        fun factory(repositorio: InventarioRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { InventarioViewModel(repositorio) } }
    }
}

private fun ResultadoCatalogo<*>.errorInventario(): Int = when (this) {
    ResultadoCatalogo.SinConexion -> R.string.login_error_no_connection
    is ResultadoCatalogo.SinPermiso -> R.string.error_prohibido
    ResultadoCatalogo.NoEncontrado -> R.string.inv_producto_no_encontrado
    else -> R.string.inv_error
}
