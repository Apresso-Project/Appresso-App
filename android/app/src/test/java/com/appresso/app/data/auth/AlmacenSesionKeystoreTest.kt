package com.appresso.app.data.auth

import android.content.SharedPreferences
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmacenSesionKeystoreTest {

    private val clave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cifrador = CifradorSesion { clave }
    private val prefs = PrefsFalsas()
    private val sesion = Sesion("token.secreto.jwt", Rol.CLIENTE, "Ana Pérez")

    @Test
    fun `al empezar no hay sesion`() {
        assertNull(almacen().sesion.value)
    }

    @Test
    fun `la sesion guardada se recupera al volver a abrir la app`() {
        almacen().guardar(sesion)

        assertEquals(sesion, almacen().sesion.value)
    }

    @Test
    fun `en el telefono no queda nada en texto plano`() {
        almacen().guardar(sesion)

        assertEquals(1, prefs.datos.size)
        val escrito = prefs.datos.values.single()
        listOf("token.secreto.jwt", "Ana", "CLIENTE", "token").forEach {
            assertFalse("Se ve $it en lo guardado", escrito.contains(it))
        }
    }

    @Test
    fun `borrar cierra la sesion y no deja nada guardado`() {
        val almacen = almacen()
        almacen.guardar(sesion)

        almacen.borrar()

        assertNull(almacen.sesion.value)
        assertTrue(prefs.datos.isEmpty())
        assertNull(almacen().sesion.value)
    }

    @Test
    fun `guardar de nuevo reemplaza la sesion anterior`() {
        almacen().guardar(sesion)
        val otra = Sesion("otro", Rol.MESERO, "Luis")

        almacen().guardar(otra)

        assertEquals(otra, almacen().sesion.value)
    }

    @Test
    fun `si lo guardado esta danado se descarta y hay que iniciar sesion`() {
        prefs.datos[AlmacenSesionKeystore.CLAVE_SESION] = "basura"

        assertNull(almacen().sesion.value)
        assertTrue(prefs.datos.isEmpty())
    }

    @Test
    fun `si la clave del dispositivo cambio se descarta lo guardado`() {
        almacen().guardar(sesion)
        val otraClave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertNull(AlmacenSesionKeystore(prefs, CifradorSesion { otraClave }).sesion.value)
        assertTrue(prefs.datos.isEmpty())
    }

    @Test
    fun `un rol desconocido se descarta`() {
        prefs.datos[AlmacenSesionKeystore.CLAVE_SESION] =
            cifrador.cifrar("""{"token":"t","rol":"GERENTE","nombre":"Ana"}""")

        assertNull(almacen().sesion.value)
    }

    @Test
    fun `si el Keystore falla la sesion queda en memoria y nunca sin cifrar`() {
        prefs.datos[AlmacenSesionKeystore.CLAVE_SESION] = "anterior"
        val almacen = AlmacenSesionKeystore(prefs, CifradorSesion { error("Keystore no disponible") })

        almacen.guardar(sesion)

        assertEquals(sesion, almacen.sesion.value)
        assertTrue(prefs.datos.isEmpty())
    }

    private fun almacen() = AlmacenSesionKeystore(prefs, cifrador)

    /** SharedPreferences en memoria: solo lo que usa el almacén. */
    private class PrefsFalsas : SharedPreferences {
        val datos = mutableMapOf<String, String>()

        override fun getString(key: String, defValue: String?) = datos[key] ?: defValue
        override fun contains(key: String) = key in datos
        override fun getAll(): MutableMap<String, *> = datos
        override fun edit(): SharedPreferences.Editor = Editor()

        override fun getStringSet(key: String, defValues: MutableSet<String>?) = noSeUsa()
        override fun getInt(key: String, defValue: Int) = noSeUsa()
        override fun getLong(key: String, defValue: Long) = noSeUsa()
        override fun getFloat(key: String, defValue: Float) = noSeUsa()
        override fun getBoolean(key: String, defValue: Boolean) = noSeUsa()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { if (value == null) datos -= key else datos[key] = value }
            override fun remove(key: String) = apply { datos -= key }
            override fun clear() = apply { datos.clear() }
            override fun commit() = true
            override fun apply() = Unit

            override fun putStringSet(key: String, values: MutableSet<String>?) = noSeUsa()
            override fun putInt(key: String, value: Int) = noSeUsa()
            override fun putLong(key: String, value: Long) = noSeUsa()
            override fun putFloat(key: String, value: Float) = noSeUsa()
            override fun putBoolean(key: String, value: Boolean) = noSeUsa()
        }

        private fun noSeUsa(): Nothing = throw UnsupportedOperationException()
    }
}
