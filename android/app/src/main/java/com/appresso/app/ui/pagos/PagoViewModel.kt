package com.appresso.app.ui.pagos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.pagos.MedioPago
import com.appresso.app.data.pagos.PagoConfirmado
import com.appresso.app.data.pagos.PagosRepository
import com.appresso.app.data.pagos.ResultadoPago
import com.appresso.app.permisos.Accion
import com.appresso.app.permisos.puede
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Problema al confirmar el pago. El ViewModel no conoce textos: la pantalla los traduce. */
sealed interface ErrorPago {
    /** No se sabe si el pago llegó al servidor: volver a confirmar es seguro (mismo identificador). */
    data object SinConexion : ErrorPago

    /** Igual que [SinConexion]: volver a confirmar es seguro. */
    data object ErrorDelServidor : ErrorPago

    data object NoEncontrada : ErrorPago

    data class SinPermiso(val mensajeServidor: String?) : ErrorPago

    /** La comanda ya fue pagada, fue cancelada o aún no está lista; el mensaje es del servidor. */
    data class Conflicto(val mensajeServidor: String?) : ErrorPago

    data class DatosInvalidos(val mensajeServidor: String?) : ErrorPago
}

data class PagoUiState(
    val comandaId: Long,
    /** Nombre de la mesa y total de la comanda, si la pantalla que abrió esta los conoce. */
    val mesa: String? = null,
    val total: BigDecimal? = null,
    /** Los medios que el rol puede confirmar: el mesero solo ve transferencia (RF-29, RF-30). */
    val medios: List<MedioPago> = emptyList(),
    val medio: MedioPago? = null,
    /** true mientras se muestra la pregunta «¿confirmar el pago?». */
    val confirmando: Boolean = false,
    val procesando: Boolean = false,
    val error: ErrorPago? = null,
    /** El pago registrado; cuando existe, la comanda ya quedó cerrada. */
    val pagado: PagoConfirmado? = null,
    /** true si el servidor respondió que ese pago ya estaba registrado (un reintento). */
    val yaEstabaRegistrado: Boolean = false,
)

/**
 * Pantalla de confirmación de pago (6.1.1): elegir el medio y confirmar. Qué medios se ofrecen
 * depende del rol; el backend vuelve a validarlo (403).
 *
 * Genera **un** identificador de operación al crearse y lo reenvía en cada intento. Si la red se
 * cae o el servidor falla sin que se sepa si el pago llegó, volver a pulsar «Confirmar» es seguro:
 * el servidor devuelve el pago ya registrado en vez de cobrar dos veces (RNF-11, RF-43).
 */
class PagoViewModel(
    comandaId: Long,
    mesa: String?,
    total: BigDecimal?,
    rol: Rol,
    private val repositorio: PagosRepository,
    nuevoIdentificador: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val identificadorOperacion = nuevoIdentificador()

    private val _estado = MutableStateFlow(
        run {
            val medios = buildList {
                if (puede(rol, Accion.COBRAR_EFECTIVO)) add(MedioPago.EFECTIVO)
                if (puede(rol, Accion.CONFIRMAR_TRANSFERENCIA)) add(MedioPago.TRANSFERENCIA)
            }
            PagoUiState(
                comandaId = comandaId,
                mesa = mesa,
                total = total,
                medios = medios,
                // Si el rol solo tiene una opción (el mesero), ya viene elegida.
                medio = medios.singleOrNull(),
            )
        },
    )
    val estado: StateFlow<PagoUiState> = _estado.asStateFlow()

    fun seleccionarMedio(medio: MedioPago) = _estado.update {
        if (medio !in it.medios || it.procesando || it.pagado != null) it else it.copy(medio = medio, error = null)
    }

    /** Abre la pregunta de confirmación. Sin medio elegido no hace nada. */
    fun pedirConfirmacion() = _estado.update {
        if (it.medio == null || it.procesando || it.pagado != null) it else it.copy(confirmando = true, error = null)
    }

    fun cancelarConfirmacion() = _estado.update { it.copy(confirmando = false) }

    fun descartarError() = _estado.update { it.copy(error = null) }

    /** Envía el pago. Ignora la llamada si ya hay una en curso (doble pulsación) o ya está pagado. */
    fun confirmar() {
        val actual = _estado.value
        val medio = actual.medio
        if (medio == null || actual.procesando || actual.pagado != null) return
        _estado.update { it.copy(confirmando = false, procesando = true, error = null) }
        viewModelScope.launch {
            val r = repositorio.confirmar(actual.comandaId, medio, identificadorOperacion)
            _estado.update { it.aplicar(r) }
        }
    }

    /**
     * Un 401 no muestra nada: el cliente HTTP ya cerró la sesión y la app vuelve al login.
     * La falta de conexión sí se muestra aquí (aunque el cliente la avise): hay que decir que
     * volver a confirmar es seguro.
     */
    private fun PagoUiState.aplicar(r: ResultadoPago): PagoUiState = when (r) {
        is ResultadoPago.Exito -> copy(
            procesando = false,
            pagado = r.pago,
            yaEstabaRegistrado = r.repetido,
        )
        is ResultadoPago.DatosInvalidos -> falla(ErrorPago.DatosInvalidos(r.mensaje))
        is ResultadoPago.Conflicto -> falla(ErrorPago.Conflicto(r.mensaje))
        ResultadoPago.SesionInvalida -> copy(procesando = false)
        is ResultadoPago.SinPermiso -> falla(ErrorPago.SinPermiso(r.mensaje))
        ResultadoPago.NoEncontrada -> falla(ErrorPago.NoEncontrada)
        ResultadoPago.SinConexion -> falla(ErrorPago.SinConexion)
        ResultadoPago.ErrorDelServidor -> falla(ErrorPago.ErrorDelServidor)
    }

    private fun PagoUiState.falla(error: ErrorPago) = copy(procesando = false, error = error)

    companion object {
        fun factory(
            comandaId: Long,
            mesa: String?,
            total: BigDecimal?,
            rol: Rol,
            repositorio: PagosRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PagoViewModel(comandaId, mesa, total, rol, repositorio) }
        }
    }
}
