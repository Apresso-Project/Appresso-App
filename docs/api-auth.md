# API de autenticación — Appresso

Contrato del inicio de sesión (entregables 2.1.1 y 7.1.1, RF-01) y de la recuperación de la contraseña del cliente (7.1.1, RF-65) para quien lo consuma desde la app Android. El registro del cliente (`POST /api/auth/registro`) está en [`api-clientes.md`](api-clientes.md). El código está en `backend/src/main/java/com/appresso/backend/auth/` y las pruebas en `backend/src/test/java/com/appresso/backend/auth/`.

> Estado: `POST /api/auth/login` emite el token y, desde 2.3.1, **los demás endpoints lo exigen y lo validan** según el rol. Qué rol puede usar cada endpoint está en [`permisos-por-rol.md`](permisos-por-rol.md).

## Iniciar sesión

`POST /api/auth/login` — público (no lleva token). Cuerpo y respuesta en JSON, con `Content-Type: application/json`.

El **personal** (administrador, cajero, mesero y cocinero) entra con su **correo**; el **cliente**, con su **número de documento**. Ambos con su contraseña. Si el identificador trae `@` se trata como correo; si no, como documento. Un cliente que intenta entrar con su correo, o alguien del personal con un documento, recibe `401` como unas credenciales inválidas.

### Solicitud

| Campo | Tipo | Reglas |
|---|---|---|
| `identificador` | texto | Obligatorio. Máximo 150 caracteres. El correo (sin distinguir mayúsculas) o el número de documento. El documento se compara sin espacios, puntos ni guiones y en mayúsculas: `1.234.567` y `1234567` son el mismo. |
| `contrasena` | texto | Obligatorio. Máximo 72 caracteres. El campo se llama `contrasena`, **sin ñ**. |

```json
{ "identificador": "admin@appresso.test", "contrasena": "la_clave" }
```

```json
{ "identificador": "1234567890", "contrasena": "la_clave_del_cliente" }
```

### Respuestas

| Código | Cuándo | Cuerpo |
|---|---|---|
| **200** | Credenciales válidas | `{ "token", "rol", "nombre" }` |
| **400** | Campos vacíos o inválidos, o JSON mal formado | `{ "mensaje", "campos"? }` |
| **401** | Cuenta inexistente, contraseña incorrecta, cuenta inactiva o identificador que no corresponde al rol | `{ "mensaje": "Credenciales inválidas" }` |
| **423** | Cuenta bloqueada por demasiados intentos fallidos | `{ "mensaje": "..." }` |

Todos los errores traen `mensaje`, un texto pensado para mostrarse al usuario (sin trazas ni datos técnicos). La app debe **mostrarlo tal cual y no depender de su contenido exacto**, salvo `401` ("Credenciales inválidas"), que es fijo.

#### 200 — inicio de sesión correcto

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "rol": "ADMINISTRADOR",
  "nombre": "Administrador de prueba"
}
```

- `rol` es uno de: `ADMINISTRADOR`, `CAJERO`, `MESERO`, `COCINERO`, `CLIENTE`. La app decide con él a qué interfaz navegar.
- `nombre` es el nombre del usuario, para mostrarlo en pantalla.
- La respuesta nunca incluye la contraseña ni su hash.

#### 400 — datos inválidos

```json
{
  "mensaje": "Revisa los datos ingresados.",
  "campos": {
    "identificador": "El correo o documento es obligatorio",
    "contrasena": "La contraseña es obligatoria"
  }
}
```

`campos` solo aparece en errores de validación y asocia cada campo con su problema (uno por campo), para señalarlo en el formulario. Si el cuerpo no es JSON válido, o falta, no hay `campos` y el `mensaje` es `"La solicitud no tiene un formato válido."`.

#### 401 — credenciales inválidas

```json
{ "mensaje": "Credenciales inválidas" }
```

Es **la misma respuesta** para cuenta inexistente, contraseña incorrecta, cuenta desactivada y un identificador del tipo equivocado (el correo de un cliente o un documento del personal). Es a propósito, para no revelar qué cuentas están registradas: la app no puede ni debe distinguirlos.

#### 423 — cuenta bloqueada

```json
{ "mensaje": "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos." }
```

El mensaje trae los minutos que faltan (en singular: "1 minuto."). No hay un campo numérico aparte; si la app lo necesitara, hay que pedirlo y agregarlo.

## Bloqueo por intentos fallidos (RNF-26)

- Cada contraseña incorrecta de una cuenta existente y activa suma un intento fallido. Vale igual para el personal y para los clientes.
- Al **5.º fallo seguido** la cuenta se bloquea **15 minutos**. Ese 5.º intento aún responde `401`; desde el 6.º responde `423`, **aunque la contraseña sea correcta**.
- Mientras dure el bloqueo no se cuentan más intentos.
- Un inicio de sesión correcto reinicia el contador a cero.
- Pasados los 15 minutos, el contador empieza de cero (un fallo cuenta como el primero).

## El token

Es un JWT firmado (HS256) con una clave que solo conoce el servidor.

| Dato (claim) | Contenido |
|---|---|
| `iss` | `appresso` |
| `sub` | Id del usuario (como texto) |
| `rol` | El mismo valor que devuelve la respuesta |
| `iat` / `exp` | Emisión y vencimiento (segundos desde 1970) |

- **Vigencia:** 12 horas por defecto (`JWT_EXPIRACION_MINUTOS`, 720). Cerrar la sesión por 30 minutos de inactividad (RNF-26.2) se resuelve en 2.3.1; hoy no está.
- **Contenido legible:** cualquiera que tenga el token puede leer sus datos, pero no modificarlos sin que la firma falle. Por eso no lleva correo ni datos sensibles.
- **La app no verifica la firma** (no tiene la clave). Puede leer `exp` para saber cuándo pedir un nuevo inicio de sesión, pero quien decide si el token vale es siempre el servidor.
- **Almacenamiento en Android:** en Android Keystore (entregable 8.4.1). Mientras tanto, detrás de una interfaz propia para poder cambiarlo sin tocar el resto.
- **Cerrar sesión** es solo borrar el token en el teléfono: el servidor no guarda sesiones.

### Cómo se envía

Toda petición a un endpoint protegido lleva `Authorization: Bearer <token>` (confirmado en 2.3.1).

| Código | Cuándo | Qué hace la app |
|---|---|---|
| **401** | Sin token, o token vencido, alterado o de una cuenta desactivada | Borrar la sesión y volver al login |
| **403** | Token válido, pero el rol no tiene permiso | Mostrar el `mensaje` y quedarse en la pantalla |

Ambos responden `{ "mensaje": "..." }`, igual que el resto de errores. El detalle de los permisos está en [`permisos-por-rol.md`](permisos-por-rol.md).

## Qué debe hacer la app con cada respuesta

| Resultado | Comportamiento |
|---|---|
| **200** | Guardar el token, navegar según `rol` y mostrar `nombre`. |
| **400** | Señalar cada campo de `campos` en el formulario, conservando lo que escribió el usuario (RNF-19). |
| **401** | Mostrar "Credenciales inválidas" sin indicar qué dato falló. |
| **423** | Mostrar el `mensaje` del servidor. |
| Sin conexión o tiempo agotado | "Sin conexión a Internet. Por favor, verifica tu red." (RF-05, RNF-21). No es una respuesta del servidor: el cliente HTTP la produce. |
| Otro error (5xx) | Un mensaje genérico de "algo salió mal, intenta de nuevo", sin detalles técnicos. |

Antes de enviar, la app puede validar lo mismo que el servidor (campos obligatorios, formato del correo o del documento, máximo de la contraseña) para ahorrar una petición.

## Recuperar la contraseña del cliente (RF-65)

Solo para **cuentas de cliente**. Las contraseñas del personal las gestiona el administrador desde la administración de usuarios ([`api-usuarios.md`](api-usuarios.md)). Ambos endpoints son públicos (sin token).

### 1. Pedir el código — `POST /api/auth/recuperar`

```json
{ "documento": "1234567890" }
```

Responde **siempre** `200` con el mismo cuerpo, exista o no el documento, para no revelar qué documentos están registrados:

```json
{ "mensaje": "Si el documento está registrado, enviamos un código al correo asociado." }
```

- Si el documento es de un cliente con cuenta activa, se envía a su correo un **código de 6 dígitos** que vence a los **15 minutos** y sirve **una sola vez**. El correo sale en segundo plano, después de responder.
- Pedir un código nuevo invalida los anteriores. Si se pidió uno hace menos de **60 segundos**, no se envía otro (la respuesta es la misma).
- `400` si falta el documento (`campos.documento`).

### 2. Cambiar la contraseña — `POST /api/auth/restablecer`

```json
{ "documento": "1234567890", "codigo": "482913", "contrasenaNueva": "Clave-Nueva-123" }
```

| Código | Cuándo | Cuerpo |
|---|---|---|
| **200** | Código vigente y contraseña válida | `{ "mensaje": "Tu contraseña se actualizó. Ya puedes iniciar sesión." }` |
| **400** | El código es incorrecto, ya se usó, venció o el documento no tiene cuenta | `{ "mensaje": "El código no es válido o venció. Solicita uno nuevo.", "campos": { "codigo": "..." } }` |
| **400** | Falta un campo, el código no tiene 6 dígitos o la contraseña no tiene entre 8 y 72 caracteres (72 bytes) | `{ "mensaje": "Revisa los datos ingresados.", "campos": { ... } }` |

- Tras **5 intentos** con un código incorrecto, ese código deja de servir y hay que pedir otro.
- Una contraseña nueva inválida no gasta el código.
- Al cambiar la contraseña, la cuenta se desbloquea (borra los intentos fallidos del inicio de sesión). Las sesiones abiertas en otros teléfonos siguen hasta que venza su token.
- Pedir y usar el código queda en la auditoría (`CODIGO_RECUPERACION_SOLICITADO`, `CONTRASENA_RESTABLECIDA`), sin guardar el código.

### El envío del correo

El servidor SMTP se configura con las variables `CORREO_HOST`, `CORREO_PUERTO` (587 por defecto, con STARTTLS), `CORREO_USUARIO`, `CORREO_CONTRASENA` y `CORREO_REMITENTE` (en `backend/.env` o en el servidor). Sin `CORREO_HOST` no se envía nada: con el perfil `dev` el código aparece en la consola del backend (`[dev] Sin servidor de correo. Código de recuperación para ...`), para probar en local; sin ese perfil solo se registra un aviso.

## Probarlo a mano

Con el backend encendido y el administrador de prueba creado (ver `backend/README.md`, paso 1 y paso 5):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identificador":"admin@appresso.test","contrasena":"la_clave"}'
```

El cliente de prueba (perfil `dev`) entra con su documento: `{"identificador":"1000000001","contrasena":"..."}`.

Desde un emulador de Android, `localhost` es el propio emulador: el backend de tu computador se alcanza en `http://10.0.2.2:8080`. En un teléfono físico, usa la IP de tu computador en la red Wi-Fi.

**Ojo con HTTP:** desde Android 9 la app bloquea por defecto las conexiones sin cifrar, y el backend local responde por `http://`. En desarrollo hay que permitirlo solo para el entorno de depuración (por ejemplo, con una configuración de seguridad de red que aplique únicamente a `debug`); en producción todo va por HTTPS (8.4.1 y 8.5.1). La URL base debe poder cambiar según el tipo de compilación.

## Fuera de este contrato (por ahora)

- Cierre de sesión por 30 minutos de inactividad (RNF-26.2): pendiente.
- Renovación del token (refresh) y cierre de sesión en el servidor: no previstos.
- Registro de los intentos fallidos en la auditoría (RNF-12): pendiente de decidir.
- Almacenamiento seguro del token en Android y HTTPS: **8.4.1**.
