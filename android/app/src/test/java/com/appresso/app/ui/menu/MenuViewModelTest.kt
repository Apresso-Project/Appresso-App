package com.appresso.app.ui.menu

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.menu.Disponibilidad
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuProducto
import com.appresso.app.data.menu.MenuRepository
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
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
class MenuViewModelTest {

    private fun producto(id: Long, nombre: String, existencias: Int = 10, pocas: Boolean = false) = MenuProducto(
        id = id,
        nombre = nombre,
        descripcion = null,
        precio = BigDecimal("6500"),
        imagenUrl = null,
        existencias = existencias,
        disponibilidad = if (pocas) Disponibilidad.POCAS_UNIDADES else Disponibilidad.DISPONIBLE,
    )

    private val bebidas = MenuCategoria(1, "Bebidas", listOf(producto(1, "Latte"), producto(2, "Té", 2, pocas = true)))
    private val postres = MenuCategoria(2, "Postres", listOf(producto(3, "Brownie")))

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = RepositorioFalso()
    private lateinit var vm: MenuViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(dispatcher)
        repo.resultado = ResultadoCatalogo.Exito(listOf(bebidas, postres))
        vm = MenuViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    @Test
    fun `al abrir carga el menu`() {
        assertEquals(listOf("Bebidas", "Postres"), estado.categorias.map { it.nombre })
        assertEquals(1, repo.consultas)
        assertFalse(estado.cargando)
        assertNull(estado.error)
    }

    @Test
    fun `mientras carga el estado es cargando y al llegar se apaga`() = runBlocking {
        val espera = CompletableDeferred<ResultadoCatalogo<List<MenuCategoria>>>()
        repo.enEspera = espera
        val otro = MenuViewModel(repo)
        assertTrue(otro.estado.value.cargando)

        espera.complete(ResultadoCatalogo.Exito(listOf(postres)))

        assertFalse(otro.estado.value.cargando)
        assertEquals(listOf("Postres"), otro.estado.value.categorias.map { it.nombre })
    }

    @Test
    fun `sin filtro se ven todas las categorias y con filtro solo la elegida`() {
        assertEquals(2, estado.visibles.size)

        vm.filtrarPorCategoria(2)
        assertEquals(listOf("Postres"), estado.visibles.map { it.nombre })

        vm.filtrarPorCategoria(null)
        assertEquals(2, estado.visibles.size)
    }

    @Test
    fun `un menu vacio no es un error`() {
        repo.resultado = ResultadoCatalogo.Exito(emptyList())
        vm.cargar()

        assertTrue(estado.categorias.isEmpty())
        assertNull(estado.error)
    }

    @Test
    fun `los errores se traducen y reintentar recupera el menu`() {
        repo.resultado = ResultadoCatalogo.SinConexion
        vm.cargar()
        assertEquals(ErrorMenu.SinConexion, estado.error)

        repo.resultado = ResultadoCatalogo.ErrorDelServidor
        vm.cargar()
        assertEquals(ErrorMenu.ErrorDelServidor, estado.error)

        repo.resultado = ResultadoCatalogo.SinPermiso("No")
        vm.cargar()
        assertEquals(ErrorMenu.SinPermiso("No"), estado.error)

        repo.resultado = ResultadoCatalogo.Exito(listOf(bebidas))
        vm.cargar()
        assertNull(estado.error)
        assertEquals(1, estado.categorias.size)
    }

    @Test
    fun `una sesion invalida no muestra error porque el cliente cierra la sesion`() {
        repo.resultado = ResultadoCatalogo.SesionInvalida
        vm.cargar()

        assertNull(estado.error)
        assertFalse(estado.cargando)
    }

    @Test
    fun `la recarga en silencio actualiza precio y existencias sin indicador de carga`() {
        val actualizado = bebidas.copy(productos = listOf(producto(1, "Latte", existencias = 3, pocas = true)))
        repo.resultado = ResultadoCatalogo.Exito(listOf(actualizado))

        vm.recargarEnSilencio()

        assertFalse(estado.cargando)
        assertEquals(3, estado.categorias.single().productos.single().existencias)
        assertEquals(Disponibilidad.POCAS_UNIDADES, estado.categorias.single().productos.single().disponibilidad)
    }

    @Test
    fun `un fallo en la recarga en silencio conserva lo que ya se veia y no avisa`() {
        repo.resultado = ResultadoCatalogo.SinConexion

        vm.recargarEnSilencio()

        assertEquals(2, estado.categorias.size)
        assertNull(estado.error)
    }

    @Test
    fun `si la categoria filtrada desaparece del menu se vuelve a todas`() {
        vm.filtrarPorCategoria(2)
        repo.resultado = ResultadoCatalogo.Exito(listOf(bebidas))

        vm.recargarEnSilencio()

        assertNull(estado.filtroCategoriaId)
        assertEquals(listOf("Bebidas"), estado.visibles.map { it.nombre })
    }

    @Test
    fun `cada aviso de cambios vuelve a pedir el menu`() {
        val cambios = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val conAvisos = MenuViewModel(repo, cambios)
        val antes = repo.consultas

        cambios.tryEmit(Unit)
        cambios.tryEmit(Unit)

        assertEquals(antes + 2, repo.consultas)
        assertFalse(conAvisos.estado.value.cargando)
    }

    @Test
    fun `no lanza una segunda consulta mientras hay una en curso`() = runBlocking {
        val espera = CompletableDeferred<ResultadoCatalogo<List<MenuCategoria>>>()
        repo.enEspera = espera
        val otro = MenuViewModel(repo)
        val antes = repo.consultas

        otro.cargar()
        otro.recargarEnSilencio()

        assertEquals(antes, repo.consultas)
        espera.complete(ResultadoCatalogo.Exito(emptyList()))
        Unit
    }

    // ------------------------------------------------------------ revisión del PR #25

    @Test
    fun `un cambio de stock durante la carga inicial no se descarta y el producto agotado desaparece`() {
        val inicial = CompletableDeferred<ResultadoCatalogo<List<MenuCategoria>>>()
        repo.enEspera = inicial
        val otro = MenuViewModel(repo) // carga inicial en curso, con el stock anterior

        otro.recargarEnSilencio() // llega el evento de stock: el producto 2 se agotó

        repo.enEspera = null
        repo.resultado = ResultadoCatalogo.Exito(listOf(bebidas.copy(productos = listOf(producto(1, "Latte")))))
        inicial.complete(ResultadoCatalogo.Exito(listOf(bebidas, postres))) // respuesta armada antes del cambio

        // La consulta se repitió y la pantalla no se queda con el stock viejo.
        assertEquals(listOf(1L), otro.estado.value.categorias.single().productos.map { it.id })
        assertFalse(otro.estado.value.cargando)
    }

    @Test
    fun `las recargas en silencio no se solapan y una respuesta vieja no pisa la nueva`() {
        val vieja = CompletableDeferred<ResultadoCatalogo<List<MenuCategoria>>>()
        repo.enEspera = vieja
        vm.recargarEnSilencio() // consulta en curso (la del setUp ya terminó)
        repeat(4) { vm.recargarEnSilencio() } // llegan más cambios mientras tanto

        repo.enEspera = null
        repo.resultado = ResultadoCatalogo.Exito(listOf(postres))
        vieja.complete(ResultadoCatalogo.Exito(listOf(bebidas)))

        assertEquals(1, repo.maxActivas)
        assertEquals(listOf("Postres"), estado.categorias.map { it.nombre })
    }

    @Test
    fun `si una actualizacion en silencio falla avisa que esta desactualizado y reintenta solo`() {
        repo.resultado = ResultadoCatalogo.SinConexion
        vm.recargarEnSilencio()
        assertTrue(estado.desactualizado)
        assertEquals(2, estado.categorias.size) // conserva el último menú

        val antes = repo.consultas
        repo.resultado = ResultadoCatalogo.Exito(listOf(postres))
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(antes + 1, repo.consultas)
        assertFalse(estado.desactualizado)
        assertEquals(listOf("Postres"), estado.categorias.map { it.nombre })

        dispatcher.scheduler.advanceTimeBy(120_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(antes + 1, repo.consultas)
    }

    @Test
    fun `un fallo que no se arregla reintentando no se reintenta`() {
        repo.resultado = ResultadoCatalogo.SinPermiso("No")
        vm.recargarEnSilencio()
        val antes = repo.consultas

        dispatcher.scheduler.advanceTimeBy(120_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(antes, repo.consultas)
        assertFalse(estado.desactualizado)
    }

    private class RepositorioFalso : MenuRepository {
        var resultado: ResultadoCatalogo<List<MenuCategoria>> = ResultadoCatalogo.Exito(emptyList())
        var enEspera: CompletableDeferred<ResultadoCatalogo<List<MenuCategoria>>>? = null
        var consultas = 0
        var activas = 0
        var maxActivas = 0

        override suspend fun consultar(): ResultadoCatalogo<List<MenuCategoria>> {
            consultas++
            activas++
            maxActivas = maxOf(maxActivas, activas)
            try {
                return enEspera?.await() ?: resultado
            } finally {
                activas--
            }
        }
    }
}
