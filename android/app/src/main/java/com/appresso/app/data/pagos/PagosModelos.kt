package com.appresso.app.data.pagos

import java.math.BigDecimal
import java.time.Instant

/** Medio de pago. Efectivo y transferencia llevan saldos separados en el backend (RF-31). */
enum class MedioPago { EFECTIVO, TRANSFERENCIA }

/** Pago ya registrado por el backend (docs/api-pagos.md). La comanda queda `CERRADA`. */
data class PagoConfirmado(
    val id: Long,
    val comandaId: Long,
    val mesa: String,
    val valor: BigDecimal,
    val medio: MedioPago,
    /** Null si el backend envía una fecha que la app no sabe leer; no impide mostrar el pago. */
    val fecha: Instant?,
    val confirmadoPorNombre: String,
    val identificadorOperacion: String,
)

/**
 * Resultado de confirmar un pago, ya traducido de las respuestas del backend (200/201, 400, 401,
 * 403, 404, 409 y fallos de red; ver docs/api-pagos.md). Los textos de la persona no se definen
 * aquí sino en quien lo consume, salvo el [Conflicto.mensaje] del servidor.
 */
sealed interface ResultadoPago {

    /**
     * 201: pago registrado. 200: [repetido] es true; era el reintento de un pago que ya se había
     * registrado (mismo identificador), no se sumó otra vez.
     */
    data class Exito(val pago: PagoConfirmado, val repetido: Boolean) : ResultadoPago

    /** 400: la solicitud no es válida (no debería pasar desde la pantalla). */
    data class DatosInvalidos(val mensaje: String?) : ResultadoPago

    /**
     * 409: la comanda ya fue pagada, fue cancelada o aún no está lista. [mensaje] es el del
     * servidor y se muestra tal cual.
     */
    data class Conflicto(val mensaje: String?) : ResultadoPago

    /** 401: no hay sesión o el token venció. */
    data object SesionInvalida : ResultadoPago

    /** 403: el rol no puede confirmar ese medio (p. ej. un mesero con efectivo). */
    data class SinPermiso(val mensaje: String?) : ResultadoPago

    /** 404: la comanda no existe. */
    data object NoEncontrada : ResultadoPago

    data object SinConexion : ResultadoPago

    data object ErrorDelServidor : ResultadoPago
}
