package com.appresso.app.ui.cocina

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.cocina.CocinaRepository
import com.appresso.app.data.cocina.ComandaCocina
import com.appresso.app.data.cocina.EstadoCocina
import com.appresso.app.data.cocina.ItemCocina
import com.appresso.app.tiemporeal.CambioCocina
import kotlinx.coroutines.CompletableDeferred
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
class CocinaViewModelTest {

    private fun comanda(id: Long, estado: EstadoCocina = EstadoCocina.PENDIENTE, mesa: String = "Mesa $id") =
        ComandaCocina(id, mesa, estado, null, listOf(ItemCocina("Latte", 1, null, agregado = false)))

    private val cambio = CambioCocina("COMANDA_ESTADO_CAMBIADO", null)

    private val repo = RepositorioFalso()
    private lateinit var vm: CocinaViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(1), comanda(2)))
        vm = CocinaViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    @Test
    fun `al abrir carga las comandas en el orden del servidor`() {
        assertEquals(listOf(1L, 2L), estado.comandas.map { it.id })
        assertEquals(1, repo.consultas)
        assertFalse(estado.cargando)
        assertNull(estado.error)
    }

    @Test
    fun `mientras carga por primera vez el estado es cargando`() {
        val espera = CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>()
        repo.enEspera = espera
        val otro = CocinaViewModel(repo)
        assertTrue(otro.estado.value.cargando)

        espera.complete(ResultadoCatalogo.Exito(listOf(comanda(5))))

        assertFalse(otro.estado.value.cargando)
        assertEquals(listOf(5L), otro.estado.value.comandas.map { it.id })
    }

    @Test
    fun `un cambio vuelve a pedir la lista y refleja el nuevo estado sin indicador de carga`() {
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(1, EstadoCocina.EN_PREPARACION), comanda(2), comanda(3)))

        vm.alCambiar(cambio)

        assertEquals(2, repo.consultas)
        assertFalse(estado.cargando)
        assertEquals(listOf(1L, 2L, 3L), estado.comandas.map { it.id })
        assertEquals(EstadoCocina.EN_PREPARACION, estado.comandas.first().estado)
    }

    @Test
    fun `una comanda cerrada desaparece de la lista`() {
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(2)))

        vm.alCambiar(cambio)

        assertEquals(listOf(2L), estado.comandas.map { it.id })
    }

    @Test
    fun `un fallo en una actualizacion en silencio conserva la lista y no avisa`() {
        repo.resultado = ResultadoCatalogo.SinConexion

        vm.alCambiar(cambio)

        assertEquals(listOf(1L, 2L), estado.comandas.map { it.id })
        assertNull(estado.error)
    }

    @Test
    fun `un cambio que llega durante una consulta la repite al terminar`() {
        val primera = CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>()
        repo.enEspera = primera
        val otro = CocinaViewModel(repo) // consulta 2: queda esperando
        val antes = repo.consultas

        otro.alCambiar(cambio) // llega mientras se consulta
        assertEquals(antes, repo.consultas) // no se lanza otra en paralelo

        repo.enEspera = null
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(9, EstadoCocina.LISTA)))
        primera.complete(ResultadoCatalogo.Exito(listOf(comanda(9))))

        // La segunda consulta (la repetida) deja la lista con el estado más nuevo.
        assertEquals(antes + 1, repo.consultas)
        assertEquals(EstadoCocina.LISTA, otro.estado.value.comandas.single().estado)
        assertFalse(otro.estado.value.cargando)
    }

    @Test
    fun `los errores de la primera carga se traducen y reintentar recupera la lista`() {
        repo.resultado = ResultadoCatalogo.ErrorDelServidor
        val otro = CocinaViewModel(repo)
        assertEquals(ErrorCocina.ErrorDelServidor, otro.estado.value.error)

        repo.resultado = ResultadoCatalogo.SinConexion
        otro.cargar()
        assertEquals(ErrorCocina.SinConexion, otro.estado.value.error)

        repo.resultado = ResultadoCatalogo.SinPermiso("No")
        otro.cargar()
        assertEquals(ErrorCocina.SinPermiso("No"), otro.estado.value.error)

        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(1)))
        otro.cargar()
        assertNull(otro.estado.value.error)
        assertEquals(1, otro.estado.value.comandas.size)
    }

    @Test
    fun `una sesion invalida no muestra error porque el cliente cierra la sesion`() {
        repo.resultado = ResultadoCatalogo.SesionInvalida
        val otro = CocinaViewModel(repo)

        assertNull(otro.estado.value.error)
        assertFalse(otro.estado.value.cargando)
    }

    @Test
    fun `una lista vacia no es un error`() {
        repo.resultado = ResultadoCatalogo.Exito(emptyList())
        vm.alCambiar(cambio)

        assertTrue(estado.comandas.isEmpty())
        assertNull(estado.error)
    }

    private class RepositorioFalso : CocinaRepository {
        var resultado: ResultadoCatalogo<List<ComandaCocina>> = ResultadoCatalogo.Exito(emptyList())
        var enEspera: CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>? = null
        var consultas = 0

        override suspend fun consultar(): ResultadoCatalogo<List<ComandaCocina>> {
            consultas++
            return enEspera?.await() ?: resultado
        }
    }
}
