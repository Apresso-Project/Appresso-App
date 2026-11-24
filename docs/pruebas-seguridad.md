# Pruebas de seguridad — Appresso

Evidencia del entregable 8.4.1 (SCRUM-104): contraseñas con hash, HTTPS y token. Lo que comprueba cada medida y cómo está hecha se explica en [`seguridad-y-ley-1581.md`](seguridad-y-ley-1581.md).

| Criterio | Cómo se verifica | Resultado |
|---|---|:-:|
| Las contraseñas no se almacenan en texto plano (8.4.1, RNF-05) | Parte A | ✅ 13 / 13 |
| Todas las comunicaciones usan HTTPS (8.4.1, RNF-06) | Parte B | ✅ 19 / 19 en local; en el servidor, con 8.5.1 |
| El token es seguro en el servidor (RNF-07) | Parte C | ✅ 17 / 17 |
| El token se almacena en Android Keystore (8.4.1) | Parte D | ✅ automatizadas; **falta el teléfono** |
| Todo endpoint protegido rechaza a los roles no autorizados (RNF-07) | [`verificacion-roles.md`](verificacion-roles.md) | ✅ 258 / 258 |
| El cliente puede eliminar sus datos (8.4.1, RNF-09) | [`eliminacion-de-datos.md`](eliminacion-de-datos.md) | ✅ 17 / 17; **falta el teléfono** |

**Fecha:** 22/11/2026 · **Responsable:** Felipe Salcedo · **Rama:** `feat/8.4.1-seguridad-y-ley-1581`, fusionada con `main` · **Commit:** ____

## Cómo se hizo

- **Backend:** el `.jar` empaquetado, arrancado de cuatro formas: perfil `dev`; como en producción (`EXIGIR_HTTPS=true`, sin `dev`); igual, pero sin confiar en quien hace la solicitud; y con HTTPS real (perfil `https`, certificado de prueba).
- **Base:** PostgreSQL 17 en un contenedor temporal, con datos de prueba. Para saber si un dato queda guardado se busca en el **volcado completo** (`pg_dump`), no solo en su tabla.
- **App:** el manifest y los recursos se leyeron de los APK de release y de debug ya compilados.
- **Detalle de cada caso:** [`evidencias/pruebas-seguridad.csv`](evidencias/pruebas-seguridad.csv).

Las pruebas automatizadas cubren en cada PR las reglas del código (no el volcado de la base, TLS ni los APK): backend 431 (`AuthControllerTest`, `JwtServiceTest`, `ExigirHttpsTest`, `SinExigirHttpsTest`, `PoliticaPermisosTest`, `RecuperacionContrasenaTest`, `ClienteSupresionTest`, `ClienteSupresionBloqueoTest`) y Android 301 (`CifradorSesionTest`, `AlmacenSesionKeystoreTest`, `InterceptorSesionTest`, `MisDatos*Test`), todas sin fallos.

## Parte A — Contraseñas con hash (RNF-05)

| Caso | Comprobación | Resultado |
|---|---|:-:|
| A1 | El registro responde 201 y no devuelve la contraseña ni su hash | ✅ |
| A2 | Crear personal no devuelve la contraseña ni su hash | ✅ |
| A3 | Todas las contraseñas guardadas son BCrypt de costo 10 o más | ✅ |
| A4 | La misma contraseña en dos cuentas da hashes distintos (sal) | ✅ |
| A5 | Ninguna contraseña aparece en el volcado completo de la base | ✅ |
| A6 | Con la contraseña correcta se entra y con otra no | ✅ |
| A7 | Más de 72 bytes se rechaza en vez de truncarse | ✅ |
| A8 | El hash no sirve como contraseña | ✅ |
| A9 | Cuenta inexistente y contraseña incorrecta responden igual | ✅ |
| A10 | Al quinto fallo la cuenta se bloquea aun con la correcta (423) | ✅ |
| A11 | El código de recuperación se guarda con BCrypt, no en claro | ✅ |
| A12 | Tras restablecer, la nueva entra, la anterior no, y queda con hash | ✅ |
| A13 | Ninguna contraseña aparece en el registro (log) del backend | ✅ |

## Parte B — HTTPS en las comunicaciones (RNF-06)

| Caso | Comprobación | Resultado |
|---|---|:-:|
| B1 | Login por HTTP: 426 y mensaje | ✅ |
| B2 | Endpoint protegido por HTTP: 426 (ni siquiera lee el token) | ✅ |
| B3 | Registro, recuperación y eliminación por HTTP: 426 | ✅ |
| B4 | WebSocket sin cifrar (ws://): 426 | ✅ |
| B5 | No redirige (una redirección ya habría enviado la contraseña en claro) | ✅ |
| B6 | El chequeo de salud del servidor sigue respondiendo por HTTP | ✅ |
| B7 | Con proxy TLS (X-Forwarded-Proto: https) la solicitud sigue normal | ✅ |
| B8 | HSTS de un año en las respuestas HTTPS | ✅ |
| B9 | WebSocket detrás del proxy TLS (wss://): 101 | ✅ |
| B10 | Con X-Forwarded-Proto: http se rechaza (426) | ✅ |
| B11 | Sin perfil dev, el código de recuperación no se escribe en el registro | ✅ |
| B12 | Sin perfil dev no se crean usuarios de prueba | ✅ |
| B13 | X-Forwarded-Proto: https enviado por quien no es el proxy: 426 | ✅ |
| B14 | HTTPS real: el backend responde | ✅ |
| B15 | Un cliente que valida certificados rechaza el autofirmado | ✅ |
| B16 | http:// al puerto HTTPS no funciona | ✅ |
| B17 | Acepta TLS 1.2 y 1.3; rechaza 1.0 y 1.1 | ✅ |
| B18 | WebSocket cifrado (wss://): 101 | ✅ |
| B19 | HSTS presente con HTTPS real | ✅ |

**En la app** (leído de los APK):

| Comprobación | Release | Debug |
|---|---|---|
| Permite conexiones sin cifrar (`cleartextTrafficPermitted`) | No ✅ | Sí, para el backend local |
| Certificados en los que confía | Solo los del sistema ✅ | Solo los del sistema |
| Dirección del backend | `https://appresso.invalid/` (la real se pone en 8.5.1) ✅ | La de cada equipo, por `http://` |
| Copias de seguridad (`allowBackup`) | Desactivadas ✅ | Desactivadas ✅ |

### Por repetir en el servidor (8.5.1)

Cambia `<servidor>` por la dirección real.

| Comprobación | Comando | Esperado | ✅/❌ |
|---|---|---|:-:|
| HTTP no sirve | `curl -i http://<servidor>/api/health` | `426`, o la redirección a HTTPS del proveedor | |
| HTTPS con certificado válido | `curl -i https://<servidor>/api/health` (sin `-k`) | `200` | |
| HSTS | El mismo comando | `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` | |
| TLS antiguo rechazado | `openssl s_client -connect <servidor>:443 -tls1_1` | Falla la conexión | |
| Nadie se hace pasar por el proxy | `curl -i -H "X-Forwarded-Proto: https" http://<servidor>/api/health` | No responde `200` | |
| La app de release inicia sesión y recibe cambios en tiempo real | En el teléfono | Funciona | |

## Parte C — Token en el servidor (RNF-07)

| Caso | Comprobación | Resultado |
|---|---|:-:|
| C1 | El token válido abre lo propio | ✅ |
| C2 | El token no lleva datos personales ni contraseña | ✅ |
| C3 | Vence a las 12 horas | ✅ |
| C4 | Sin token responde 401 | ✅ |
| C5 | Cambiar el rol dentro del token lo invalida (401) | ✅ |
| C6 | Sin firma (alg none) responde 401 | ✅ |
| C7 | Firmado con otra clave responde 401 | ✅ |
| C8 | Control: refirmado con la clave correcta sí sirve | ✅ |
| C9 | Vencido responde 401 | ✅ |
| C10 | Sin vencimiento responde 401 | ✅ |
| C11 | De otro emisor responde 401 | ✅ |
| C12 | En la URL no se acepta (solo en la cabecera) | ✅ |
| C13 | Aun con firma válida, manda el rol de la base, no el del token | ✅ |
| C14 | Al desactivar una cuenta, su token deja de servir de inmediato | ✅ |
| C15 | El cliente no entra a lo del personal (403) | ✅ |
| C16 | Ningún token aparece en el registro del backend | ✅ |
| C17 | El servidor no guarda tokens en la base | ✅ |

## Parte D — Token en el teléfono (Android Keystore)

**Automatizadas (en la JVM, con una clave por software):** ✅ 15 pruebas. Nada queda en texto plano, un dato alterado o de otra clave se descarta, y si el cifrado falla la sesión queda solo en memoria.

**En el teléfono, por hacer.** Con la versión debug instalada y el teléfono por USB (`adb` está en `Android/Sdk/platform-tools`):

| # | Pasos | Esperado | ✅/❌ |
|---|---|---|:-:|
| 1 | En `android/`: `./gradlew connectedDebugAndroidTest` (puede desinstalar la app; reinstálala) | Las 2 pruebas de `AlmacenSesionKeystoreInstrumentedTest` pasan | |
| 2 | Iniciar sesión y ejecutar `adb shell run-as com.appresso.app cat shared_prefs/appresso_sesion_segura.xml` | Solo `sesion_cifrada` con texto ilegible: sin nombre, rol ni un token (empieza por `eyJ`) | |
| 3 | Cerrar la app por completo y abrirla | La sesión sigue abierta | |
| 4 | Cerrar sesión y repetir el comando del caso 2 | Ya no está `sesion_cifrada` | |
| 5 | Con la sesión abierta, que el administrador desactive esa cuenta; luego abrir una sección que consulte al servidor (por ejemplo, Clientes y buscar) | La app vuelve al inicio de sesión | |
| 6 | Iniciar sesión y usar la app con `adb logcat` abierto; buscar `eyJ` y la contraseña | No aparecen | |

**Dispositivo:** ____ (Android __) · **Fecha:** ____ · **Capturas:** ____

## Observaciones

Ninguna incumple los criterios de 8.4.1.

| # | Observación | Dónde se resuelve |
|---|---|---|
| 1 | El certificado, la redirección y TLS de producción solo se pueden comprobar con el servidor (RNF-06, criterios 1 y 3) | 8.5.1, tabla de la parte B |
| 2 | El backend confía en `X-Forwarded-Proto` si llega desde una dirección de red interna. Confirmar que el proveedor lo reemplaza y que el backend no queda expuesto sin pasar por el proxy | 8.5.1 |
| 3 | Las sesiones abiertas siguen hasta vencer (12 h) tras restablecer la contraseña | Por decidir |
| 4 | El registro público no tiene límite de solicitudes | Por decidir |
| 5 | Cierre de sesión por 30 minutos de inactividad (RNF-26.2), más visible ahora que la sesión se conserva al cerrar la app | Por decidir |
| 6 | La app de release no se ha ejecutado en un teléfono (falta la firma y la dirección real) | 8.5.1 |
