package com.appresso.app.ui.comandas

import com.appresso.app.data.catalogo.ResultadoCatalogo
import com.appresso.app.data.comandas.Comanda
import com.appresso.app.data.comandas.ComandasRepository
import com.appresso.app.data.comandas.EstadoComanda
import com.appresso.app.data.comandas.Mesa
import com.appresso.app.data.comandas.NuevaComanda
import com.appresso.app.data.menu.MenuCategoria
import com.appresso.app.data.menu.MenuRepository
import java.math.BigDecimal

/** Repositorio de comandas en memoria: responde lo que cada prueba prepara y anota lo que se le pidió. */
internal class ComandasFalso : ComandasRepository {
    var mesas: ResultadoCatalogo<List<Mesa>> = ResultadoCatalogo.Exito(emptyList())
    var creada: ResultadoCatalogo<Mesa> = ResultadoCatalogo.ErrorDelServidor
    var comandaCreada: ResultadoCatalogo<Comanda> = ResultadoCatalogo.ErrorDelServidor
    var estadoCambiado: ResultadoCatalogo<Comanda> = ResultadoCatalogo.ErrorDelServidor

    val nombresCreados = mutableListOf<String>()
    val comandasCreadas = mutableListOf<NuevaComanda>()
    val cambios = mutableListOf<Pair<Long, EstadoComanda>>()
    var consultasMesas = 0

    override suspend fun listarMesas(): ResultadoCatalogo<List<Mesa>> {
        consultasMesas++
        return mesas
    }

    override suspend fun crearMesa(nombre: String): ResultadoCatalogo<Mesa> {
        nombresCreados += nombre
        return creada
    }

    override suspend fun crearComanda(comanda: NuevaComanda): ResultadoCatalogo<Comanda> {
        comandasCreadas += comanda
        return comandaCreada
    }

    override suspend fun consultarComanda(id: Long): ResultadoCatalogo<Comanda> = ResultadoCatalogo.NoEncontrado

    override suspend fun cambiarEstado(id: Long, estado: EstadoComanda): ResultadoCatalogo<Comanda> {
        cambios += id to estado
        return estadoCambiado
    }
}

internal class MenuFalso(var respuesta: ResultadoCatalogo<List<MenuCategoria>>) : MenuRepository {
    override suspend fun consultar() = respuesta
}

internal fun comanda(id: Long, estado: EstadoComanda, total: String = "0") = Comanda(
    id = id,
    mesaId = 3,
    mesaNombre = "Mesa 3",
    estado = estado,
    lineas = emptyList(),
    subtotal = BigDecimal(total),
    descuento = BigDecimal.ZERO,
    total = BigDecimal(total),
)
