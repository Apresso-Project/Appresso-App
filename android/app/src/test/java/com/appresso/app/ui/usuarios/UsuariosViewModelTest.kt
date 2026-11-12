package com.appresso.app.ui.usuarios

import com.appresso.app.data.auth.AlmacenSesionEnMemoria
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import com.appresso.app.data.usuarios.DatosUsuario
import com.appresso.app.data.usuarios.ResultadoUsuarios
import com.appresso.app.data.usuarios.Usuario
import com.appresso.app.data.usuarios.UsuariosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsuariosViewModelTest {

    private val ana = Usuario(1, "Ana", "ana@appresso.test", Rol.MESERO, true)
    private val beto = Usuario(2, "beto", "beto@appresso.test", Rol.CAJERO, true)

    private val repo = RepositorioFalso()
    private val almacen = AlmacenSesionEnMemoria()
    private lateinit var vm: UsuariosViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        almacen.guardar(Sesion("t", Rol.ADMINISTRADOR, "Admin"))
        repo.lista = ResultadoUsuarios.Exito(listOf(beto, ana))
        vm = UsuariosViewModel(repo, almacen)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    // ------------------------------------------------------------ lista

    @Test
    fun `al abrir carga la lista ordenada por nombre sin distinguir mayusculas`() {
        assertEquals(listOf(ana, beto), estado.usuarios)
        assertFalse(estado.cargando)
    }

    @Test
    fun `si falla la carga muestra el error y reintentar lo recupera`() {
        repo.lista = ResultadoUsuarios.SinConexion
        vm.cargar()
        assertEquals(ErrorUsuarios.SinConexion, estado.errorLista)

        repo.lista = ResultadoUsuarios.Exito(listOf(ana))
        vm.cargar()
        assertNull(estado.errorLista)
        assertEquals(listOf(ana), estado.usuarios)
    }

    @Test
    fun `un 403 al cargar se muestra con el mensaje del servidor`() {
        repo.lista = ResultadoUsuarios.SinPermiso("Acceso no autorizado")
        vm.cargar()
        assertEquals(ErrorUsuarios.SinPermiso("Acceso no autorizado"), estado.errorLista)
    }

    @Test
    fun `un 401 cierra la sesion`() {
        repo.lista = ResultadoUsuarios.SesionInvalida
        vm.cargar()
        assertNull(almacen.sesion.value)
    }

    // ------------------------------------------------------------ crear

    @Test
    fun `crear con datos validos agrega la cuenta a la lista sin recargar y cierra el formulario`() {
        val nueva = Usuario(3, "Carla", "carla@appresso.test", Rol.COCINERO, true)
        repo.crear = ResultadoUsuarios.Exito(nueva)
        val listados = repo.listados

        vm.nuevoUsuario()
        vm.onNombreChange(" Carla ")
        vm.onCorreoChange("carla@appresso.test")
        vm.onContrasenaChange("clave-segura")
        vm.onRolChange(Rol.COCINERO)
        vm.guardar()

        assertNull(estado.formulario)
        assertEquals(listOf(ana, beto, nueva).map { it.id }.toSet(), estado.usuarios.map { it.id }.toSet())
        assertEquals(listados, repo.listados) // no volvió a pedir la lista
        assertEquals(DatosUsuario("Carla", "carla@appresso.test", "clave-segura", Rol.COCINERO), repo.ultimosDatos)
    }

    @Test
    fun `crear con campos vacios marca errores y no llama al servidor`() {
        vm.nuevoUsuario()
        vm.guardar()

        assertEquals(
            setOf(Campo.NOMBRE, Campo.CORREO, Campo.CONTRASENA, Campo.ROL),
            estado.formulario!!.errores.keys,
        )
        assertEquals(0, repo.llamadasCrear)
    }

    @Test
    fun `validaciones de formato y largo`() {
        vm.nuevoUsuario()
        vm.onNombreChange("x".repeat(101))
        vm.onCorreoChange("sin-arroba")
        vm.onContrasenaChange("corta")
        vm.onRolChange(Rol.MESERO)
        vm.guardar()
        val e = estado.formulario!!.errores
        assertEquals(ErrorCampo.NombreDemasiadoLargo, e[Campo.NOMBRE])
        assertEquals(ErrorCampo.CorreoInvalido, e[Campo.CORREO])
        assertEquals(ErrorCampo.ContrasenaCorta, e[Campo.CONTRASENA])

        vm.onContrasenaChange("y".repeat(73))
        vm.guardar()
        assertEquals(ErrorCampo.ContrasenaLarga, estado.formulario!!.errores[Campo.CONTRASENA])
    }

    @Test
    fun `editar un campo borra solo su error`() {
        vm.nuevoUsuario()
        vm.guardar()
        vm.onNombreChange("Carla")
        assertFalse(Campo.NOMBRE in estado.formulario!!.errores)
        assertTrue(Campo.CORREO in estado.formulario!!.errores)
    }

    @Test
    fun `correo duplicado se muestra en el campo correo y el formulario sigue abierto`() {
        repo.crear = ResultadoUsuarios.Conflicto("Ya existe", mapOf("correo" to "Ya está registrado"))
        llenarNuevo()

        val f = estado.formulario!!
        assertEquals(ErrorCampo.Servidor("Ya está registrado"), f.errores[Campo.CORREO])
        assertNull(f.error)
        assertFalse(f.guardando)
    }

    @Test
    fun `sin conexion al guardar conserva lo escrito y muestra el error`() {
        repo.crear = ResultadoUsuarios.SinConexion
        llenarNuevo()

        val f = estado.formulario!!
        assertEquals(ErrorUsuarios.SinConexion, f.error)
        assertEquals("Carla", f.nombre)
        assertEquals("clave-segura", f.contrasena)
    }

    // ------------------------------------------------------------ editar

    @Test
    fun `editar abre el formulario con los datos y contrasena vacia`() {
        vm.editarUsuario(ana)
        val f = estado.formulario!!
        assertEquals("Ana", f.nombre)
        assertEquals(Rol.MESERO, f.rol)
        assertEquals("", f.contrasena)
    }

    @Test
    fun `editar pide confirmar y solo guarda al confirmar`() {
        val editada = ana.copy(rol = Rol.CAJERO)
        repo.editar = ResultadoUsuarios.Exito(editada)
        vm.editarUsuario(ana)
        vm.onRolChange(Rol.CAJERO)

        vm.guardar()
        assertTrue(estado.formulario!!.confirmando)
        assertEquals(0, repo.llamadasEditar)

        vm.confirmarCambios()
        assertNull(estado.formulario)
        assertEquals(editada, estado.usuarios.first { it.id == 1L }) // la lista cambió sin recargar
        assertEquals(DatosUsuario("Ana", "ana@appresso.test", null, Rol.CAJERO), repo.ultimosDatos)
    }

    @Test
    fun `cancelar la confirmacion vuelve al formulario sin guardar`() {
        vm.editarUsuario(ana)
        vm.onRolChange(Rol.COCINERO)
        vm.guardar()
        vm.cancelarConfirmacion()

        assertFalse(estado.formulario!!.confirmando)
        assertEquals(0, repo.llamadasEditar)
    }

    @Test
    fun `editar sin cambiar nada cierra sin llamar al servidor`() {
        vm.editarUsuario(ana)
        vm.guardar()

        assertNull(estado.formulario)
        assertEquals(0, repo.llamadasEditar)
    }

    @Test
    fun `cambios detecta cada campo y la contrasena escrita`() {
        val f = FormularioUiState(editando = ana, nombre = "Ana M", correo = ana.correo, contrasena = "nueva-clave", rol = Rol.MESERO)
        assertEquals(setOf(Campo.NOMBRE, Campo.CONTRASENA), UsuariosViewModel.cambios(f, ana))
    }

    @Test
    fun `una edicion con 409 de regla muestra el mensaje del servidor`() {
        repo.editar = ResultadoUsuarios.Conflicto("No puedes cambiar tu propio rol.", emptyMap())
        vm.editarUsuario(ana)
        vm.onRolChange(Rol.CAJERO)
        vm.guardar()
        vm.confirmarCambios()

        assertEquals(ErrorUsuarios.Regla("No puedes cambiar tu propio rol."), estado.formulario!!.error)
    }

    // ------------------------------------------------------------ desactivar / activar

    @Test
    fun `desactivar pide confirmacion y no llama hasta confirmar`() {
        vm.pedirDesactivar(ana)
        assertEquals(ana, estado.confirmarDesactivar)
        assertEquals(0, repo.llamadasDesactivar)

        vm.cancelarDesactivar()
        assertNull(estado.confirmarDesactivar)
        assertEquals(0, repo.llamadasDesactivar)
    }

    @Test
    fun `confirmar desactivacion actualiza la fila sin recargar`() {
        repo.desactivar = ResultadoUsuarios.Exito(ana.copy(activo = false))
        val listados = repo.listados

        vm.pedirDesactivar(ana)
        vm.confirmarDesactivar()

        assertFalse(estado.usuarios.first { it.id == 1L }.activo)
        assertNull(estado.confirmarDesactivar)
        assertNull(estado.procesandoId)
        assertEquals(listados, repo.listados)
    }

    @Test
    fun `desactivarse a si mismo muestra el mensaje del servidor y la fila no cambia`() {
        repo.desactivar = ResultadoUsuarios.Conflicto("No puedes desactivar tu propia cuenta.", emptyMap())

        vm.pedirDesactivar(ana)
        vm.confirmarDesactivar()

        assertEquals(ErrorUsuarios.Regla("No puedes desactivar tu propia cuenta."), estado.errorAccion)
        assertTrue(estado.usuarios.first { it.id == 1L }.activo)
        vm.descartarError()
        assertNull(estado.errorAccion)
    }

    @Test
    fun `si la cuenta ya no existe se muestra el aviso y se recarga la lista`() {
        repo.desactivar = ResultadoUsuarios.NoEncontrado
        val listados = repo.listados

        vm.pedirDesactivar(ana)
        vm.confirmarDesactivar()

        assertEquals(ErrorUsuarios.NoEncontrado, estado.errorAccion)
        assertEquals(listados + 1, repo.listados)
    }

    @Test
    fun `activar actualiza la fila`() {
        val inactiva = ana.copy(activo = false)
        repo.lista = ResultadoUsuarios.Exito(listOf(inactiva))
        vm.cargar()
        repo.activar = ResultadoUsuarios.Exito(ana)

        vm.activar(inactiva)

        assertTrue(estado.usuarios.single().activo)
    }

    // ------------------------------------------------------------ auxiliares

    private fun llenarNuevo() {
        vm.nuevoUsuario()
        vm.onNombreChange("Carla")
        vm.onCorreoChange("carla@appresso.test")
        vm.onContrasenaChange("clave-segura")
        vm.onRolChange(Rol.COCINERO)
        vm.guardar()
    }

    private class RepositorioFalso : UsuariosRepository {
        var lista: ResultadoUsuarios<List<Usuario>> = ResultadoUsuarios.Exito(emptyList())
        var crear: ResultadoUsuarios<Usuario> = ResultadoUsuarios.ErrorDelServidor
        var editar: ResultadoUsuarios<Usuario> = ResultadoUsuarios.ErrorDelServidor
        var desactivar: ResultadoUsuarios<Usuario> = ResultadoUsuarios.ErrorDelServidor
        var activar: ResultadoUsuarios<Usuario> = ResultadoUsuarios.ErrorDelServidor
        var listados = 0
        var llamadasCrear = 0
        var llamadasEditar = 0
        var llamadasDesactivar = 0
        var ultimosDatos: DatosUsuario? = null

        override suspend fun listar(): ResultadoUsuarios<List<Usuario>> { listados++; return lista }
        override suspend fun crear(datos: DatosUsuario): ResultadoUsuarios<Usuario> {
            llamadasCrear++; ultimosDatos = datos; return crear
        }
        override suspend fun editar(id: Long, datos: DatosUsuario): ResultadoUsuarios<Usuario> {
            llamadasEditar++; ultimosDatos = datos; return editar
        }
        override suspend fun desactivar(id: Long): ResultadoUsuarios<Usuario> { llamadasDesactivar++; return desactivar }
        override suspend fun activar(id: Long): ResultadoUsuarios<Usuario> = activar
    }
}
