package com.appresso.app.ui.pagos

import com.appresso.app.data.auth.Rol
import com.appresso.app.data.pagos.MedioPago
import com.appresso.app.data.pagos.PagoConfirmado
import com.appresso.app.data.pagos.PagosRepository
import com.appresso.app.data.pagos.ResultadoPago
import java.math.BigDecimal
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagoViewModelTest {

    private val repo = RepositorioFalso()

    @Before
    fun iniciar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun detener() = Dispatchers.resetMain()

    private fun vm(rol: Rol, comandaId: Long = 17, total: BigDecimal? = BigDecimal("12500")) =
        PagoViewModel(comandaId, "Mesa 3", total, rol, repo, nuevoIdentificador = { "operacion-1" })

    private fun pago(medio: MedioPago, valor: String = "12500.00") = PagoConfirmado(
        id = 5, comandaId = 17, mesa = "Mesa 3", valor = BigDecimal(valor), medio = medio,
        fecha = Instant.parse("2026-11-18T15:42:10Z"), confirmadoPorNombre = "Ana", identificadorOperacion = "operacion-1",
    )

    // ------------------------------------------------------------ medios según el rol

    @Test
    fun `el cajero y el administrador ven efectivo y transferencia y no tienen ninguno elegido`() {
        for (rol in listOf(Rol.CAJERO, Rol.ADMINISTRADOR)) {
            val estado = vm(rol).estado.value
            assertEquals(listOf(MedioPago.EFECTIVO, MedioPago.TRANSFERENCIA), estado.medios)
            assertNull(estado.medio)
        }
    }

    @Test
    fun `el mesero solo ve transferencia y ya viene elegida`() {
        val estado = vm(Rol.MESERO).estado.value

        assertEquals(listOf(MedioPago.TRANSFERENCIA), estado.medios)
        assertEquals(MedioPago.TRANSFERENCIA, estado.medio)
    }

    @Test
    fun `cocinero y cliente no tienen ningun medio`() {
        for (rol in listOf(Rol.COCINERO, Rol.CLIENTE)) {
            assertTrue(vm(rol).estado.value.medios.isEmpty())
        }
    }

    @Test
    fun `el mesero no puede elegir efectivo`() {
        val v = vm(Rol.MESERO)

        v.seleccionarMedio(MedioPago.EFECTIVO)

        assertEquals(MedioPago.TRANSFERENCIA, v.estado.value.medio)
    }

    // ------------------------------------------------------------ confirmar

    @Test
    fun `sin medio elegido no se puede pedir confirmacion ni confirmar`() {
        val v = vm(Rol.CAJERO)

        v.pedirConfirmacion()
        v.confirmar()

        assertFalse(v.estado.value.confirmando)
        assertTrue(repo.llamadas.isEmpty())
    }

    @Test
    fun `elegir el medio y pedir confirmacion abre la pregunta sin enviar nada`() {
        val v = vm(Rol.CAJERO)

        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.pedirConfirmacion()

        assertTrue(v.estado.value.confirmando)
        assertTrue(repo.llamadas.isEmpty())
    }

    @Test
    fun `cancelar la pregunta no envia nada`() {
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.pedirConfirmacion()

        v.cancelarConfirmacion()

        assertFalse(v.estado.value.confirmando)
        assertTrue(repo.llamadas.isEmpty())
    }

    @Test
    fun `confirmar envia comanda medio e identificador y deja el pago registrado`() {
        repo.respuestas += ResultadoPago.Exito(pago(MedioPago.EFECTIVO), repetido = false)
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.pedirConfirmacion()

        v.confirmar()

        assertEquals(listOf(Triple(17L, MedioPago.EFECTIVO, "operacion-1")), repo.llamadas)
        val estado = v.estado.value
        assertNotNull(estado.pagado)
        assertEquals(MedioPago.EFECTIVO, estado.pagado!!.medio)
        assertFalse(estado.procesando)
        assertFalse(estado.confirmando)
        assertFalse(estado.yaEstabaRegistrado)
        assertNull(estado.error)
    }

    @Test
    fun `el mesero confirma una transferencia`() {
        repo.respuestas += ResultadoPago.Exito(pago(MedioPago.TRANSFERENCIA), repetido = false)
        val v = vm(Rol.MESERO)

        v.pedirConfirmacion()
        v.confirmar()

        assertEquals(listOf(Triple(17L, MedioPago.TRANSFERENCIA, "operacion-1")), repo.llamadas)
        assertNotNull(v.estado.value.pagado)
    }

    @Test
    fun `un reintento ya registrado se marca como tal`() {
        repo.respuestas += ResultadoPago.Exito(pago(MedioPago.EFECTIVO), repetido = true)
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()

        assertTrue(v.estado.value.yaEstabaRegistrado)
    }

    @Test
    fun `una doble pulsacion envia un solo pago`() {
        val espera = CompletableDeferred<ResultadoPago>()
        repo.pendiente = espera
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()
        assertTrue(v.estado.value.procesando)
        v.confirmar()
        v.pedirConfirmacion()
        espera.complete(ResultadoPago.Exito(pago(MedioPago.EFECTIVO), repetido = false))

        assertEquals(1, repo.llamadas.size)
        assertNotNull(v.estado.value.pagado)
    }

    @Test
    fun `mientras se envia no se puede cambiar el medio`() {
        repo.pendiente = CompletableDeferred()
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.confirmar()

        v.seleccionarMedio(MedioPago.TRANSFERENCIA)

        assertEquals(MedioPago.EFECTIVO, v.estado.value.medio)
    }

    @Test
    fun `una vez pagado no se vuelve a enviar`() {
        repo.respuestas += ResultadoPago.Exito(pago(MedioPago.EFECTIVO), repetido = false)
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.confirmar()

        v.confirmar()
        v.pedirConfirmacion()

        assertEquals(1, repo.llamadas.size)
        assertFalse(v.estado.value.confirmando)
    }

    // ------------------------------------------------------------ errores

    @Test
    fun `un conflicto muestra el mensaje del servidor y no deja el pago registrado`() {
        repo.respuestas += ResultadoPago.Conflicto("Esta comanda ya fue pagada.")
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()

        val estado = v.estado.value
        assertEquals(ErrorPago.Conflicto("Esta comanda ya fue pagada."), estado.error)
        assertNull(estado.pagado)
        assertFalse(estado.procesando)
    }

    @Test
    fun `un 403 muestra su mensaje`() {
        repo.respuestas += ResultadoPago.SinPermiso("No tienes permiso para realizar esta acción.")
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()

        assertEquals(ErrorPago.SinPermiso("No tienes permiso para realizar esta acción."), v.estado.value.error)
    }

    @Test
    fun `un 401 no muestra error porque el cliente ya cerro la sesion`() {
        repo.respuestas += ResultadoPago.SesionInvalida
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()

        val estado = v.estado.value
        assertNull(estado.error)
        assertNull(estado.pagado)
        assertFalse(estado.procesando)
    }

    @Test
    fun `los demas errores se traducen`() {
        val casos = listOf(
            ResultadoPago.NoEncontrada to ErrorPago.NoEncontrada,
            ResultadoPago.SinConexion to ErrorPago.SinConexion,
            ResultadoPago.ErrorDelServidor to ErrorPago.ErrorDelServidor,
            ResultadoPago.DatosInvalidos("x") to ErrorPago.DatosInvalidos("x"),
        )
        for ((resultado, esperado) in casos) {
            repo.llamadas.clear()
            repo.respuestas += resultado
            val v = vm(Rol.CAJERO)
            v.seleccionarMedio(MedioPago.EFECTIVO)

            v.confirmar()

            assertEquals(esperado, v.estado.value.error)
        }
    }

    @Test
    fun `tras un fallo de red se reintenta con el mismo identificador`() {
        repo.respuestas += ResultadoPago.SinConexion
        repo.respuestas += ResultadoPago.Exito(pago(MedioPago.EFECTIVO), repetido = true)
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)

        v.confirmar()
        assertEquals(ErrorPago.SinConexion, v.estado.value.error)
        v.confirmar()

        assertEquals(2, repo.llamadas.size)
        assertEquals(repo.llamadas[0], repo.llamadas[1])
        assertNotNull(v.estado.value.pagado)
        assertNull(v.estado.value.error)
    }

    @Test
    fun `elegir otro medio o descartar quita el error`() {
        repo.respuestas += ResultadoPago.ErrorDelServidor
        val v = vm(Rol.CAJERO)
        v.seleccionarMedio(MedioPago.EFECTIVO)
        v.confirmar()

        v.seleccionarMedio(MedioPago.TRANSFERENCIA)

        assertNull(v.estado.value.error)
    }

    // ------------------------------------------------------------ utilidades de la pantalla

    @Test
    fun `el numero de comanda solo acepta enteros positivos`() {
        assertEquals(17L, numeroDeComanda(" 17 "))
        assertNull(numeroDeComanda(""))
        assertNull(numeroDeComanda("0"))
        assertNull(numeroDeComanda("-3"))
        assertNull(numeroDeComanda("abc"))
        assertNull(numeroDeComanda("1.5"))
    }

    @Test
    fun `los pesos se muestran con separador de miles`() {
        assertTrue(formatoPesos(BigDecimal("12500.00")).contains("12.500"))
    }

    private class RepositorioFalso : PagosRepository {
        val respuestas = ArrayDeque<ResultadoPago>()
        val llamadas = mutableListOf<Triple<Long, MedioPago, String>>()

        /** Si se asigna, la siguiente llamada espera a que se complete (para simular una petición en curso). */
        var pendiente: CompletableDeferred<ResultadoPago>? = null

        override suspend fun confirmar(comandaId: Long, medio: MedioPago, identificadorOperacion: String): ResultadoPago {
            llamadas += Triple(comandaId, medio, identificadorOperacion)
            pendiente?.let { return it.await() }
            return respuestas.removeFirst()
        }
    }
}
