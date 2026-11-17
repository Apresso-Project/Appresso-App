# API de administración de usuarios — Appresso

Contrato del entregable 2.2.1 (RF-02) para quien lo consuma desde la app Android. El código está en `backend/src/main/java/com/appresso/backend/usuarios/` y las pruebas en `backend/src/test/java/com/appresso/backend/usuarios/`.

> Todos los endpoints son **exclusivos del administrador** (RF-02, RF-04, RNF-07). Para llamarlos hay que haber iniciado sesión (`docs/api-auth.md`) y enviar el token en cada petición.

## Acceso

Cada petición lleva el encabezado:

```
Authorization: Bearer <token>
```

| Código | Cuándo | Cuerpo |
|---|---|---|
| **401** | Falta el encabezado, el token es inválido o venció, o la cuenta ya no existe o está desactivada | `{ "mensaje": "Tu sesión no es válida o expiró. Inicia sesión de nuevo." }` |
| **403** | Sesión válida pero el rol **no es administrador** | `{ "mensaje": "No tienes permiso para realizar esta acción." }` |

Cómo decide el servidor:

- Mira el rol que la cuenta tiene **en la base de datos en este momento**, no el que trae el token. Por eso, si el administrador cambia un rol (RF-02, RF-03) o desactiva una cuenta, el efecto es inmediato aunque el usuario siga con su token viejo (válido hasta 12 horas).
- La app, ante un `401`, vuelve a la pantalla de inicio de sesión; ante un `403`, muestra el aviso "No tienes permiso para realizar esta acción." (RF-04). Ambos los maneja el cliente HTTP compartido de la app.
- Esta verificación la hace la seguridad común del backend (2.3.1): la regla `/api/usuarios/**` de `PoliticaPermisos` (ver [`permisos-por-rol.md`](permisos-por-rol.md)). El controlador no repite la comprobación.

## Endpoints

| Método y ruta | Qué hace | Éxito |
|---|---|---|
| `GET /api/usuarios` | Lista todas las cuentas (activas e inactivas), ordenadas por nombre | 200, arreglo de usuarios |
| `GET /api/usuarios/{id}` | Una cuenta | 200 |
| `POST /api/usuarios` | Crea una cuenta | 201 + cabecera `Location` |
| `PUT /api/usuarios/{id}` | Edita nombre, correo, rol y, opcionalmente, la contraseña | 200 |
| `POST /api/usuarios/{id}/desactivar` | Desactiva la cuenta (no la borra) | 200 |
| `POST /api/usuarios/{id}/activar` | Vuelve a activar una cuenta desactivada | 200 |

Cuerpos y respuestas en JSON (`Content-Type: application/json`). Las respuestas de crear, editar, desactivar y activar devuelven la cuenta ya actualizada, para que la lista de la app se refresque sin volver a pedirla.

### El objeto usuario

```json
{
  "id": 7,
  "nombre": "Ana Mesera",
  "correo": "ana@appresso.test",
  "rol": "MESERO",
  "activo": true,
  "fechaCreacion": "2026-11-12T17:03:11.412Z"
}
```

`rol` es uno de `ADMINISTRADOR`, `CAJERO`, `MESERO`, `COCINERO`, `CLIENTE`. **Nunca** se devuelve la contraseña ni su hash.

### Crear — `POST /api/usuarios`

| Campo | Tipo | Reglas |
|---|---|---|
| `nombre` | texto | Obligatorio. Máximo 100 caracteres. |
| `correo` | texto | Obligatorio. Formato de correo. Máximo 150. **Único** sin distinguir mayúsculas. |
| `contrasena` | texto | Obligatoria. Entre 8 y 72 caracteres (y máximo 72 bytes). El campo se llama `contrasena`, **sin ñ**. |
| `rol` | texto | Obligatorio. Uno de los cinco roles. |

```json
{ "nombre": "Ana Mesera", "correo": "ana@appresso.test", "contrasena": "Clave-Segura-1", "rol": "MESERO" }
```

La contraseña se guarda cifrada con BCrypt (RNF-05). La cuenta nace activa.

### Editar — `PUT /api/usuarios/{id}`

Mismos campos que crear, pero `contrasena` es **opcional**: si no viene (o es `null`) la contraseña no cambia; si viene, la reemplaza **y desbloquea la cuenta** (borra los intentos fallidos). Se envían siempre `nombre`, `correo` y `rol` (la app manda los valores actuales de lo que no cambió).

### Desactivar y activar

- **Desactivar** no borra nada: la cuenta conserva su historial, desaparece de los inicios de sesión (`401` "Credenciales inválidas") y sus tokens dejan de servir de inmediato. Sigue apareciendo en la lista con `activo: false`. Si ya estaba desactivada, responde `200` sin hacer nada.
- **Activar** la reactiva y la desbloquea. Existe para deshacer una desactivación por error.

## Errores

Todos traen `mensaje` (texto para mostrar al usuario, sin datos técnicos); los de validación traen además `campos`, con un mensaje por campo.

| Código | Cuándo | Cuerpo |
|---|---|---|
| **400** | Campos inválidos | `{ "mensaje": "Revisa los datos ingresados.", "campos": { "correo": "El correo no tiene un formato válido" } }` |
| **400** | JSON mal formado, rol que no existe o id que no es número | `{ "mensaje": "La solicitud no tiene un formato válido." }` |
| **404** | La cuenta no existe | `{ "mensaje": "El usuario 99 no existe" }` |
| **409** | Correo ya usado por otra cuenta | `{ "mensaje": "Ya existe una cuenta con ese correo", "campos": { "correo": "..." } }` |
| **409** | Regla de negocio (ver abajo) | `{ "mensaje": "No puedes desactivar tu propia cuenta. ..." }` |

## Reglas de negocio

- **Un administrador no puede desactivar su propia cuenta ni cambiarse el rol.** Así el sistema no se queda sin administradores por accidente. Otro administrador sí puede hacerlo. Responde `409`.
- **Siempre queda al menos un administrador activo.** Desactivar o cambiar de rol al único administrador activo responde `409` (`"Debe quedar al menos un administrador activo."`), aunque dos administradores lo intenten a la vez (el servidor atiende esas solicitudes una tras otra).
- **Cambiar una contraseña no cierra las sesiones abiertas** de esa cuenta: su token sigue sirviendo hasta que venza (12 h). Si hay que cortar el acceso de inmediato, se desactiva la cuenta (eso sí es inmediato) y se vuelve a activar.
- El correo es único sin importar mayúsculas ("Ana@x.com" y "ana@x.com" son el mismo). Se guarda sin espacios sobrantes.
- Cambiar un rol surte efecto **de inmediato** (RF-02, RF-03).
- **Auditoría (RNF-12):** crear, editar (solo si algo cambió), desactivar y activar quedan en `registros_auditoria` con el administrador responsable, la fecha y un detalle del cambio (por ejemplo `rol MESERO -> COCINERO`). La contraseña nunca se guarda ahí.

## Qué debe hacer la app

| Resultado | Comportamiento |
|---|---|
| **200 / 201** | Reemplazar o agregar la cuenta devuelta en la lista, sin recargar toda la pantalla. |
| **400** con `campos` | Señalar cada campo en el formulario, conservando lo escrito (RNF-19). |
| **401** | Cerrar la sesión local y volver al inicio de sesión (lo hace el cliente HTTP compartido). |
| **403** | Aviso "No tienes permiso para realizar esta acción." (cliente HTTP compartido). La pantalla de usuarios solo se muestra al administrador. |
| **404** | "Ese usuario ya no existe" y recargar la lista. |
| **409** | Mostrar el `mensaje` (y señalar `campos.correo` si viene). |
| Sin conexión o tiempo agotado | "Sin conexión a Internet. Por favor, verifica tu red." (RF-05, RNF-21). |

Antes de **desactivar**, la app pide confirmación (SCRUM-58): es la acción que le quita el acceso a una persona.

## Límites actuales

- La lista devuelve **todas** las cuentas sin paginar: el personal de una cafetería son pocas decenas.
- Las cuentas de rol `CLIENTE` creadas aquí no crean el perfil de fidelización; eso lo gestiona el módulo de clientes.
- No hay "olvidé mi contraseña": el administrador la restablece editando la cuenta con una contraseña nueva.

## Probarlo a mano

Con el backend en marcha (`backend/README.md`) y el administrador de prueba creado:

```bash
# 1. Iniciar sesión y copiar el token
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"admin@appresso.test","contrasena":"TU_CLAVE"}'

# 2. Listar (reemplaza TOKEN)
curl -s http://localhost:8080/api/usuarios -H "Authorization: Bearer TOKEN"

# 3. Crear
curl -s -X POST http://localhost:8080/api/usuarios -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"nombre":"Ana Mesera","correo":"ana@appresso.test","contrasena":"Clave-Segura-1","rol":"MESERO"}'
```

Sin el encabezado `Authorization` debe responder `401`; con el token de un mesero, `403`.
