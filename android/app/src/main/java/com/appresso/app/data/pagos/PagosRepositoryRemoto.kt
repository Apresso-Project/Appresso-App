package com.appresso.app.data.pagos

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.MedioPagoDto
import com.appresso.app.data.remote.PagoBody
import com.appresso.app.data.remote.PagoDto
import com.appresso.app.data.remote.PagosApi
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Habla con `/api/pagos` y traduce cada respuesta del contrato (docs/api-pagos.md) a un
 * [ResultadoPago]. El token y el cierre de sesión ante un 401 los maneja el cliente HTTP compartido.
 */
class PagosRepositoryRemoto(private val api: PagosApi) : PagosRepository {

    override suspend fun confirmar(
        comandaId: Long,
        medio: MedioPago,
        identificadorOperacion: String,
    ): ResultadoPago =
        try {
            val cuerpo = PagoBody(comandaId, identificadorOperacion)
            traducir(
                when (medio) {
                    MedioPago.EFECTIVO -> api.efectivo(cuerpo)
                    MedioPago.TRANSFERENCIA -> api.transferencia(cuerpo)
                },
            )
        } catch (e: IOException) {
            ResultadoPago.SinConexion
        } catch (e: SerializationException) {
            // Respondió 2xx con un cuerpo ilegible. El pago pudo haberse registrado: la pantalla
            // debe reintentar con el mismo identificador, que devuelve el pago ya registrado.
            ResultadoPago.ErrorDelServidor
        }

    private fun traducir(respuesta: Response<PagoDto>): ResultadoPago = when {
        respuesta.isSuccessful -> {
            val cuerpo = respuesta.body()
            if (cuerpo == null) {
                ResultadoPago.ErrorDelServidor
            } else {
                ResultadoPago.Exito(cuerpo.aPago(), repetido = respuesta.code() == 200)
            }
        }

        respuesta.code() == 400 -> ResultadoPago.DatosInvalidos(leerError(respuesta)?.mensaje)

        respuesta.code() == 401 -> ResultadoPago.SesionInvalida

        respuesta.code() == 403 -> ResultadoPago.SinPermiso(leerError(respuesta)?.mensaje)

        respuesta.code() == 404 -> ResultadoPago.NoEncontrada

        respuesta.code() == 409 -> ResultadoPago.Conflicto(leerError(respuesta)?.mensaje)

        else -> ResultadoPago.ErrorDelServidor
    }

    /** Si el cuerpo falta o no es JSON válido devuelve null en vez de fallar. */
    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}

private fun PagoDto.aPago() = PagoConfirmado(
    id = id,
    comandaId = comandaId,
    mesa = mesa,
    valor = valor,
    medio = when (medio) {
        MedioPagoDto.EFECTIVO -> MedioPago.EFECTIVO
        MedioPagoDto.TRANSFERENCIA -> MedioPago.TRANSFERENCIA
    },
    fecha = try {
        Instant.parse(fecha)
    } catch (e: DateTimeException) {
        null
    },
    confirmadoPorNombre = confirmadoPorNombre,
    identificadorOperacion = identificadorOperacion,
)
