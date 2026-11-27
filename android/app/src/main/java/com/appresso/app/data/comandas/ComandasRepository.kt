package com.appresso.app.data.comandas

import com.appresso.app.data.catalogo.ResultadoCatalogo

/**
 * Mesas y comandas (HU 4.1.1). Devuelve [ResultadoCatalogo], el mismo conjunto de resultados que el
 * resto de módulos (éxito, 400, 401, 403, 404, 409, sin conexión, error del servidor). Nunca lanza
 * excepciones de red.
 */
interface ComandasRepository {

    /** Mapa de mesas (administrador y mesero), en el orden del servidor (por nombre). */
    suspend fun listarMesas(): ResultadoCatalogo<List<Mesa>>

    /**
     * Crea una mesa (solo administrador). Nombre vacío o de más de 30 caracteres → `DatosInvalidos`;
     * repetido → `Conflicto` (ambos con `campos.nombre`); otro rol → `SinPermiso`.
     */
    suspend fun crearMesa(nombre: String): ResultadoCatalogo<Mesa>

    /**
     * Crea la comanda en `PENDIENTE` (la mesa pasa a `OCUPADA`). Sin productos → `DatosInvalidos`
     * (`campos.items`); mesa ocupada o sin stock → `Conflicto`.
     */
    suspend fun crearComanda(comanda: NuevaComanda): ResultadoCatalogo<Comanda>

    suspend fun consultarComanda(id: Long): ResultadoCatalogo<Comanda>

    /** Transición no permitida o de un rol que no corresponde → `DatosInvalidos`. */
    suspend fun cambiarEstado(id: Long, estado: EstadoComanda): ResultadoCatalogo<Comanda>
}
