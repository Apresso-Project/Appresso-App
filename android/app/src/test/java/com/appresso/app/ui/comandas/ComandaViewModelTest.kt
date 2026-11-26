package com.appresso.app.ui.comandas

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.comandas.EstadoComanda
import com.appresso.app.data.comandas.ItemPedido
import com.appresso.app.data.menu.Disponibilidad
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuProducto
import java.math.BigDecimal
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
class ComandaViewModelTest {

    private fun producto(id: Long, nombre: String, precio: String, existencias: Int = 10) =
        MenuProducto(id, nombre, null, BigDecimal(precio), null, existencias, Disponibilidad.DISPONIBLE)

    private val latte = producto(7, "Latte", "6500")
    private val brownie = producto(9, "Brownie", "4500.50", existencias = 2)
    private val bebidas = MenuCategoria(1, "Bebidas", listOf(latte))
    private val postres = MenuCategoria(2, "Postres", listOf(brownie))

    private val menu = MenuFalso(ResultadoCatalogo.Exito(listOf(bebidas, postres)))
    private val comandas = ComandasFalso()
    private lateinit var vm: ComandaViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ComandaViewModel(mesaId = 3, mesaNombre = "Mesa 3", menu = menu, comandas = comandas)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value
    private fun pesos(valor: String) = BigDecimal(valor).setScale(2)

    // ---------------------------------------------------------------- armar

    @Test
    fun `al abrir carga los productos del menu y la comanda empieza vacia`() {
        assertEquals(listOf(bebidas, postres), estado.categorias)
        assertTrue(estado.vacia)
        assertEquals(pesos("0"), estado.importes.total)
        assertFalse(estado.puedeEnviar)
    }

    @Test
    fun `agregar y quitar cambia la cantidad y quitar la ultima unidad saca el producto`() {
        vm.agregar(latte)
        vm.agregar(latte)
        assertEquals(2, estado.cantidadDe(latte.id))

        vm.quitar(latte)
        assertEquals(1, estado.cantidadDe(latte.id))
        vm.quitar(latte)
        assertTrue(estado.vacia)
        vm.quitar(latte) // no baja de cero
        assertEquals(0, estado.cantidadDe(latte.id))
    }

    @Test
    fun `cada linea tiene su subtotal y el total es la suma de los subtotales`() {
        repeat(2) { vm.agregar(latte) }
        vm.agregar(brownie)

        assertEquals(listOf(pesos("13000"), pesos("4500.50")), estado.lineas.map { it.subtotal })
        assertEquals(pesos("17500.50"), estado.importes.subtotal)
        assertEquals(pesos("17500.50"), estado.importes.total)
        assertEquals(estado.lineas.fold(BigDecimal.ZERO.setScale(2)) { s, l -> s + l.subtotal }, estado.importes.total)
        assertEquals(3, estado.unidades)
    }

    @Test
    fun `no se pueden pedir mas unidades que las existencias`() {
        repeat(5) { vm.agregar(brownie) }
        assertEquals(2, estado.cantidadDe(brownie.id))
    }

    @Test
    fun `filtrar por categoria muestra solo sus productos`() {
        vm.filtrarPorCategoria(2)
        assertEquals(listOf(postres), estado.visibles)
        vm.filtrarPorCategoria(null)
        assertEquals(2, estado.visibles.size)
    }

    @Test
    fun `si el menu no carga muestra el error y reintentar lo recupera`() {
        menu.respuesta = ResultadoCatalogo.SinConexion
        vm.cargarMenu()
        assertEquals(ErrorComanda.SinConexion, estado.errorMenu)

        menu.respuesta = ResultadoCatalogo.Exito(listOf(bebidas))
        vm.cargarMenu()
        assertNull(estado.errorMenu)
    }

    @Test
    fun `al recargar el menu se ajustan las cantidades a lo que sigue disponible`() {
        repeat(2) { vm.agregar(brownie) }
        vm.agregar(latte)

        menu.respuesta = ResultadoCatalogo.Exito(
            listOf(MenuCategoria(1, "Bebidas", listOf(latte)), MenuCategoria(2, "Postres", listOf(brownie.copy(existencias = 1)))),
        )
        vm.cargarMenu()
        assertEquals(1, estado.cantidadDe(brownie.id))

        menu.respuesta = ResultadoCatalogo.Exito(listOf(bebidas)) // el brownie se agotó
        vm.cargarMenu()
        assertEquals(0, estado.cantidadDe(brownie.id))
        assertEquals(1, estado.cantidadDe(latte.id))
    }

    // ---------------------------------------------------------------- comanda vacía

    @Test
    fun `la comanda vacia no pasa al resumen y avisa`() {
        vm.verResumen()
        assertEquals(PasoComanda.ARMAR, estado.paso)
        assertEquals(ErrorComanda.Vacia, estado.error)
    }

    @Test
    fun `la comanda vacia no se envia al servidor`() {
        vm.confirmar()
        assertEquals(ErrorComanda.Vacia, estado.error)
        assertTrue(comandas.comandasCreadas.isEmpty())
        assertTrue(comandas.cambios.isEmpty())
    }

    @Test
    fun `agregar un producto quita el aviso de comanda vacia`() {
        vm.verResumen()
        vm.agregar(latte)
        assertNull(estado.error)
    }

    // ---------------------------------------------------------------- resumen y envío

    @Test
    fun `el resumen muestra las lineas y se puede volver a editar`() {
        vm.agregar(latte)
        vm.verResumen()
        assertEquals(PasoComanda.RESUMEN, estado.paso)
        assertEquals(listOf(latte), estado.lineas.map { it.producto })

        vm.seguirEditando()
        assertEquals(PasoComanda.ARMAR, estado.paso)
        assertEquals(1, estado.cantidadDe(latte.id))
    }

    @Test
    fun `confirmar crea la comanda con la mesa y las cantidades y luego la envia a cocina`() {
        comandas.comandaCreada = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.PENDIENTE))
        comandas.estadoCambiado = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.ENVIADA, total = "17500.50"))
        repeat(2) { vm.agregar(latte) }
        vm.agregar(brownie)
        vm.verResumen()

        vm.confirmar()

        assertEquals(listOf(ItemPedido(7, 2), ItemPedido(9, 1)), comandas.comandasCreadas.single().items)
        assertEquals(3L, comandas.comandasCreadas.single().mesaId)
        assertEquals(listOf(12L to EstadoComanda.ENVIADA), comandas.cambios)
        assertEquals(PasoComanda.ENVIADA, estado.paso)
        assertEquals(BigDecimal("17500.50"), estado.enviada?.total)
        assertFalse(estado.enviando)
    }

    @Test
    fun `si crear falla por mesa ocupada muestra el mensaje y no intenta enviar`() {
        comandas.comandaCreada = ResultadoCatalogo.Conflicto("La mesa ya tiene una comanda abierta", emptyMap())
        vm.agregar(latte)
        vm.verResumen()

        vm.confirmar()

        assertEquals(ErrorComanda.DelServidor("La mesa ya tiene una comanda abierta"), estado.error)
        assertTrue(comandas.cambios.isEmpty())
        assertNull(estado.comandaCreadaId)
        assertEquals(PasoComanda.RESUMEN, estado.paso)
    }

    @Test
    fun `un 400 con campo muestra el mensaje del campo`() {
        comandas.comandaCreada = ResultadoCatalogo.DatosInvalidos(
            "Revisa los datos ingresados.",
            mapOf("recompensaId" to "La recompensa no está activa"),
        )
        vm.agregar(latte)
        vm.confirmar()
        assertEquals(ErrorComanda.DelServidor("La recompensa no está activa"), estado.error)
    }

    @Test
    fun `si el envio falla la comanda queda creada y reintentar solo la envia`() {
        comandas.comandaCreada = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.PENDIENTE))
        comandas.estadoCambiado = ResultadoCatalogo.SinConexion
        vm.agregar(latte)
        vm.verResumen()

        vm.confirmar()
        assertEquals(12L, estado.comandaCreadaId)
        assertEquals(ErrorComanda.SinConexion, estado.error)
        assertFalse(estado.editable)

        // Ya creada: no se puede volver a editar ni cambiar cantidades.
        vm.seguirEditando()
        vm.agregar(latte)
        assertEquals(PasoComanda.RESUMEN, estado.paso)
        assertEquals(1, estado.cantidadDe(latte.id))

        comandas.estadoCambiado = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.ENVIADA))
        vm.confirmar()
        assertEquals(1, comandas.comandasCreadas.size)
        assertEquals(listOf(12L to EstadoComanda.ENVIADA, 12L to EstadoComanda.ENVIADA), comandas.cambios)
        assertEquals(PasoComanda.ENVIADA, estado.paso)
    }

    @Test
    fun `una transicion rechazada con 400 muestra el mensaje del servidor`() {
        comandas.comandaCreada = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.PENDIENTE))
        comandas.estadoCambiado = ResultadoCatalogo.DatosInvalidos("El rol CAJERO no puede pasar la comanda a ENVIADA", emptyMap())
        vm.agregar(latte)

        vm.confirmar()

        assertEquals(ErrorComanda.DelServidor("El rol CAJERO no puede pasar la comanda a ENVIADA"), estado.error)
        assertEquals(PasoComanda.ARMAR, estado.paso)
    }

    @Test
    fun `ya enviada no se vuelve a enviar`() {
        comandas.comandaCreada = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.PENDIENTE))
        comandas.estadoCambiado = ResultadoCatalogo.Exito(comanda(12, EstadoComanda.ENVIADA))
        vm.agregar(latte)
        vm.confirmar()
        vm.confirmar()

        assertEquals(1, comandas.comandasCreadas.size)
        assertEquals(1, comandas.cambios.size)
    }
}
