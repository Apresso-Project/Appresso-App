package com.appresso.app.ui.comandas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.comandas.CalculadoraComanda
import com.appresso.app.data.comandas.Comanda
import com.appresso.app.data.comandas.ComandasRepository
import com.appresso.app.data.comandas.ItemPedido
import com.appresso.app.data.comandas.NuevaComanda
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuProducto
import com.appresso.app.data.menu.MenuRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasoComanda { ARMAR, RESUMEN, DETALLE }

internal const val MAX_OBSERVACIONES_COMANDA = 100

/** Por qué no se puede seguir; el texto se arma en la pantalla. */
sealed interface ErrorComanda {
    data object Vacia : ErrorComanda
    data object SinConexion : ErrorComanda
    data object ErrorDelServidor : ErrorComanda
    data object NoEncontrada : ErrorComanda
    data class SinPermiso(val mensajeServidor: String?) : ErrorComanda

    /** El servidor rechazó la comanda con su mensaje (mesa ocupada, sin stock, descuento no autorizado…). */
    data class DelServidor(val mensaje: String) : ErrorComanda
}

/** Un producto de la comanda que se está armando, con el subtotal calculado (cantidad × precio vigente). */
data class LineaBorrador(
    val producto: MenuProducto,
    val cantidad: Int,
    val observaciones: String? = null,
) {
    val subtotal: BigDecimal get() = CalculadoraComanda.subtotalLinea(producto.precio, cantidad)
}

data class ComandaUiState(
    val mesaId: Long,
    val mesaNombre: String,
    val categorias: List<MenuCategoria> = emptyList(),
    val cargandoMenu: Boolean = false,
    val errorMenu: ErrorComanda? = null,
    /** null = todas las categorías. */
    val filtroCategoriaId: Long? = null,
    /** Unidades por producto, en el orden en que se agregaron. */
    val cantidades: Map<Long, Int> = emptyMap(),
    val observaciones: Map<Long, String> = emptyMap(),
    val paso: PasoComanda = PasoComanda.ARMAR,
    val guardando: Boolean = false,
    val cargandoDetalle: Boolean = false,
    val error: ErrorComanda? = null,
    val guardada: Comanda? = null,
) {
    private val productos: Map<Long, MenuProducto> by lazy {
        categorias.flatMap { it.productos }.associateBy { it.id }
    }

    val visibles: List<MenuCategoria>
        get() = if (filtroCategoriaId == null) categorias else categorias.filter { it.id == filtroCategoriaId }

    val lineas: List<LineaBorrador> by lazy {
        cantidades.mapNotNull { (id, cantidad) ->
            productos[id]?.let { LineaBorrador(it, cantidad, observaciones[id]?.trim()?.takeIf(String::isNotEmpty)) }
        }
    }

    val importes: CalculadoraComanda.Importes by lazy {
        CalculadoraComanda.calcular(lineas.map { CalculadoraComanda.Linea(it.producto.precio, it.cantidad) })
    }

    val unidades: Int get() = lineas.sumOf { it.cantidad }
    val vacia: Boolean get() = lineas.isEmpty()
    val editable: Boolean get() = paso != PasoComanda.DETALLE && !guardando
    val puedeGuardar: Boolean
        get() = paso == PasoComanda.RESUMEN && !vacia && !guardando && !cargandoMenu && errorMenu == null

    fun cantidadDe(productoId: Long): Int = cantidades[productoId] ?: 0
}

/**
 * Arma y guarda una comanda pendiente o consulta la comanda activa de una mesa. El backend compara
 * el total revisado antes de guardar. El envío a cocina corresponde a 4.2.1.
 */
class ComandaViewModel(
    mesaId: Long,
    mesaNombre: String,
    private val menu: MenuRepository,
    private val comandas: ComandasRepository,
    private val comandaActivaId: Long? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ComandaUiState(
            mesaId = mesaId,
            mesaNombre = mesaNombre,
            paso = if (comandaActivaId == null) PasoComanda.ARMAR else PasoComanda.DETALLE,
        ),
    )
    val estado: StateFlow<ComandaUiState> = _estado.asStateFlow()

    init {
        if (comandaActivaId == null) cargarMenu() else cargarDetalle()
    }

    fun cargarMenu() {
        if (!_estado.value.editable || _estado.value.cargandoMenu) return
        _estado.update { it.copy(cargandoMenu = true, errorMenu = null) }
        viewModelScope.launch {
            when (val r = menu.consultar()) {
                is ResultadoCatalogo.Exito -> _estado.update { estado ->
                    val ids = r.valor.flatMap { it.productos }.associateBy { it.id }
                    val cantidades = estado.cantidades
                        .mapNotNull { (id, n) -> ids[id]?.let { id to minOf(n, it.existencias) } }
                        .filter { it.second > 0 }
                        .toMap()
                    estado.copy(
                        categorias = r.valor,
                        cargandoMenu = false,
                        // Lo que ya no está en el menú (agotado o desactivado) sale de la comanda, y
                        // ninguna cantidad supera las existencias actuales.
                        cantidades = cantidades,
                        observaciones = estado.observaciones.filterKeys { it in cantidades },
                        filtroCategoriaId = estado.filtroCategoriaId?.takeIf { id -> r.valor.any { it.id == id } },
                    )
                }

                else -> _estado.update { it.copy(cargandoMenu = false, errorMenu = r.aErrorComanda()) }
            }
        }
    }

    fun cargarDetalle() {
        val id = comandaActivaId ?: return
        if (_estado.value.cargandoDetalle) return
        _estado.update { it.copy(cargandoDetalle = true, error = null) }
        viewModelScope.launch {
            when (val r = comandas.consultarComanda(id)) {
                is ResultadoCatalogo.Exito -> _estado.update { it.copy(cargandoDetalle = false, guardada = r.valor) }
                else -> _estado.update { it.copy(cargandoDetalle = false, error = r.aErrorComanda()) }
            }
        }
    }

    fun filtrarPorCategoria(id: Long?) = _estado.update { it.copy(filtroCategoriaId = id) }

    fun agregar(producto: MenuProducto) = _estado.update { estado ->
        val actual = estado.cantidadDe(producto.id)
        if (!estado.editable || actual >= producto.existencias) {
            estado
        } else {
            estado.copy(cantidades = estado.cantidades + (producto.id to actual + 1), error = null)
        }
    }

    fun quitar(producto: MenuProducto) = _estado.update { estado ->
        val actual = estado.cantidadDe(producto.id)
        when {
            !estado.editable || actual == 0 -> estado
            actual == 1 -> estado.copy(
                cantidades = estado.cantidades - producto.id,
                observaciones = estado.observaciones - producto.id,
            )
            else -> estado.copy(cantidades = estado.cantidades + (producto.id to actual - 1))
        }
    }

    fun cambiarObservaciones(productoId: Long, texto: String) = _estado.update { estado ->
        if (!estado.editable || estado.cantidadDe(productoId) == 0) estado
        else estado.copy(observaciones = estado.observaciones + (productoId to texto.take(MAX_OBSERVACIONES_COMANDA)))
    }

    /** Pasa al resumen previo; con la comanda vacía no avanza y avisa. */
    fun verResumen() = _estado.update {
        when {
            !it.editable -> it
            it.vacia -> it.copy(error = ErrorComanda.Vacia)
            else -> it.copy(paso = PasoComanda.RESUMEN, error = null)
        }
    }

    /** Vuelve a editar desde el resumen, si la comanda todavía no se creó en el servidor. */
    fun seguirEditando() = _estado.update {
        if (it.editable) it.copy(paso = PasoComanda.ARMAR, error = null) else it
    }

    fun guardar() {
        val actual = _estado.value
        if (actual.guardando || actual.paso != PasoComanda.RESUMEN) return
        if (actual.vacia) {
            _estado.update { it.copy(error = ErrorComanda.Vacia) }
            return
        }
        if (!actual.puedeGuardar) return
        _estado.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            when (val r = comandas.crearComanda(actual.aNuevaComanda())) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(guardando = false, paso = PasoComanda.DETALLE, guardada = r.valor)
                }

                else -> {
                    _estado.update { it.copy(guardando = false, error = r.aErrorComanda()) }
                    if (r is ResultadoCatalogo.Conflicto && ("totalEsperado" in r.campos || "items" in r.campos)) {
                        cargarMenu()
                    }
                }
            }
        }
    }

    private fun ComandaUiState.aNuevaComanda() = NuevaComanda(
        mesaId = mesaId,
        items = lineas.map { ItemPedido(it.producto.id, it.cantidad, it.observaciones) },
        totalEsperado = importes.total,
    )

    companion object {
        fun factory(
            mesaId: Long,
            mesaNombre: String,
            menu: MenuRepository,
            comandas: ComandasRepository,
            comandaActivaId: Long?,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { ComandaViewModel(mesaId, mesaNombre, menu, comandas, comandaActivaId) } }
    }
}

/** 401 no muestra nada: el cliente HTTP ya cerró la sesión. Los 400/409 traen el mensaje del servidor. */
private fun ResultadoCatalogo<*>.aErrorComanda(): ErrorComanda? = when (this) {
    is ResultadoCatalogo.Exito -> null
    ResultadoCatalogo.SesionInvalida -> null
    is ResultadoCatalogo.SinPermiso -> ErrorComanda.SinPermiso(mensaje)
    ResultadoCatalogo.SinConexion -> ErrorComanda.SinConexion
    ResultadoCatalogo.NoEncontrado -> ErrorComanda.NoEncontrada
    is ResultadoCatalogo.DatosInvalidos -> delServidor(campos.values.firstOrNull() ?: mensaje)
    is ResultadoCatalogo.Conflicto -> delServidor(campos.values.firstOrNull() ?: mensaje)
    else -> ErrorComanda.ErrorDelServidor
}

private fun delServidor(mensaje: String?): ErrorComanda =
    mensaje?.let(ErrorComanda::DelServidor) ?: ErrorComanda.ErrorDelServidor
