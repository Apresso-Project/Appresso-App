package com.appresso.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Almacena los tokens y datos de sesión de forma persistente y cifrada mediante Android Keystore.
 *
 * Utiliza una clave AES-256 generada y custodiada por el hardware de seguridad de Android (TEE o
 * StrongBox mediante el proveedor "AndroidKeyStore"), y cifra la sesión con el algoritmo AES/GCM/NoPadding.
 *
 * Los datos cifrados se persisten en SharedPreferences privadas. En ningún momento el token de sesión
 * se almacena en texto plano en disco ni se expone a otras aplicaciones.
 */
class AlmacenSesionKeystore(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE),
) : AlmacenSesion {

    private val _sesion = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    init {
        _sesion.value = cargarSesionGuardada()
    }

    override fun guardar(sesion: Sesion) {
        try {
            val datos = DatosSesionCifrada(
                token = sesion.token,
                rol = sesion.rol.name,
                nombre = sesion.nombre,
            )
            val jsonTexto = Json.encodeToString(datos)
            val textoCifrado = cifrar(jsonTexto)
            prefs.edit().putString(CLAVE_SESION_CIFRADA, textoCifrado).apply()
            _sesion.value = sesion
        } catch (e: Exception) {
            // Si ocurre algún error en el cifrado o persistencia, se mantiene en memoria y se limpia disco.
            prefs.edit().remove(CLAVE_SESION_CIFRADA).apply()
            _sesion.value = sesion
        }
    }

    override fun borrar() {
        prefs.edit().remove(CLAVE_SESION_CIFRADA).apply()
        _sesion.value = null
    }

    private fun cargarSesionGuardada(): Sesion? {
        val payloadCifrado = prefs.getString(CLAVE_SESION_CIFRADA, null) ?: return null
        return try {
            val jsonTexto = descifrar(payloadCifrado)
            val datos = Json.decodeFromString<DatosSesionCifrada>(jsonTexto)
            val rol = Rol.valueOf(datos.rol)
            Sesion(
                token = datos.token,
                rol = rol,
                nombre = datos.nombre,
            )
        } catch (e: Exception) {
            // Si el token no se pudo descifrar (por ejemplo cambio de claves del dispositivo o dato corrupto),
            // se limpia el almacenamiento para evitar estados inconsistentes.
            prefs.edit().remove(CLAVE_SESION_CIFRADA).apply()
            null
        }
    }

    private fun cifrar(textoClaro: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearClave())
        val iv = cipher.iv
        val bytesCifrados = cipher.doFinal(textoClaro.toByteArray(Charsets.UTF_8))
        val paquete = ByteArray(iv.size + bytesCifrados.size)
        System.arraycopy(iv, 0, paquete, 0, iv.size)
        System.arraycopy(bytesCifrados, 0, paquete, iv.size, bytesCifrados.size)
        return Base64.encodeToString(paquete, Base64.NO_WRAP)
    }

    private fun descifrar(payloadBase64: String): String {
        val paquete = Base64.decode(payloadBase64, Base64.NO_WRAP)
        if (paquete.size < TAMANO_IV_BYTES) {
            throw IllegalArgumentException("Payload cifrado inválido: tamaño menor al vector de inicialización.")
        }
        val iv = ByteArray(TAMANO_IV_BYTES)
        val longitudCifrado = paquete.size - TAMANO_IV_BYTES
        val bytesCifrados = ByteArray(longitudCifrado)
        System.arraycopy(paquete, 0, iv, 0, TAMANO_IV_BYTES)
        System.arraycopy(paquete, TAMANO_IV_BYTES, bytesCifrados, 0, longitudCifrado)

        val cipher = Cipher.getInstance(TRANSFORMACION)
        val spec = GCMParameterSpec(LONGITUD_TAG_GCM_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), spec)
        val bytesClaros = cipher.doFinal(bytesCifrados)
        return String(bytesClaros, Charsets.UTF_8)
    }

    private fun obtenerClave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
        val entrada = keyStore.getEntry(ALIAS_CLAVE, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("No se encontró la clave en Android Keystore.")
        return entrada.secretKey
    }

    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS_CLAVE)) {
            val entrada = keyStore.getEntry(ALIAS_CLAVE, null) as? KeyStore.SecretKeyEntry
            if (entrada != null) return entrada.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS_CLAVE,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Serializable
    private data class DatosSesionCifrada(
        val token: String,
        val rol: String,
        val nombre: String,
    )

    companion object {
        const val NOMBRE_PREFS = "appresso_sesion_segura"
        const val CLAVE_SESION_CIFRADA = "sesion_cifrada"
        private const val PROVEEDOR_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS_CLAVE = "appresso_auth_keystore_alias"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val TAMANO_IV_BYTES = 12
        private const val LONGITUD_TAG_GCM_BITS = 128

        /**
         * Crea la instancia de [AlmacenSesion] respaldada por Android Keystore.
         * Si el entorno no soporta el proveedor AndroidKeyStore (por ejemplo en pruebas unitarias de JVM),
         * recurre a [AlmacenSesionEnMemoria] de forma segura.
         */
        fun crear(context: Context): AlmacenSesion {
            return try {
                KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
                AlmacenSesionKeystore(context)
            } catch (t: Throwable) {
                // Proveedor AndroidKeyStore no disponible en JVM local de pruebas
                AlmacenSesionEnMemoria()
            }
        }
    }
}
