package com.appresso.app.ui.cuenta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.cuenta.CuentaClienteRepository
import com.appresso.app.data.cuenta.DatosRegistro
import com.appresso.app.data.cuenta.ReglasCuenta
import com.appresso.app.data.cuenta.ResultadoCuenta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Los campos del formulario de registro, en el orden en que se muestran. */
enum class CampoRegistro(val nombreServidor: String) {
    NOMBRE("nombre"),
    APELLIDO("apellido"),
    DOCUMENTO("documento"),
    CELULAR("celular"),
    CORREO("correo"),
    CONTRASENA("contrasena"),
    ACEPTO("aceptoTratamientoDatos"),
}

/** Qué le pasa a un campo según la validación de la app. */
enum class ProblemaCampo { FALTA, FORMATO }

/**
 * Por qué no se pudo registrar. El ViewModel no conoce textos: la pantalla traduce cada caso
 * (los mensajes del servidor se muestran tal cual, porque son los de RF-50).
 */
sealed interface ErrorRegistro {
    /** Falta algún dato o no aceptó el tratamiento de datos (RF-50, criterio 2). */
    data object Incompleto : ErrorRegistro

    /** El primer campo con formato inválido, en el orden del formulario. */
    data class Formato(val campo: CampoRegistro) : ErrorRegistro

    /** 400 o 409 del servidor, por ejemplo "Este documento ya está registrado.". */
    data class DelServidor(val mensaje: String?) : ErrorRegistro

    data object SinConexion : ErrorRegistro
    data object ErrorDelServidor : ErrorRegistro
}

/** Todo lo que la pantalla de registro necesita dibujar. */
data class RegistroUiState(
    val nombre: String = "",
    val apellido: String = "",
    val documento: String = "",
    val celular: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val acepto: Boolean = false,
    val cargando: Boolean = false,
    /** Problemas detectados antes de enviar. */
    val problemas: Map<CampoRegistro, ProblemaCampo> = emptyMap(),
    /** Problemas por campo que devolvió el servidor, con su texto. */
    val erroresServidor: Map<CampoRegistro, String> = emptyMap(),
    val error: ErrorRegistro? = null,
    /** La cuenta ya se creó: la pantalla muestra [mensajeExito] y el paso a iniciar sesión. */
    val registrado: Boolean = false,
    val mensajeExito: String? = null,
)

/**
 * Registro del cliente desde la app (RF-50). Valida con las mismas reglas del servidor
 * ([ReglasCuenta]) antes de enviar, evita enviar dos veces y, si falla, conserva lo escrito.
 * La contraseña se descarta al terminar.
 */
class RegistroClienteViewModel(private val repositorio: CuentaClienteRepository) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroUiState())
    val estado: StateFlow<RegistroUiState> = _estado.asStateFlow()

    fun onNombreChange(valor: String) = editar(CampoRegistro.NOMBRE) { it.copy(nombre = valor) }
    fun onApellidoChange(valor: String) = editar(CampoRegistro.APELLIDO) { it.copy(apellido = valor) }
    fun onDocumentoChange(valor: String) = editar(CampoRegistro.DOCUMENTO) { it.copy(documento = valor) }
    fun onCelularChange(valor: String) = editar(CampoRegistro.CELULAR) { it.copy(celular = valor) }
    fun onCorreoChange(valor: String) = editar(CampoRegistro.CORREO) { it.copy(correo = valor) }
    fun onContrasenaChange(valor: String) = editar(CampoRegistro.CONTRASENA) { it.copy(contrasena = valor) }
    fun onAceptoChange(valor: Boolean) = editar(CampoRegistro.ACEPTO) { it.copy(acepto = valor) }

    fun enviar() {
        val actual = _estado.value
        if (actual.cargando || actual.registrado) return

        val problemas = validar(actual)
        if (problemas.isNotEmpty()) {
            val error = if (ProblemaCampo.FALTA in problemas.values) {
                ErrorRegistro.Incompleto
            } else {
                ErrorRegistro.Formato(CampoRegistro.entries.first { it in problemas })
            }
            _estado.update { it.copy(problemas = problemas, erroresServidor = emptyMap(), error = error) }
            return
        }

        _estado.update { it.copy(cargando = true, problemas = emptyMap(), erroresServidor = emptyMap(), error = null) }
        viewModelScope.launch {
            val datos = DatosRegistro(
                nombre = actual.nombre.trim(),
                apellido = actual.apellido.trim(),
                documento = actual.documento.trim(),
                celular = actual.celular.trim(),
                correo = actual.correo.trim(),
                contrasena = actual.contrasena,
                aceptoTratamientoDatos = true,
            )
            when (val resultado = repositorio.registrar(datos)) {
                is ResultadoCuenta.Exito -> _estado.update {
                    it.copy(cargando = false, registrado = true, mensajeExito = resultado.mensaje, contrasena = "")
                }
                is ResultadoCuenta.DatosInvalidos -> rechazado(resultado.mensaje, resultado.campos)
                is ResultadoCuenta.Duplicado -> rechazado(resultado.mensaje, resultado.campos)
                ResultadoCuenta.SinConexion -> _estado.update { it.copy(cargando = false, error = ErrorRegistro.SinConexion) }
                ResultadoCuenta.ErrorDelServidor ->
                    _estado.update { it.copy(cargando = false, error = ErrorRegistro.ErrorDelServidor) }
            }
        }
    }

    private fun rechazado(mensaje: String?, campos: Map<String, String>) {
        val porCampo = campos.mapNotNull { (nombre, texto) ->
            CampoRegistro.entries.firstOrNull { it.nombreServidor == nombre }?.let { it to texto }
        }.toMap()
        _estado.update {
            it.copy(cargando = false, erroresServidor = porCampo, error = ErrorRegistro.DelServidor(mensaje))
        }
    }

    /** Mientras se espera la respuesta no se edita; al editar un campo, sus errores ya no aplican. */
    private fun editar(campo: CampoRegistro, cambio: (RegistroUiState) -> RegistroUiState) = _estado.update {
        if (it.cargando || it.registrado) {
            it
        } else {
            cambio(it).copy(problemas = it.problemas - campo, erroresServidor = it.erroresServidor - campo, error = null)
        }
    }

    companion object {
        internal fun validar(e: RegistroUiState): Map<CampoRegistro, ProblemaCampo> = buildMap {
            fun revisar(campo: CampoRegistro, valor: String, valido: (String) -> Boolean) {
                when {
                    valor.isBlank() -> put(campo, ProblemaCampo.FALTA)
                    !valido(valor.trim()) -> put(campo, ProblemaCampo.FORMATO)
                }
            }
            revisar(CampoRegistro.NOMBRE, e.nombre) { it.length <= ReglasCuenta.MAX_NOMBRE }
            revisar(CampoRegistro.APELLIDO, e.apellido) { it.length <= ReglasCuenta.MAX_NOMBRE }
            revisar(CampoRegistro.DOCUMENTO, e.documento, ReglasCuenta::esDocumentoValido)
            revisar(CampoRegistro.CELULAR, e.celular, ReglasCuenta::esCelularValido)
            revisar(CampoRegistro.CORREO, e.correo, ReglasCuenta::esCorreoValido)
            if (e.contrasena.isEmpty()) {
                put(CampoRegistro.CONTRASENA, ProblemaCampo.FALTA)
            } else if (!ReglasCuenta.esContrasenaValida(e.contrasena)) {
                put(CampoRegistro.CONTRASENA, ProblemaCampo.FORMATO)
            }
            if (!e.acepto) put(CampoRegistro.ACEPTO, ProblemaCampo.FALTA)
        }

        fun factory(repositorio: CuentaClienteRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { RegistroClienteViewModel(repositorio) } }
    }
}
