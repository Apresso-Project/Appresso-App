package com.appresso.app.ui.catalogo

import com.appresso.app.data.catalogo.CatalogoRepository
import com.appresso.app.data.catalogo.Categoria
import com.appresso.app.data.catalogo.DatosProducto
import com.appresso.app.data.catalogo.Producto
import com.appresso.app.data.catalogo.ResultadoCatalogo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriasViewModelTest {

    private val bebidas = Categoria(1, "Bebidas", true, 3)
    private val postres = Categoria(2, "postres", true, 0)
    private val panes = Categoria(3, "Panes", false, 2)

    private val repo = RepositorioFalso()
    private lateinit var vm: CategoriasViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.lista = ResultadoCatalogo.Exito(listOf(postres, panes, bebidas))
        vm = CategoriasViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    // ------------------------------------------------------------ lista

    @Test
    fun `al abrir carga todas las categorias incluidas las desactivadas y las ordena`() {
        assertEquals(listOf(bebidas, panes, postres), estado.categorias)
        assertFalse(estado.cargando)
        assertEquals(listOf(true), repo.pidioInactivas)
    }

    @Test
    fun `si falla la carga muestra el error y reintentar lo recupera`() {
        repo.lista = ResultadoCatalogo.ErrorDelServidor
        vm.cargar()
        assertEquals(ErrorCategorias.ErrorDelServidor, estado.errorLista)

        repo.lista = ResultadoCatalogo.Exito(listOf(bebidas))
        vm.cargar()
        assertNull(estado.errorLista)
        assertEquals(listOf(bebidas), estado.categorias)
    }

    @Test
    fun `un 401 no muestra error porque la sesion ya se cerro`() {
        repo.lista = ResultadoCatalogo.SesionInvalida
        vm.cargar()
        assertNull(estado.errorLista)
    }

    // ------------------------------------------------------------ crear

    @Test
    fun `crear una categoria la agrega a la lista y cierra el formulario`() {
        repo.creada = ResultadoCatalogo.Exito(Categoria(9, "Almuerzos", true, 0))
        vm.nuevaCategoria()
        vm.onNombreChange("  Almuerzos ")
        vm.guardar()

        assertEquals(listOf("Almuerzos"), repo.nombresEnviados)
        assertNull(estado.formulario)
        assertEquals(listOf("Almuerzos", "Bebidas", "Panes", "postres"), estado.categorias.map { it.nombre })
    }

    @Test
    fun `un nombre vacio no llama al servidor`() {
        vm.nuevaCategoria()
        vm.onNombreChange("   ")
        vm.guardar()
        assertEquals(ErrorNombre.Obligatorio, estado.formulario?.errorNombre)
        assertEquals(emptyList<String>(), repo.nombresEnviados)
    }

    @Test
    fun `un nombre de mas de 80 caracteres no llama al servidor`() {
        vm.nuevaCategoria()
        vm.onNombreChange("a".repeat(81))
        vm.guardar()
        assertEquals(ErrorNombre.DemasiadoLargo, estado.formulario?.errorNombre)
        assertEquals(emptyList<String>(), repo.nombresEnviados)
    }

    @Test
    fun `un nombre repetido ignorando mayusculas se avisa sin llamar al servidor`() {
        vm.nuevaCategoria()
        vm.onNombreChange("BEBIDAS")
        vm.guardar()
        assertEquals(ErrorNombre.Repetido, estado.formulario?.errorNombre)
        assertEquals(emptyList<String>(), repo.nombresEnviados)
    }

    @Test
    fun `escribir de nuevo limpia el error del campo`() {
        vm.nuevaCategoria()
        vm.guardar()
        assertNotNull(estado.formulario?.errorNombre)
        vm.onNombreChange("Dulces")
        assertNull(estado.formulario?.errorNombre)
    }

    @Test
    fun `el conflicto del servidor sobre el nombre se muestra en el campo y conserva el texto`() {
        repo.creada = ResultadoCatalogo.Conflicto("Ya existe", mapOf("nombre" to "Ya existe una categoría con ese nombre."))
        vm.nuevaCategoria()
        vm.onNombreChange("Dulces")
        vm.guardar()

        val f = estado.formulario!!
        assertEquals(ErrorNombre.Servidor("Ya existe una categoría con ese nombre."), f.errorNombre)
        assertEquals("Dulces", f.nombre)
        assertNull(f.error)
        assertFalse(f.guardando)
    }

    @Test
    fun `un 403 al guardar no repite el aviso que ya mostro el cliente HTTP`() {
        repo.creada = ResultadoCatalogo.SinPermiso("No puedes")
        vm.nuevaCategoria()
        vm.onNombreChange("Dulces")
        vm.guardar()
        assertNull(estado.formulario?.error)
        assertFalse(estado.formulario!!.guardando)
    }

    @Test
    fun `un error del servidor al guardar queda en el formulario`() {
        repo.creada = ResultadoCatalogo.ErrorDelServidor
        vm.nuevaCategoria()
        vm.onNombreChange("Dulces")
        vm.guardar()
        assertEquals(ErrorCategorias.ErrorDelServidor, estado.formulario?.error)
    }

    @Test
    fun `no se guarda dos veces mientras hay una peticion en curso`() {
        val espera = CompletableDeferred<ResultadoCatalogo<Categoria>>()
        repo.creadaEnEspera = espera
        vm.nuevaCategoria()
        vm.onNombreChange("Dulces")
        vm.guardar()
        vm.guardar()
        assertEquals(listOf("Dulces"), repo.nombresEnviados)
        assertEquals(true, estado.formulario?.guardando)

        vm.cerrarFormulario() // ignorado mientras guarda
        assertNotNull(estado.formulario)

        espera.complete(ResultadoCatalogo.Exito(Categoria(9, "Dulces", true, 0)))
        assertNull(estado.formulario)
    }

    // ------------------------------------------------------------ renombrar

    @Test
    fun `renombrar abre el formulario con el nombre actual y actualiza la lista`() {
        repo.renombrada = ResultadoCatalogo.Exito(Categoria(1, "Bebidas frías", true, 3))
        vm.renombrarCategoria(bebidas)
        assertEquals("Bebidas", estado.formulario?.nombre)

        vm.onNombreChange("Bebidas frías")
        vm.guardar()

        assertEquals(listOf(1L to "Bebidas frías"), repo.renombrados)
        assertEquals("Bebidas frías", estado.categorias.first { it.id == 1L }.nombre)
        assertEquals(3, estado.categorias.size)
        assertNull(estado.formulario)
    }

    @Test
    fun `renombrar con el mismo nombre cierra sin llamar al servidor`() {
        vm.renombrarCategoria(bebidas)
        vm.guardar()
        assertNull(estado.formulario)
        assertEquals(emptyList<Pair<Long, String>>(), repo.renombrados)
    }

    @Test
    fun `renombrar solo cambiando mayusculas no cuenta como repetido consigo misma`() {
        repo.renombrada = ResultadoCatalogo.Exito(Categoria(1, "BEBIDAS", true, 3))
        vm.renombrarCategoria(bebidas)
        vm.onNombreChange("BEBIDAS")
        vm.guardar()
        assertEquals(listOf(1L to "BEBIDAS"), repo.renombrados)
    }

    @Test
    fun `si la categoria ya no existe avisa y recarga la lista`() {
        repo.renombrada = ResultadoCatalogo.NoEncontrado
        repo.lista = ResultadoCatalogo.Exito(listOf(postres))
        vm.renombrarCategoria(bebidas)
        vm.onNombreChange("Otra")
        vm.guardar()

        assertEquals(ErrorCategorias.NoEncontrada, estado.formulario?.error)
        assertEquals(listOf(postres), estado.categorias)
    }

    // ------------------------------------------------------------ desactivar / activar

    @Test
    fun `desactivar pide confirmacion y no llama al servidor hasta confirmar`() {
        vm.pedirDesactivar(bebidas)
        assertEquals(bebidas, estado.confirmarDesactivar)
        assertEquals(emptyList<Long>(), repo.desactivadas)

        vm.cancelarDesactivar()
        assertNull(estado.confirmarDesactivar)
        assertEquals(emptyList<Long>(), repo.desactivadas)
    }

    @Test
    fun `confirmar desactiva la categoria y actualiza la lista con la respuesta`() {
        repo.desactivada = ResultadoCatalogo.Exito(bebidas.copy(activa = false))
        vm.pedirDesactivar(bebidas)
        vm.confirmarDesactivar()

        assertEquals(listOf(1L), repo.desactivadas)
        assertNull(estado.confirmarDesactivar)
        assertNull(estado.procesandoId)
        assertFalse(estado.categorias.first { it.id == 1L }.activa)
    }

    @Test
    fun `activar no pide confirmacion`() {
        repo.activada = ResultadoCatalogo.Exito(panes.copy(activa = true))
        vm.activar(panes)
        assertEquals(listOf(3L), repo.activadas)
        assertEquals(true, estado.categorias.first { it.id == 3L }.activa)
    }

    @Test
    fun `un 403 al desactivar no deja mensaje propio porque el cliente ya lo mostro`() {
        repo.desactivada = ResultadoCatalogo.SinPermiso("No puedes")
        vm.pedirDesactivar(bebidas)
        vm.confirmarDesactivar()
        assertNull(estado.errorAccion)
        assertNull(estado.procesandoId)
        assertEquals(true, estado.categorias.first { it.id == 1L }.activa)
    }

    @Test
    fun `un error del servidor al desactivar queda como error de accion y se puede descartar`() {
        repo.desactivada = ResultadoCatalogo.ErrorDelServidor
        vm.pedirDesactivar(bebidas)
        vm.confirmarDesactivar()
        assertEquals(ErrorCategorias.ErrorDelServidor, estado.errorAccion)
        vm.descartarError()
        assertNull(estado.errorAccion)
    }

    @Test
    fun `mientras hay una desactivacion en curso no se acepta otra`() {
        val espera = CompletableDeferred<ResultadoCatalogo<Categoria>>()
        repo.desactivadaEnEspera = espera
        vm.pedirDesactivar(bebidas)
        vm.confirmarDesactivar()
        assertEquals(1L, estado.procesandoId)

        vm.pedirDesactivar(postres)
        assertNull(estado.confirmarDesactivar)
        vm.activar(panes)
        assertEquals(emptyList<Long>(), repo.activadas)

        espera.complete(ResultadoCatalogo.Exito(bebidas.copy(activa = false)))
        assertNull(estado.procesandoId)
    }

    // ------------------------------------------------------------ tiempo real

    @Test
    fun `un aviso de cambios recarga la lista sin mostrar el indicador de carga`() {
        val avisos = MutableSharedFlow<Unit>()
        val vmConAvisos = CategoriasViewModel(repo, avisos)
        repo.lista = ResultadoCatalogo.Exito(listOf(bebidas, Categoria(7, "Nueva", true, 0)))

        kotlinx.coroutines.runBlocking { avisos.emit(Unit) }

        assertEquals(listOf("Bebidas", "Nueva"), vmConAvisos.estado.value.categorias.map { it.nombre })
        assertFalse(vmConAvisos.estado.value.cargando)
    }

    @Test
    fun `si la recarga por aviso falla se conserva la lista y no se muestra error`() {
        val avisos = MutableSharedFlow<Unit>()
        val vmConAvisos = CategoriasViewModel(repo, avisos)
        repo.lista = ResultadoCatalogo.SinConexion

        kotlinx.coroutines.runBlocking { avisos.emit(Unit) }

        assertEquals(3, vmConAvisos.estado.value.categorias.size)
        assertNull(vmConAvisos.estado.value.errorLista)
    }

    // ------------------------------------------------------------ doble

    private class RepositorioFalso : CatalogoRepository {
        var lista: ResultadoCatalogo<List<Categoria>> = ResultadoCatalogo.Exito(emptyList())
        var creada: ResultadoCatalogo<Categoria> = ResultadoCatalogo.ErrorDelServidor
        var creadaEnEspera: CompletableDeferred<ResultadoCatalogo<Categoria>>? = null
        var renombrada: ResultadoCatalogo<Categoria> = ResultadoCatalogo.ErrorDelServidor
        var desactivada: ResultadoCatalogo<Categoria> = ResultadoCatalogo.ErrorDelServidor
        var desactivadaEnEspera: CompletableDeferred<ResultadoCatalogo<Categoria>>? = null
        var activada: ResultadoCatalogo<Categoria> = ResultadoCatalogo.ErrorDelServidor

        val pidioInactivas = mutableListOf<Boolean>()
        val nombresEnviados = mutableListOf<String>()
        val renombrados = mutableListOf<Pair<Long, String>>()
        val desactivadas = mutableListOf<Long>()
        val activadas = mutableListOf<Long>()

        override suspend fun listarCategorias(incluirInactivas: Boolean): ResultadoCatalogo<List<Categoria>> {
            pidioInactivas += incluirInactivas
            return lista
        }

        override suspend fun crearCategoria(nombre: String): ResultadoCatalogo<Categoria> {
            nombresEnviados += nombre
            return creadaEnEspera?.await() ?: creada
        }

        override suspend fun renombrarCategoria(id: Long, nombre: String): ResultadoCatalogo<Categoria> {
            renombrados += id to nombre
            return renombrada
        }

        override suspend fun desactivarCategoria(id: Long): ResultadoCatalogo<Categoria> {
            desactivadas += id
            return desactivadaEnEspera?.await() ?: desactivada
        }

        override suspend fun activarCategoria(id: Long): ResultadoCatalogo<Categoria> {
            activadas += id
            return activada
        }

        override suspend fun listarProductos(categoriaId: Long?, incluirFueraDelMenu: Boolean): ResultadoCatalogo<List<Producto>> =
            error("no se usa en estas pruebas")

        override suspend fun obtenerProducto(id: Long): ResultadoCatalogo<Producto> = error("no se usa")
        override suspend fun crearProducto(datos: DatosProducto): ResultadoCatalogo<Producto> = error("no se usa")
        override suspend fun editarProducto(id: Long, datos: DatosProducto): ResultadoCatalogo<Producto> = error("no se usa")
        override suspend fun desactivarProducto(id: Long): ResultadoCatalogo<Producto> = error("no se usa")
        override suspend fun activarProducto(id: Long): ResultadoCatalogo<Producto> = error("no se usa")
    }
}
