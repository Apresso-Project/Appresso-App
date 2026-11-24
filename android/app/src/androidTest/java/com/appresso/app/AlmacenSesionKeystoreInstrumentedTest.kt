package com.appresso.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appresso.app.data.auth.AlmacenSesionKeystore
import com.appresso.app.data.auth.CifradorSesion
import com.appresso.app.data.auth.Rol
import com.appresso.app.data.auth.Sesion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El almacén de la sesión con el Android Keystore real (8.4.1, SCRUM-102). Necesita un teléfono o un
 * emulador: `./gradlew connectedDebugAndroidTest`. Usa unas preferencias propias, así que no toca la
 * sesión abierta en la app.
 */
@RunWith(AndroidJUnit4::class)
class AlmacenSesionKeystoreInstrumentedTest {

    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("prueba_sesion", Context.MODE_PRIVATE)

    @After
    fun limpiar() {
        prefs.edit().clear().commit()
    }

    @Test
    fun laSesionSeCifraConElKeystoreYSeRecupera() {
        val sesion = Sesion("token.secreto.jwt", Rol.CLIENTE, "Ana")
        almacen().guardar(sesion)

        assertEquals(sesion, almacen().sesion.value)
        assertFalse(prefs.all.toString().contains("token.secreto.jwt"))

        almacen().borrar()
        assertNull(almacen().sesion.value)
    }

    @Test
    fun laClaveNoSePuedeExportar() {
        assertNull(CifradorSesion.claveDelKeystore().encoded)
    }

    private fun almacen() = AlmacenSesionKeystore(prefs, CifradorSesion(CifradorSesion::claveDelKeystore))
}
