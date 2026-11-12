package com.appresso.app.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.appresso.app.data.auth.AlmacenSesion
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.usuarios.DatosUsuario
import com.appresso.app.data.usuarios.ResultadoUsuarios
import com.appresso.app.data.usuarios.Usuario
import com.appresso.app.data.usuarios.UsuariosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Campo { NOMBRE, CORREO, CONTRASENA, ROL }

/** Problema de un campo del formulario. El ViewModel no conoce textos: la pantalla los traduce. */
sealed interface ErrorCampo {
    data object Obligatorio : ErrorCampo
    data object NombreDemasiadoLargo : ErrorCampo
    data object CorreoInvalido : ErrorCampo
    data object ContrasenaCorta : ErrorCampo
    data object ContrasenaLarga : ErrorCampo

    /** Texto que envió el servidor (se muestra tal cual). */
    data class Servidor(val mensaje: String) : ErrorCampo
}

/** Problema general de una operación (no asociado a un campo). */
sealed interface ErrorUsuarios {
    data object SinConexion : ErrorUsuarios
    data object ErrorDelServidor : ErrorUsuarios
    data object NoEncontrado : ErrorUsuarios
    data class SinPermiso(val mensajeServidor: String?) : ErrorUsuarios
    data class Regla(val mensajeServidor: String?) : ErrorUsuarios
    data class DatosInvalidos(val mensajeServidor: String?) : ErrorUsuarios
}

data class FormularioUiState(
    /** null = crear una cuenta nueva; si no, la cuenta que se edita. */
    val editando: Usuario? = null,
    val nombre: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val rol: Rol? = null,
    val errores: Map<Campo, ErrorCampo> = emptyMap(),
    val error: ErrorUsuarios? = null,
    val guardando: Boolean = false,
    /** true mientras se muestra la confirmación de los cambios de una edición. */
    val confirmando: Boolean = false,
)

data class UsuariosUiState(
    val usuarios: List<Usuario> = emptyList(),
    val cargando: Boolean = false,
    val errorLista: ErrorUsuarios? = null,
    val formulario: FormularioUiState? = null,
    /** Cuenta para la que se pide confirmar la desactivación. */
    val confirmarDesactivar: Usuario? = null,
    /** Cuenta con una desactivación o activación en curso. */
    val procesandoId: Long? = null,
    /** Error de la última desactivación/activación; se descarta con [UsuariosViewModel.descartarError]. */
    val errorAccion: ErrorUsuarios? = null,
)

class UsuariosViewModel(
    private val repositorio: UsuariosRepository,
    private val almacenSesion: AlmacenSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(UsuariosUiState())
    val estado: StateFlow<UsuariosUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    // ---------------------------------------------------------------- lista

    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorLista = null) }
        viewModelScope.launch {
            when (val r = repositorio.listar()) {
                is ResultadoUsuarios.Exito -> _estado.update {
                    it.copy(cargando = false, usuarios = ordenar(r.valor))
                }

                else -> {
                    val error = r.aError()
                    _estado.update { it.copy(cargando = false, errorLista = error) }
                }
            }
        }
    }

    fun descartarError() = _estado.update { it.copy(errorAccion = null) }

    // ---------------------------------------------------------------- desactivar / activar

    fun pedirDesactivar(usuario: Usuario) = _estado.update {
        if (it.procesandoId != null) it else it.copy(confirmarDesactivar = usuario, errorAccion = null)
    }

    fun cancelarDesactivar() = _estado.update { it.copy(confirmarDesactivar = null) }

    fun confirmarDesactivar() {
        val usuario = _estado.value.confirmarDesactivar ?: return
        _estado.update { it.copy(confirmarDesactivar = null) }
        cambiarEstado(usuario.id) { repositorio.desactivar(usuario.id) }
    }

    /** Reactivar no pide confirmación: es reversible y no bloquea a nadie. */
    fun activar(usuario: Usuario) = cambiarEstado(usuario.id) { repositorio.activar(usuario.id) }

    private fun cambiarEstado(id: Long, llamada: suspend () -> ResultadoUsuarios<Usuario>) {
        if (_estado.value.procesandoId != null) return
        _estado.update { it.copy(procesandoId = id, errorAccion = null) }
        viewModelScope.launch {
            val r = llamada()
            if (r is ResultadoUsuarios.Exito) {
                // La lista se actualiza con la respuesta, sin volver a pedirla.
                _estado.update { it.copy(procesandoId = null, usuarios = reemplazar(it.usuarios, r.valor)) }
            } else {
                val error = r.aError()
                _estado.update { it.copy(procesandoId = null, errorAccion = error) }
                if (error == ErrorUsuarios.NoEncontrado) cargar()
            }
        }
    }

    // ---------------------------------------------------------------- formulario

    fun nuevoUsuario() = _estado.update { it.copy(formulario = FormularioUiState()) }

    fun editarUsuario(usuario: Usuario) = _estado.update {
        it.copy(
            formulario = FormularioUiState(
                editando = usuario,
                nombre = usuario.nombre,
                correo = usuario.correo,
                rol = usuario.rol,
            ),
        )
    }

    fun cerrarFormulario() = _estado.update {
        // No se puede cerrar mientras se guarda: la respuesta llegaría a un formulario que ya no existe.
        if (it.formulario?.guardando == true) it else it.copy(formulario = null)
    }

    fun onNombreChange(valor: String) = editar { it.copy(nombre = valor).sinError(Campo.NOMBRE) }
    fun onCorreoChange(valor: String) = editar { it.copy(correo = valor).sinError(Campo.CORREO) }
    fun onContrasenaChange(valor: String) = editar { it.copy(contrasena = valor).sinError(Campo.CONTRASENA) }
    fun onRolChange(valor: Rol) = editar { it.copy(rol = valor).sinError(Campo.ROL) }

    private fun editar(cambio: (FormularioUiState) -> FormularioUiState) = _estado.update { estado ->
        val f = estado.formulario
        if (f == null || f.guardando) estado else estado.copy(formulario = cambio(f))
    }

    private fun FormularioUiState.sinError(campo: Campo) =
        copy(errores = errores - campo, error = null, confirmando = false)

    /** Valida; al crear guarda directo, al editar primero pide confirmar los cambios. */
    fun guardar() {
        val f = _estado.value.formulario ?: return
        if (f.guardando) return

        val errores = validar(f)
        if (errores.isNotEmpty()) {
            editar { it.copy(errores = errores, error = null, confirmando = false) }
            return
        }
        val editando = f.editando
        if (editando == null) {
            enviar(f)
        } else if (cambios(f, editando).isEmpty()) {
            cerrarFormulario() // nada que guardar
        } else {
            editar { it.copy(confirmando = true) }
        }
    }

    fun cancelarConfirmacion() = editar { it.copy(confirmando = false) }

    fun confirmarCambios() {
        val f = _estado.value.formulario ?: return
        if (f.guardando || !f.confirmando) return
        enviar(f)
    }

    private fun enviar(f: FormularioUiState) {
        val rol = f.rol ?: return
        val clave = f.contrasena.ifEmpty { null }
        val datos = DatosUsuario(f.nombre.trim(), f.correo.trim(), clave, rol)

        _estado.update { it.copy(formulario = f.copy(guardando = true, confirmando = false, error = null)) }
        viewModelScope.launch {
            val r = if (f.editando == null) repositorio.crear(datos) else repositorio.editar(f.editando.id, datos)
            if (r is ResultadoUsuarios.Exito) {
                // Se cierra el formulario y la lista ya trae la cuenta nueva o modificada.
                _estado.update { it.copy(formulario = null, usuarios = reemplazar(it.usuarios, r.valor)) }
            } else {
                mostrarErrorDelFormulario(r)
            }
        }
    }

    private fun mostrarErrorDelFormulario(r: ResultadoUsuarios<*>) {
        val porCampo = when (r) {
            is ResultadoUsuarios.DatosInvalidos -> r.campos
            is ResultadoUsuarios.Conflicto -> r.campos
            else -> emptyMap()
        }
        val errores = porCampo.mapNotNull { (nombre, texto) ->
            Campo.entries.firstOrNull { it.name.equals(nombre, ignoreCase = true) }?.let { it to ErrorCampo.Servidor(texto) }
        }.toMap()
        // Si el servidor señaló campos concretos, el aviso general sobra.
        val general = if (errores.isNotEmpty()) null else r.aError()
        _estado.update { estado ->
            estado.copy(formulario = estado.formulario?.copy(guardando = false, errores = errores, error = general))
        }
    }

    /** Un 401 (token vencido o cuenta desactivada) cierra la sesión y la app vuelve al login. */
    private fun ResultadoUsuarios<*>.aError(): ErrorUsuarios? = when (this) {
        is ResultadoUsuarios.Exito -> null
        is ResultadoUsuarios.DatosInvalidos -> ErrorUsuarios.DatosInvalidos(mensaje)
        is ResultadoUsuarios.Conflicto -> ErrorUsuarios.Regla(mensaje)
        ResultadoUsuarios.SesionInvalida -> {
            almacenSesion.borrar()
            null
        }
        is ResultadoUsuarios.SinPermiso -> ErrorUsuarios.SinPermiso(mensaje)
        ResultadoUsuarios.NoEncontrado -> ErrorUsuarios.NoEncontrado
        ResultadoUsuarios.SinConexion -> ErrorUsuarios.SinConexion
        ResultadoUsuarios.ErrorDelServidor -> ErrorUsuarios.ErrorDelServidor
    }

    companion object {
        const val MAX_NOMBRE = 100
        const val MAX_CORREO = 150
        const val MIN_CONTRASENA = 8
        const val MAX_CONTRASENA = 72

        private val FORMATO_CORREO = Regex("^[^@\\s]+@[^@\\s]+$")

        /** Mismas reglas que el backend (docs/api-usuarios.md); el formato fino del correo lo valida el servidor. */
        internal fun validar(f: FormularioUiState): Map<Campo, ErrorCampo> = buildMap {
            val nombre = f.nombre.trim()
            val correo = f.correo.trim()
            when {
                nombre.isEmpty() -> put(Campo.NOMBRE, ErrorCampo.Obligatorio)
                nombre.length > MAX_NOMBRE -> put(Campo.NOMBRE, ErrorCampo.NombreDemasiadoLargo)
            }
            when {
                correo.isEmpty() -> put(Campo.CORREO, ErrorCampo.Obligatorio)
                correo.length > MAX_CORREO || !FORMATO_CORREO.matches(correo) -> put(Campo.CORREO, ErrorCampo.CorreoInvalido)
            }
            val obligatoria = f.editando == null
            when {
                f.contrasena.isEmpty() && obligatoria -> put(Campo.CONTRASENA, ErrorCampo.Obligatorio)
                f.contrasena.isEmpty() -> Unit // al editar, vacía = no cambiarla
                f.contrasena.length < MIN_CONTRASENA -> put(Campo.CONTRASENA, ErrorCampo.ContrasenaCorta)
                f.contrasena.length > MAX_CONTRASENA -> put(Campo.CONTRASENA, ErrorCampo.ContrasenaLarga)
            }
            if (f.rol == null) put(Campo.ROL, ErrorCampo.Obligatorio)
        }

        /** Qué campos cambiarían respecto a la cuenta original; sirve para la confirmación. */
        internal fun cambios(f: FormularioUiState, original: Usuario): Set<Campo> = buildSet {
            if (f.nombre.trim() != original.nombre) add(Campo.NOMBRE)
            if (f.correo.trim() != original.correo) add(Campo.CORREO)
            if (f.contrasena.isNotEmpty()) add(Campo.CONTRASENA)
            if (f.rol != original.rol) add(Campo.ROL)
        }

        internal fun ordenar(lista: List<Usuario>) =
            lista.sortedWith(compareBy<Usuario, String>(String.CASE_INSENSITIVE_ORDER) { it.nombre }.thenBy { it.id })

        internal fun reemplazar(lista: List<Usuario>, usuario: Usuario) =
            ordenar(lista.filterNot { it.id == usuario.id } + usuario)

        fun factory(repositorio: UsuariosRepository, almacenSesion: AlmacenSesion): ViewModelProvider.Factory =
            viewModelFactory { initializer { UsuariosViewModel(repositorio, almacenSesion) } }
    }
}
