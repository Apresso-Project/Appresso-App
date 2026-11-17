package com.appresso.app.data.usuarios

/**
 * Administración de cuentas (solo rol ADMINISTRADOR). Existe como interfaz para poder
 * sustituirla en las pruebas. Nunca lanza por errores de red o del servidor: los devuelve
 * como [ResultadoUsuarios]. Las operaciones que modifican devuelven el usuario ya actualizado,
 * para que la lista se pueda refrescar sin volver a pedirla.
 */
interface UsuariosRepository {

    suspend fun listar(): ResultadoUsuarios<List<Usuario>>

    suspend fun crear(datos: DatosUsuario): ResultadoUsuarios<Usuario>

    suspend fun editar(id: Long, datos: DatosUsuario): ResultadoUsuarios<Usuario>

    suspend fun desactivar(id: Long): ResultadoUsuarios<Usuario>

    suspend fun activar(id: Long): ResultadoUsuarios<Usuario>
}
