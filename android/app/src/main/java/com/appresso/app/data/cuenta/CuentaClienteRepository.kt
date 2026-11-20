package com.appresso.app.data.cuenta

/** Datos con los que el cliente crea su cuenta (RF-50). */
data class DatosRegistro(
    val nombre: String,
    val apellido: String,
    val documento: String,
    val celular: String,
    val correo: String,
    val contrasena: String,
    val aceptoTratamientoDatos: Boolean,
) {
    override fun toString() = "DatosRegistro(documento=$documento, contrasena=***)"
}

/**
 * Resultado de una operación de la cuenta del cliente, ya traducido de las respuestas del backend.
 * Los textos que ve el usuario no se definen aquí: [mensaje] es el del servidor y puede faltar.
 */
sealed interface ResultadoCuenta {

    data class Exito(val mensaje: String?) : ResultadoCuenta

    /** 400: algún dato no es válido o el código no sirve. [campos] asocia cada campo con su problema. */
    data class DatosInvalidos(val mensaje: String?, val campos: Map<String, String>) : ResultadoCuenta

    /** 409: el documento o el correo ya están registrados. */
    data class Duplicado(val mensaje: String?, val campos: Map<String, String>) : ResultadoCuenta

    data object SinConexion : ResultadoCuenta
    data object ErrorDelServidor : ResultadoCuenta
}

/** Registro y recuperación de contraseña del cliente. Interfaz para poder sustituirla en las pruebas. */
interface CuentaClienteRepository {

    suspend fun registrar(datos: DatosRegistro): ResultadoCuenta

    /** El servidor responde lo mismo exista o no el documento (RF-65). */
    suspend fun solicitarCodigo(documento: String): ResultadoCuenta

    suspend fun restablecer(documento: String, codigo: String, contrasenaNueva: String): ResultadoCuenta
}
