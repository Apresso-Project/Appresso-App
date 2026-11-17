package com.appresso.app.data.auth

import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lee del token de la sesión el id del usuario (campo `sub` del JWT). Sirve solo para la interfaz,
 * por ejemplo para no ofrecer a un administrador acciones sobre su propia cuenta; no valida la firma
 * (eso lo hace el servidor) y no es seguridad. Si el token no se puede leer devuelve `null`, y
 * entonces la pantalla simplemente no oculta nada: el servidor igual rechaza la acción.
 */
object TokenSesion {

    fun idDeUsuario(token: String): Long? {
        val cuerpo = token.split('.').getOrNull(1) ?: return null
        return try {
            val json = String(Base64.getUrlDecoder().decode(cuerpo), Charsets.UTF_8)
            Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content?.toLongOrNull()
        } catch (e: IllegalArgumentException) {
            null // Base64 inválido, o el JSON no es un objeto
        } catch (e: SerializationException) {
            null
        }
    }
}
