package com.appresso.app.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra y descifra texto con AES-256 en modo GCM, que además detecta si el dato fue alterado. El
 * resultado es Base64 de un vector de inicialización (12 bytes, nuevo en cada cifrado) seguido del
 * texto cifrado. La clave la da [clave]: en la app sale del Keystore ([claveDelKeystore]).
 */
class CifradorSesion(private val clave: () -> SecretKey) {

    fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, clave())
        val paquete = cifrador.iv + cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(paquete)
    }

    /** Lanza una excepción si el dato no es válido, fue alterado o se cifró con otra clave. */
    fun descifrar(cifrado: String): String {
        val paquete = Base64.getDecoder().decode(cifrado)
        require(paquete.size > TAMANO_IV) { "Dato cifrado incompleto" }
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(BITS_ETIQUETA, paquete, 0, TAMANO_IV))
        return String(cifrador.doFinal(paquete, TAMANO_IV, paquete.size - TAMANO_IV), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val TAMANO_IV = 12
        private const val BITS_ETIQUETA = 128
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val ALIAS = "appresso_auth_keystore_alias"

        /**
         * La clave AES-256 que custodia Android Keystore; la crea la primera vez. La app la usa, pero
         * nunca ve sus bytes: no se puede exportar.
         */
        fun claveDelKeystore(): SecretKey {
            val almacen = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
            (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
            generador.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generador.generateKey()
        }
    }
}
