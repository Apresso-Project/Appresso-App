package com.appresso.app.ui.cocina

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.cocina.CocinaRepository
import com.appresso.app.data.cocina.ComandaCocina
import com.appresso.app.data.cocina.EstadoCocina
import com.appresso.app.data.cocina.ItemCocina
import com.appresso.app.tiemporeal.CambioCocina
import com.appresso.app.tiemporeal.MedidorCocina
import java.time.Instant
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

    private val cambio = CambioCocina("COMANDA_ESTADO_CAMBIADO", null, Instant.parse("2026-11-22T20:15:03Z"))

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = RepositorioFalso()
    private lateinit var vm: CocinaViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(dispatcher)
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

    // ------------------------------------------------------------ medición (SCRUM-93)

    private fun t(ms: Long) = Instant.ofEpochMilli(1_000_000L + ms)

    @Test
    fun `con medidor cada cambio se mide cuando la lista nueva esta en pantalla`() {
        var ahora = t(400)
        val medidor = MedidorCocina(reloj = { ahora })
        val conMedidor = CocinaViewModel(repo, medidor)

        conMedidor.alCambiar(CambioCocina("COMANDA_ESTADO_CAMBIADO", t(0), t(150)))

        assertEquals(1, medidor.cantidad())
        assertEquals(400L, medidor.muestras().single().totalMs)
        assertEquals(250L, medidor.muestras().single().actualizacionMs)
        ahora = t(900) // no se vuelve a medir por sí solo
        assertEquals(1, medidor.cantidad())
    }

    @Test
    fun `un cambio que llega durante la consulta se mide despues de la consulta repetida`() {
        var ahora = t(200)
        val medidor = MedidorCocina(reloj = { ahora })
        val primera = CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>()
        repo.enEspera = primera
        val otro = CocinaViewModel(repo, medidor)

        otro.alCambiar(CambioCocina("COMANDA_MODIFICADA", t(0), t(100)))
        assertEquals(0, medidor.cantidad()) // todavía no está en pantalla

        repo.enEspera = null
        ahora = t(700)
        primera.complete(ResultadoCatalogo.Exito(listOf(comanda(1))))

        // La primera consulta no lo cubría; la repetida sí, y a esa hora se mide.
        assertEquals(1, medidor.cantidad())
        assertEquals(700L, medidor.muestras().single().totalMs)
    }

    @Test
    fun `si la actualizacion falla el cambio no se mide`() {
        val medidor = MedidorCocina()
        val conMedidor = CocinaViewModel(repo, medidor)
        repo.resultado = ResultadoCatalogo.SinConexion

        conMedidor.alCambiar(CambioCocina("COMANDA_ESTADO_CAMBIADO", t(0), t(10)))

        assertEquals(0, medidor.cantidad())
    }

    @Test
    fun `sin medidor alCambiar funciona igual`() {
        vm.alCambiar(cambio)

        assertEquals(2, repo.consultas)
    }

    // ------------------------------------------------------------ revisión del PR #25

    @Test
    fun `nunca hay dos consultas a la vez aunque lleguen varios cambios seguidos`() {
        val espera = CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>()
        repo.enEspera = espera
        val otro = CocinaViewModel(repo)

        repeat(5) { otro.alCambiar(cambio) }
        repo.enEspera = null
        espera.complete(ResultadoCatalogo.Exito(listOf(comanda(1))))

        assertEquals(1, repo.maxActivas)
    }

    @Test
    fun `una respuesta vieja no pisa la nueva`() {
        val vieja = CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>()
        repo.enEspera = vieja
        val otro = CocinaViewModel(repo)
        otro.alCambiar(cambio) // llega un cambio mientras la consulta vieja sigue en curso

        repo.enEspera = null
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(1, EstadoCocina.LISTA)))
        vieja.complete(ResultadoCatalogo.Exito(listOf(comanda(1, EstadoCocina.PENDIENTE))))

        assertEquals(EstadoCocina.LISTA, otro.estado.value.comandas.single().estado)
    }

    @Test
    fun `si una actualizacion en silencio falla avisa que esta desactualizada y reintenta sola con esperas crecientes`() {
        repo.resultado = ResultadoCatalogo.SinConexion
        vm.alCambiar(cambio)
        assertTrue(estado.desactualizada)
        assertEquals(listOf(1L, 2L), estado.comandas.map { it.id }) // conserva la última lista
        assertEquals(2, repo.consultas)

        dispatcher.scheduler.advanceTimeBy(999)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repo.consultas) // aún no
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals(3, repo.consultas) // primer reintento a 1 s; sigue fallando

        dispatcher.scheduler.advanceTimeBy(1_999)
        dispatcher.scheduler.runCurrent()
        assertEquals(3, repo.consultas) // la siguiente espera es de 2 s
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(3, EstadoCocina.LISTA)))
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(4, repo.consultas)
        assertFalse(estado.desactualizada)
        assertEquals(listOf(3L), estado.comandas.map { it.id })

        dispatcher.scheduler.advanceTimeBy(120_000) // ya no reintenta
        dispatcher.scheduler.runCurrent()
        assertEquals(4, repo.consultas)
    }

    @Test
    fun `un error del servidor tambien se reintenta`() {
        repo.resultado = ResultadoCatalogo.ErrorDelServidor
        vm.alCambiar(cambio)
        assertTrue(estado.desactualizada)

        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(7)))
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()

        assertFalse(estado.desactualizada)
        assertEquals(listOf(7L), estado.comandas.map { it.id })
    }

    @Test
    fun `un evento nuevo cancela la espera y consulta de inmediato`() {
        repo.resultado = ResultadoCatalogo.SinConexion
        vm.alCambiar(cambio)
        val antes = repo.consultas
        repo.resultado = ResultadoCatalogo.Exito(listOf(comanda(8)))

        vm.alCambiar(cambio)

        assertEquals(antes + 1, repo.consultas)
        assertFalse(estado.desactualizada)
        dispatcher.scheduler.advanceTimeBy(60_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(antes + 1, repo.consultas) // el reintento anterior ya no corre
    }

    @Test
    fun `un fallo que no se arregla reintentando no se reintenta ni marca desactualizada`() {
        repo.resultado = ResultadoCatalogo.SinPermiso("No")
        vm.alCambiar(cambio)
        val antes = repo.consultas

        dispatcher.scheduler.advanceTimeBy(120_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(antes, repo.consultas)
        assertFalse(estado.desactualizada)
    }

    @Test
    fun `un fallo de la primera carga no reintenta solo, muestra el error con su boton`() {
        repo.resultado = ResultadoCatalogo.SinConexion
        val otro = CocinaViewModel(repo)
        val antes = repo.consultas

        dispatcher.scheduler.advanceTimeBy(120_000)
        dispatcher.scheduler.runCurrent()

        assertEquals(ErrorCocina.SinConexion, otro.estado.value.error)
        assertEquals(antes, repo.consultas)
    }

    private class RepositorioFalso : CocinaRepository {
        var resultado: ResultadoCatalogo<List<ComandaCocina>> = ResultadoCatalogo.Exito(emptyList())
        var enEspera: CompletableDeferred<ResultadoCatalogo<List<ComandaCocina>>>? = null
        var consultas = 0
        var activas = 0
        var maxActivas = 0

        override suspend fun consultar(): ResultadoCatalogo<List<ComandaCocina>> {
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
