# API de autenticación — Appresso

Contrato del inicio de sesión (entregable 2.1.1, RF-01) para quien lo consuma desde la app Android. El código está en `backend/src/main/java/com/appresso/backend/auth/` y las pruebas en `backend/src/test/java/com/appresso/backend/auth/`.

> Estado: existe **un solo endpoint**, `POST /api/auth/login`. El backend **emite** el token, pero todavía **ningún endpoint lo exige ni lo valida**: eso llega con el entregable 2.3.1 (control de acceso por rol).

## Iniciar sesión

`POST /api/auth/login` — público (no lleva token). Cuerpo y respuesta en JSON, con `Content-Type: application/json`.

### Solicitud

| Campo | Tipo | Reglas |
|---|---|---|
| `correo` | texto | Obligatorio. Con formato de correo. Máximo 150 caracteres. No distingue mayúsculas de minúsculas. |
| `contrasena` | texto | Obligatorio. Máximo 72 caracteres. El campo se llama `contrasena`, **sin ñ**. |

```json
{ "correo": "admin@appresso.test", "contrasena": "la_clave" }
```

### Respuestas

| Código | Cuándo | Cuerpo |
|---|---|---|
| **200** | Credenciales válidas | `{ "token", "rol", "nombre" }` |
| **400** | Campos vacíos o inválidos, o JSON mal formado | `{ "mensaje", "campos"? }` |
| **401** | Correo inexistente, contraseña incorrecta o cuenta inactiva | `{ "mensaje": "Credenciales inválidas" }` |
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
    "correo": "El correo no tiene un formato válido",
    "contrasena": "La contraseña es obligatoria"
  }
}
```

`campos` solo aparece en errores de validación y asocia cada campo con su problema (uno por campo), para señalarlo en el formulario. Si el cuerpo no es JSON válido, o falta, no hay `campos` y el `mensaje` es `"La solicitud no tiene un formato válido."`.

#### 401 — credenciales inválidas

```json
{ "mensaje": "Credenciales inválidas" }
```

Es **la misma respuesta** para correo inexistente, contraseña incorrecta y cuenta desactivada. Es a propósito, para no revelar qué correos están registrados: la app no puede ni debe distinguirlos.

#### 423 — cuenta bloqueada

```json
{ "mensaje": "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta de nuevo en 15 minutos." }
```

El mensaje trae los minutos que faltan (en singular: "1 minuto."). No hay un campo numérico aparte; si la app lo necesitara, hay que pedirlo y agregarlo.

## Bloqueo por intentos fallidos (RNF-26)

- Cada contraseña incorrecta de una cuenta existente y activa suma un intento fallido.
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

### Cómo se enviará (previsto, aún sin efecto)

Las peticiones a endpoints protegidos llevarán `Authorization: Bearer <token>`. Es la convención prevista para 2.3.1; hasta entonces ningún endpoint la lee, y 2.3.1 puede confirmarla o ajustarla.

## Qué debe hacer la app con cada respuesta

| Resultado | Comportamiento |
|---|---|
| **200** | Guardar el token, navegar según `rol` y mostrar `nombre`. |
| **400** | Señalar cada campo de `campos` en el formulario, conservando lo que escribió el usuario (RNF-19). |
| **401** | Mostrar "Credenciales inválidas" sin indicar qué dato falló. |
| **423** | Mostrar el `mensaje` del servidor. |
| Sin conexión o tiempo agotado | "Sin conexión a Internet. Por favor, verifica tu red." (RF-05, RNF-21). No es una respuesta del servidor: el cliente HTTP la produce. |
| Otro error (5xx) | Un mensaje genérico de "algo salió mal, intenta de nuevo", sin detalles técnicos. |

Antes de enviar, la app puede validar lo mismo que el servidor (campos obligatorios, formato del correo, máximo de la contraseña) para ahorrar una petición.

## Probarlo a mano

Con el backend encendido y el administrador de prueba creado (ver `backend/README.md`, paso 1 y paso 5):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"admin@appresso.test","contrasena":"la_clave"}'
```

Desde un emulador de Android, `localhost` es el propio emulador: el backend de tu computador se alcanza en `http://10.0.2.2:8080`. En un teléfono físico, usa la IP de tu computador en la red Wi-Fi.

**Ojo con HTTP:** desde Android 9 la app bloquea por defecto las conexiones sin cifrar, y el backend local responde por `http://`. En desarrollo hay que permitirlo solo para el entorno de depuración (por ejemplo, con una configuración de seguridad de red que aplique únicamente a `debug`); en producción todo va por HTTPS (8.4.1 y 8.5.1). La URL base debe poder cambiar según el tipo de compilación.

## Fuera de este contrato (por ahora)

- Validación del token y autorización por rol en los demás endpoints, y cierre de sesión por inactividad: **2.3.1**.
- Renovación del token (refresh) y cierre de sesión en el servidor: no previstos.
- Registro de los intentos fallidos en la auditoría (RNF-12): pendiente de decidir.
- Almacenamiento seguro del token en Android y HTTPS: **8.4.1**.
