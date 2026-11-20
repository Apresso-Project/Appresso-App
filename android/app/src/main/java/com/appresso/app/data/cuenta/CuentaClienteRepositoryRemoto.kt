package com.appresso.app.data.cuenta

import com.appresso.app.data.remote.ClienteHttp
import com.appresso.app.data.remote.CuentaApi
import com.appresso.app.data.remote.ErrorRespuesta
import com.appresso.app.data.remote.RegistroClienteRequest
import com.appresso.app.data.remote.RestablecerContrasenaRequest
import com.appresso.app.data.remote.SolicitarCodigoRequest
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/** Habla con los endpoints públicos de la cuenta y traduce cada respuesta a un [ResultadoCuenta]. */
class CuentaClienteRepositoryRemoto(private val api: CuentaApi) : CuentaClienteRepository {

    override suspend fun registrar(datos: DatosRegistro): ResultadoCuenta = ejecutar(
        {
            api.registrar(
                RegistroClienteRequest(
                    nombre = datos.nombre,
                    apellido = datos.apellido,
                    documento = datos.documento,
                    celular = datos.celular,
                    correo = datos.correo,
                    contrasena = datos.contrasena,
                    aceptoTratamientoDatos = datos.aceptoTratamientoDatos,
                ),
            )
        },
        { it.mensaje },
    )

    override suspend fun solicitarCodigo(documento: String): ResultadoCuenta =
        ejecutar({ api.solicitarCodigo(SolicitarCodigoRequest(documento)) }, { it.mensaje })

    override suspend fun restablecer(documento: String, codigo: String, contrasenaNueva: String): ResultadoCuenta =
        ejecutar({ api.restablecer(RestablecerContrasenaRequest(documento, codigo, contrasenaNueva)) }, { it.mensaje })

    private suspend fun <T> ejecutar(llamada: suspend () -> Response<T>, mensaje: (T) -> String?): ResultadoCuenta =
        try {
            val respuesta = llamada()
            val cuerpo = respuesta.body()
            when {
                respuesta.isSuccessful && cuerpo != null -> ResultadoCuenta.Exito(mensaje(cuerpo))
                respuesta.isSuccessful -> ResultadoCuenta.ErrorDelServidor
                respuesta.code() == 400 -> leerError(respuesta).let {
                    ResultadoCuenta.DatosInvalidos(it?.mensaje, it?.campos.orEmpty())
                }
                respuesta.code() == 409 -> leerError(respuesta).let {
                    ResultadoCuenta.Duplicado(it?.mensaje, it?.campos.orEmpty())
                }
                else -> ResultadoCuenta.ErrorDelServidor
            }
        } catch (e: IOException) {
            ResultadoCuenta.SinConexion
        } catch (e: SerializationException) {
            ResultadoCuenta.ErrorDelServidor
        }

    private fun leerError(respuesta: Response<*>): ErrorRespuesta? =
        try {
            respuesta.errorBody()?.string()?.let { ClienteHttp.json.decodeFromString<ErrorRespuesta>(it) }
        } catch (e: SerializationException) {
            null
        } catch (e: IOException) {
            null
        }
}
