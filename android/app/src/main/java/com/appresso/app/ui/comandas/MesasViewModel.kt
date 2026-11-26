package com.appresso.app.ui.comandas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.comandas.ComandasRepository
import com.appresso.app.data.comandas.Mesa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué no se pudo cargar el mapa o guardar la mesa; el texto se arma en la pantalla. */
sealed interface ErrorMesas {
    data object SinConexion : ErrorMesas
    data object ErrorDelServidor : ErrorMesas
    data class SinPermiso(val mensajeServidor: String?) : ErrorMesas

    /** El servidor rechazó el dato y dio su propio mensaje (400 o 409 sin campo conocido). */
    data class DelServidor(val mensaje: String) : ErrorMesas
}

/** Problema del nombre de la mesa. [DelServidor] trae el texto del backend (p. ej. el 409 por repetido). */
sealed interface ErrorNombreMesa {
    data object Vacio : ErrorNombreMesa
    data object MuyLargo : ErrorNombreMesa
    data object Repetido : ErrorNombreMesa
    data class DelServidor(val mensaje: String) : ErrorNombreMesa
}

/** Mismas reglas que el backend (`MesaRequest`, `MesaService`): obligatorio, ≤30, único sin mayúsculas ni espacios. */
object ReglasMesa {
    const val LARGO_MAXIMO = 30

    fun normalizar(nombre: String) = nombre.trim().lowercase()

    fun validar(nombre: String, existentes: List<Mesa>): ErrorNombreMesa? {
        val limpio = nombre.trim()
        return when {
            limpio.isEmpty() -> ErrorNombreMesa.Vacio
            limpio.length > LARGO_MAXIMO -> ErrorNombreMesa.MuyLargo
            existentes.any { normalizar(it.nombre) == normalizar(limpio) } -> ErrorNombreMesa.Repetido
            else -> null
        }
    }
}

data class FormularioMesaUiState(
    val nombre: String = "",
    val errorNombre: ErrorNombreMesa? = null,
    val error: ErrorMesas? = null,
    val guardando: Boolean = false,
)

data class MesasUiState(
    val mesas: List<Mesa> = emptyList(),
    val cargando: Boolean = false,
    /** Solo se muestra si no hay mesas que enseñar. */
    val error: ErrorMesas? = null,
    /** Abierto solo para el administrador (crear mesa). */
    val formulario: FormularioMesaUiState? = null,
    /** Nombre de la última mesa creada, para avisarlo una vez. */
    val mesaCreada: String? = null,
) {
    val disponibles: Int get() = mesas.count { it.disponible }
    val ocupadas: Int get() = mesas.size - disponibles
}

/**
 * Mapa de mesas (RF-15, RF-64): las mesas con su estado, para que el mesero elija dónde abrir una
 * comanda. El administrador además crea mesas: el nombre se valida aquí con las reglas del servidor
 * (vacío, largo o repetido no se envían) y el servidor tiene la última palabra (409 si otro
 * dispositivo la creó antes). La mesa nueva aparece disponible en el mapa.
 */
class MesasViewModel(private val repositorio: ComandasRepository) : ViewModel() {

    private val _estado = MutableStateFlow(MesasUiState())
    val estado: StateFlow<MesasUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() = cargar(silencioso = false)

    /** Vuelve a pedir las mesas sin indicador de carga (p. ej. al volver de crear una comanda). */
    fun recargarEnSilencio() {
        _estado.update { it.copy(mesaCreada = null) }
        cargar(silencioso = true)
    }

    private fun cargar(silencioso: Boolean) {
        if (!silencioso) _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = repositorio.listarMesas()) {
                is ResultadoCatalogo.Exito -> _estado.update { it.copy(mesas = r.valor, cargando = false, error = null) }
                else -> _estado.update { it.copy(cargando = false, error = if (silencioso) it.error else r.aError()) }
            }
        }
    }

    // ---------------------------------------------------------------- crear mesa (administrador)

    fun nuevaMesa() = _estado.update { it.copy(formulario = FormularioMesaUiState(), mesaCreada = null) }

    fun onNombreChange(valor: String) = _estado.update {
        it.copy(formulario = it.formulario?.copy(nombre = valor, errorNombre = null, error = null))
    }

    fun cerrarFormulario() = _estado.update { if (it.formulario?.guardando == true) it else it.copy(formulario = null) }

    fun guardar() {
        val actual = _estado.value
        val formulario = actual.formulario ?: return
        if (formulario.guardando) return
        ReglasMesa.validar(formulario.nombre, actual.mesas)?.let { problema ->
            _estado.update { it.copy(formulario = formulario.copy(errorNombre = problema)) }
            return
        }
        _estado.update { it.copy(formulario = formulario.copy(guardando = true, error = null)) }
        viewModelScope.launch {
            val r = repositorio.crearMesa(formulario.nombre.trim())
            _estado.update { estado ->
                val form = estado.formulario?.copy(guardando = false)
                when (r) {
                    is ResultadoCatalogo.Exito -> estado.copy(
                        mesas = (estado.mesas + r.valor).sortedBy { ReglasMesa.normalizar(it.nombre) },
                        formulario = null,
                        mesaCreada = r.valor.nombre,
                    )

                    is ResultadoCatalogo.DatosInvalidos -> estado.copy(formulario = form?.conRechazo(r.campos["nombre"], r.mensaje))
                    is ResultadoCatalogo.Conflicto -> estado.copy(formulario = form?.conRechazo(r.campos["nombre"], r.mensaje))
                    // 401: la sesión ya se cerró y la app vuelve al login.
                    ResultadoCatalogo.SesionInvalida -> estado.copy(formulario = null)
                    else -> estado.copy(formulario = form?.copy(error = r.aError()))
                }
            }
        }
    }

    private fun FormularioMesaUiState.conRechazo(campoNombre: String?, mensaje: String?) = when {
        campoNombre != null -> copy(errorNombre = ErrorNombreMesa.DelServidor(campoNombre))
        mensaje != null -> copy(error = ErrorMesas.DelServidor(mensaje))
        else -> copy(error = ErrorMesas.ErrorDelServidor)
    }

    companion object {
        fun factory(repositorio: ComandasRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { MesasViewModel(repositorio) } }
    }
}

/** 401 no muestra nada: el cliente HTTP ya cerró la sesión. */
internal fun ResultadoCatalogo<*>.aError(): ErrorMesas? = when (this) {
    is ResultadoCatalogo.Exito -> null
    ResultadoCatalogo.SesionInvalida -> null
    is ResultadoCatalogo.SinPermiso -> ErrorMesas.SinPermiso(mensaje)
    ResultadoCatalogo.SinConexion -> ErrorMesas.SinConexion
    is ResultadoCatalogo.DatosInvalidos -> mensaje?.let(ErrorMesas::DelServidor) ?: ErrorMesas.ErrorDelServidor
    is ResultadoCatalogo.Conflicto -> mensaje?.let(ErrorMesas::DelServidor) ?: ErrorMesas.ErrorDelServidor
    else -> ErrorMesas.ErrorDelServidor
}
