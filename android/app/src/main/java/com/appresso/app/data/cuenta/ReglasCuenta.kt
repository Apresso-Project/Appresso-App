package com.appresso.app.data.cuenta

/**
 * Las mismas reglas que aplica el servidor a los datos de la cuenta (docs/api-clientes.md), para
 * avisar antes de enviar. Son igual de permisivas o más: la última palabra la tiene el servidor.
 */
object ReglasCuenta {

    const val MAX_NOMBRE = 80
    const val MAX_CORREO = 150
    const val MIN_CONTRASENA = 8
    const val MAX_CONTRASENA = 72

    private val SEPARADORES_DOCUMENTO = Regex("[\\s.\\-]")
    private val DOCUMENTO = Regex("^[A-Z0-9]{5,20}$")
    private val CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val CELULAR = Regex("^\\+?[0-9 ]{7,20}$")

    /** Sin espacios, puntos ni guiones y en mayúsculas, igual que lo guarda el servidor. */
    fun normalizarDocumento(documento: String): String =
        documento.replace(SEPARADORES_DOCUMENTO, "").uppercase()

    fun esDocumentoValido(documento: String): Boolean = DOCUMENTO.matches(normalizarDocumento(documento))

    fun esCorreoValido(correo: String): Boolean = correo.length <= MAX_CORREO && CORREO.matches(correo)

    fun esCelularValido(celular: String): Boolean = CELULAR.matches(celular)

    /** Entre 8 y 72 caracteres, y no más de 72 bytes (el límite de BCrypt: la ñ y las tildes ocupan 2). */
    fun esContrasenaValida(contrasena: String): Boolean =
        contrasena.length in MIN_CONTRASENA..MAX_CONTRASENA &&
            contrasena.toByteArray(Charsets.UTF_8).size <= MAX_CONTRASENA
}
