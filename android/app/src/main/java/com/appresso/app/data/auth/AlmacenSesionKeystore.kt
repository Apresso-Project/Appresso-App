package com.appresso.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Guarda la sesión cifrada (8.4.1, SCRUM-102): en el teléfono queda solo el texto cifrado, en
 * SharedPreferences privadas, y la clave está en Android Keystore ([CifradorSesion]). El token nunca
 * se escribe en texto plano.
 *
 * Si el cifrado falla, la sesión queda solo en memoria (se pierde al cerrar la app) antes que
 * guardarse sin cifrar. Si lo guardado no se puede descifrar, se borra y hay que iniciar sesión.
 */
class AlmacenSesionKeystore(
    private val prefs: SharedPreferences,
    private val cifrador: CifradorSesion,
) : AlmacenSesion {

    private val _sesion = MutableStateFlow(cargar())
    override val sesion: StateFlow<Sesion?> = _sesion.asStateFlow()

    override fun guardar(sesion: Sesion) {
        val cifrada = try {
            cifrador.cifrar(Json.encodeToString(SesionGuardada(sesion.token, sesion.rol.name, sesion.nombre)))
        } catch (e: Exception) {
            null
        }
        prefs.edit { if (cifrada != null) putString(CLAVE_SESION, cifrada) else remove(CLAVE_SESION) }
        _sesion.value = sesion
    }

    override fun borrar() {
        prefs.edit { remove(CLAVE_SESION) }
        _sesion.value = null
    }

    private fun cargar(): Sesion? {
        val cifrada = prefs.getString(CLAVE_SESION, null) ?: return null
        return try {
            val datos = Json.decodeFromString<SesionGuardada>(cifrador.descifrar(cifrada))
            Sesion(token = datos.token, rol = Rol.valueOf(datos.rol), nombre = datos.nombre)
        } catch (e: Exception) {
            prefs.edit { remove(CLAVE_SESION) }
            null
        }
    }

    @Serializable
    private class SesionGuardada(val token: String, val rol: String, val nombre: String)

    companion object {
        /** El mismo nombre está en `res/xml/backup_rules.xml`. */
        const val NOMBRE_PREFS = "appresso_sesion_segura"
        const val CLAVE_SESION = "sesion_cifrada"

        fun crear(context: Context): AlmacenSesion = AlmacenSesionKeystore(
            context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE),
            CifradorSesion(CifradorSesion::claveDelKeystore),
        )
    }
}
