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
import com.appresso.app.data.comandas.EstadoComanda
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

/** Los tres momentos de la pantalla: armar, revisar el resumen y la confirmación de envío. */
enum class PasoComanda { ARMAR, RESUMEN, ENVIADA }

/** Por qué no se puede seguir; el texto se arma en la pantalla. */
sealed interface ErrorComanda {
    /** Se intentó revisar o enviar sin productos: la comanda vacía no se envía. */
    data object Vacia : ErrorComanda
    data object SinConexion : ErrorComanda
    data object ErrorDelServidor : ErrorComanda
    data class SinPermiso(val mensajeServidor: String?) : ErrorComanda

    /** El servidor rechazó la comanda con su mensaje (mesa ocupada, sin stock, descuento no autorizado…). */
    data class DelServidor(val mensaje: String) : ErrorComanda
}

/** Un producto de la comanda que se está armando, con el subtotal calculado (cantidad × precio vigente). */
data class LineaBorrador(
    val producto: MenuProducto,
    val cantidad: Int,
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
    val paso: PasoComanda = PasoComanda.ARMAR,
    val enviando: Boolean = false,
    val error: ErrorComanda? = null,
    /**
     * La comanda ya se creó en el servidor (PENDIENTE) pero no se pudo enviar a cocina. Al reintentar
     * solo se envía, sin crearla otra vez; ya no se puede editar.
     */
    val comandaCreadaId: Long? = null,
    /** Lo que devolvió el servidor al enviarla: los importes oficiales. */
    val enviada: Comanda? = null,
) {
    private val productos: Map<Long, MenuProducto> by lazy {
        categorias.flatMap { it.productos }.associateBy { it.id }
    }

    val visibles: List<MenuCategoria>
        get() = if (filtroCategoriaId == null) categorias else categorias.filter { it.id == filtroCategoriaId }

    val lineas: List<LineaBorrador> by lazy {
        cantidades.mapNotNull { (id, cantidad) -> productos[id]?.let { LineaBorrador(it, cantidad) } }
    }

    val importes: CalculadoraComanda.Importes by lazy {
        CalculadoraComanda.calcular(lineas.map { CalculadoraComanda.Linea(it.producto.precio, it.cantidad) })
    }

    val unidades: Int get() = lineas.sumOf { it.cantidad }
    val vacia: Boolean get() = lineas.isEmpty()
    val editable: Boolean get() = comandaCreadaId == null && !enviando && paso != PasoComanda.ENVIADA
    val puedeEnviar: Boolean get() = !vacia && !enviando

    fun cantidadDe(productoId: Long): Int = cantidades[productoId] ?: 0
}

/**
 * Armar una comanda para una mesa (RF-15, RF-16, RF-17): el mesero elige productos del menú y sus
 * cantidades, ve el subtotal de cada línea y el total mientras arma, revisa el resumen y lo envía a
 * cocina. Los importes se calculan aquí con [CalculadoraComanda] (las mismas reglas del backend) y,
 * al enviar, se muestran los que devolvió el servidor, que son los oficiales.
 *
 * Enviar son dos pasos del contrato: `POST /api/comandas` (nace PENDIENTE) y
 * `PATCH /api/comandas/{id}/estado` con `ENVIADA`. Si el segundo falla, la comanda queda creada y el
 * reintento solo repite el envío. La comanda vacía no se puede revisar ni enviar.
 *
 * No se pueden pedir más unidades que las existencias del menú (el backend rechazaría con 409).
 */
class ComandaViewModel(
    mesaId: Long,
    mesaNombre: String,
    private val menu: MenuRepository,
    private val comandas: ComandasRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(ComandaUiState(mesaId = mesaId, mesaNombre = mesaNombre))
    val estado: StateFlow<ComandaUiState> = _estado.asStateFlow()

    init {
        cargarMenu()
    }

    fun cargarMenu() {
        _estado.update { it.copy(cargandoMenu = true, errorMenu = null) }
        viewModelScope.launch {
            when (val r = menu.consultar()) {
                is ResultadoCatalogo.Exito -> _estado.update { estado ->
                    val ids = r.valor.flatMap { it.productos }.associateBy { it.id }
                    estado.copy(
                        categorias = r.valor,
                        cargandoMenu = false,
                        // Lo que ya no está en el menú (agotado o desactivado) sale de la comanda, y
                        // ninguna cantidad supera las existencias actuales.
                        cantidades = estado.cantidades
                            .mapNotNull { (id, n) -> ids[id]?.let { id to minOf(n, it.existencias) } }
                            .filter { it.second > 0 }
                            .toMap(),
                        filtroCategoriaId = estado.filtroCategoriaId?.takeIf { id -> r.valor.any { it.id == id } },
                    )
                }

                else -> _estado.update { it.copy(cargandoMenu = false, errorMenu = r.aErrorComanda()) }
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
            actual == 1 -> estado.copy(cantidades = estado.cantidades - producto.id)
            else -> estado.copy(cantidades = estado.cantidades + (producto.id to actual - 1))
        }
    }

    /** Pasa al resumen previo; con la comanda vacía no avanza y avisa. */
    fun verResumen() = _estado.update {
        if (it.vacia) it.copy(error = ErrorComanda.Vacia) else it.copy(paso = PasoComanda.RESUMEN, error = null)
    }

    /** Vuelve a editar desde el resumen, si la comanda todavía no se creó en el servidor. */
    fun seguirEditando() = _estado.update {
        if (it.comandaCreadaId == null && !it.enviando) it.copy(paso = PasoComanda.ARMAR, error = null) else it
    }

    /** Crea la comanda (si aún no existe) y la envía a cocina. */
    fun confirmar() {
        val actual = _estado.value
        if (actual.enviando || actual.paso == PasoComanda.ENVIADA) return
        if (actual.vacia) {
            _estado.update { it.copy(error = ErrorComanda.Vacia) }
            return
        }
        _estado.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            val id = actual.comandaCreadaId ?: when (val creada = comandas.crearComanda(actual.aNuevaComanda())) {
                is ResultadoCatalogo.Exito -> creada.valor.id.also { id ->
                    _estado.update { it.copy(comandaCreadaId = id) }
                }

                else -> {
                    _estado.update { it.copy(enviando = false, error = creada.aErrorComanda()) }
                    return@launch
                }
            }
            when (val r = comandas.cambiarEstado(id, EstadoComanda.ENVIADA)) {
                is ResultadoCatalogo.Exito -> _estado.update {
                    it.copy(enviando = false, paso = PasoComanda.ENVIADA, enviada = r.valor, comandaCreadaId = id)
                }

                else -> _estado.update { it.copy(enviando = false, error = r.aErrorComanda()) }
            }
        }
    }

    private fun ComandaUiState.aNuevaComanda() = NuevaComanda(
        mesaId = mesaId,
        items = lineas.map { ItemPedido(it.producto.id, it.cantidad) },
    )

    companion object {
        fun factory(
            mesaId: Long,
            mesaNombre: String,
            menu: MenuRepository,
            comandas: ComandasRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { ComandaViewModel(mesaId, mesaNombre, menu, comandas) } }
    }
}

/** 401 no muestra nada: el cliente HTTP ya cerró la sesión. Los 400/409 traen el mensaje del servidor. */
private fun ResultadoCatalogo<*>.aErrorComanda(): ErrorComanda? = when (this) {
    is ResultadoCatalogo.Exito -> null
    ResultadoCatalogo.SesionInvalida -> null
    is ResultadoCatalogo.SinPermiso -> ErrorComanda.SinPermiso(mensaje)
    ResultadoCatalogo.SinConexion -> ErrorComanda.SinConexion
    is ResultadoCatalogo.DatosInvalidos -> delServidor(campos.values.firstOrNull() ?: mensaje)
    is ResultadoCatalogo.Conflicto -> delServidor(campos.values.firstOrNull() ?: mensaje)
    else -> ErrorComanda.ErrorDelServidor
}

private fun delServidor(mensaje: String?): ErrorComanda =
    mensaje?.let(ErrorComanda::DelServidor) ?: ErrorComanda.ErrorDelServidor
