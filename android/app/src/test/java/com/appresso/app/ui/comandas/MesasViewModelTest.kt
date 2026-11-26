package com.appresso.app.ui.comandas

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.comandas.EstadoMesa
import com.appresso.app.data.comandas.Mesa
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
class MesasViewModelTest {

    private val mesa1 = Mesa(1, "Mesa 1", EstadoMesa.DISPONIBLE)
    private val mesa2 = Mesa(2, "Mesa 2", EstadoMesa.OCUPADA)
    private val terraza = Mesa(3, "Terraza", EstadoMesa.DISPONIBLE)

    private val repo = ComandasFalso()
    private lateinit var vm: MesasViewModel

    @Before
    fun iniciar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.mesas = ResultadoCatalogo.Exito(listOf(mesa1, mesa2, terraza))
        vm = MesasViewModel(repo)
    }

    @After
    fun detener() = Dispatchers.resetMain()

    private val estado get() = vm.estado.value

    // ---------------------------------------------------------------- mapa

    @Test
    fun `al abrir carga el mapa de mesas y cuenta disponibles y ocupadas`() {
        assertEquals(listOf(mesa1, mesa2, terraza), estado.mesas)
        assertFalse(estado.cargando)
        assertEquals(2, estado.disponibles)
        assertEquals(1, estado.ocupadas)
    }

    @Test
    fun `si falla la carga muestra el error y reintentar lo recupera`() {
        repo.mesas = ResultadoCatalogo.SinConexion
        val vm = MesasViewModel(repo)
        assertEquals(ErrorMesas.SinConexion, vm.estado.value.error)

        repo.mesas = ResultadoCatalogo.Exito(listOf(mesa1))
        vm.cargar()
        assertNull(vm.estado.value.error)
        assertEquals(listOf(mesa1), vm.estado.value.mesas)
    }

    @Test
    fun `un 401 no muestra error porque la sesion ya se cerro`() {
        repo.mesas = ResultadoCatalogo.SesionInvalida
        vm.cargar()
        assertNull(estado.error)
    }

    @Test
    fun `recargar en silencio actualiza el estado de las mesas sin mostrar errores`() {
        repo.mesas = ResultadoCatalogo.Exito(listOf(mesa1.copy(estado = EstadoMesa.OCUPADA)))
        vm.recargarEnSilencio()
        assertEquals(EstadoMesa.OCUPADA, estado.mesas.single().estado)

        repo.mesas = ResultadoCatalogo.ErrorDelServidor
        vm.recargarEnSilencio()
        assertNull(estado.error)
        assertEquals(1, estado.mesas.size)
    }

    // ---------------------------------------------------------------- crear mesa

    @Test
    fun `crear una mesa la agrega disponible al mapa en orden y cierra el formulario`() {
        repo.creada = ResultadoCatalogo.Exito(Mesa(9, "Mesa 10", EstadoMesa.DISPONIBLE))
        vm.nuevaMesa()
        vm.onNombreChange("  Mesa 10 ")
        vm.guardar()

        assertEquals(listOf("Mesa 10"), repo.nombresCreados)
        assertNull(estado.formulario)
        assertEquals("Mesa 10", estado.mesaCreada)
        assertEquals(listOf("Mesa 1", "Mesa 10", "Mesa 2", "Terraza"), estado.mesas.map { it.nombre })
        assertTrue(estado.mesas.first { it.id == 9L }.disponible)
    }

    @Test
    fun `un nombre vacio o solo espacios no se envia`() {
        vm.nuevaMesa()
        vm.onNombreChange("   ")
        vm.guardar()

        assertEquals(ErrorNombreMesa.Vacio, estado.formulario?.errorNombre)
        assertTrue(repo.nombresCreados.isEmpty())
    }

    @Test
    fun `un nombre de mas de 30 caracteres no se envia`() {
        vm.nuevaMesa()
        vm.onNombreChange("x".repeat(31))
        vm.guardar()

        assertEquals(ErrorNombreMesa.MuyLargo, estado.formulario?.errorNombre)
        assertTrue(repo.nombresCreados.isEmpty())
    }

    @Test
    fun `un nombre repetido sin distinguir mayusculas ni espacios no se envia`() {
        vm.nuevaMesa()
        vm.onNombreChange("  mesa 1 ")
        vm.guardar()

        assertEquals(ErrorNombreMesa.Repetido, estado.formulario?.errorNombre)
        assertTrue(repo.nombresCreados.isEmpty())
    }

    @Test
    fun `si el servidor responde 409 muestra su mensaje en el nombre`() {
        repo.creada = ResultadoCatalogo.Conflicto("Ya existe", mapOf("nombre" to "Ya existe una mesa con ese nombre"))
        vm.nuevaMesa()
        vm.onNombreChange("Barra")
        vm.guardar()

        assertEquals(ErrorNombreMesa.DelServidor("Ya existe una mesa con ese nombre"), estado.formulario?.errorNombre)
        assertFalse(estado.formulario!!.guardando)
    }

    @Test
    fun `si el rol no es administrador muestra el mensaje del 403 y no agrega nada`() {
        repo.creada = ResultadoCatalogo.SinPermiso("No tienes permiso para realizar esta acción.")
        vm.nuevaMesa()
        vm.onNombreChange("Barra")
        vm.guardar()

        assertEquals(ErrorMesas.SinPermiso("No tienes permiso para realizar esta acción."), estado.formulario?.error)
        assertEquals(3, estado.mesas.size)
    }

    @Test
    fun `escribir borra el error del nombre`() {
        vm.nuevaMesa()
        vm.guardar()
        assertEquals(ErrorNombreMesa.Vacio, estado.formulario?.errorNombre)

        vm.onNombreChange("B")
        assertNull(estado.formulario?.errorNombre)
    }

    @Test
    fun `las reglas del nombre son las del backend`() {
        assertNull(ReglasMesa.validar("x".repeat(30), emptyList()))
        assertEquals(ErrorNombreMesa.MuyLargo, ReglasMesa.validar("x".repeat(31), emptyList()))
        assertEquals(ErrorNombreMesa.Vacio, ReglasMesa.validar("", emptyList()))
        assertEquals(ErrorNombreMesa.Repetido, ReglasMesa.validar("TERRAZA", listOf(terraza)))
    }
}
