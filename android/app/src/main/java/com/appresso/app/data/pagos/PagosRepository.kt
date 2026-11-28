package com.appresso.app.data.pagos

/**
 * Confirmación de pagos. Existe como interfaz para poder sustituirla en las pruebas. Nunca lanza
 * por errores de red o del servidor: los devuelve como [ResultadoPago].
 */
interface PagosRepository {

    /**
     * Confirma el pago de la comanda [comandaId] con el [medio] indicado. El valor lo calcula el
     * servidor.
     *
     * [identificadorOperacion] (un UUID) debe generarse **una vez por intento de pago** y reenviarse
     * igual si no se supo si la petición llegó: así un reintento devuelve el pago ya registrado en
     * vez de fallar o cobrar dos veces (RNF-11).
     */
    suspend fun confirmar(comandaId: Long, medio: MedioPago, identificadorOperacion: String): ResultadoPago
}
