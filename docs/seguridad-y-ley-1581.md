# Seguridad y Cumplimiento Legal (Ley 1581 de 2012) — Appresso

Este documento especifica e instruye las medidas de seguridad informática implementadas en el sistema Appresso y los mecanismos de cumplimiento legal de protección de datos personales de acuerdo con la legislación colombiana (**Ley Estatutaria 1581 de 2012** y **Decreto Reglamentario 1377 de 2013**).

---

## 1. Requisitos de Seguridad

### 1.1. Cifrado y gestión de contraseñas con BCrypt
- **Prohibición de texto plano:** Ninguna contraseña se almacena, transmite sin cifrar ni se registra en archivos de log o volcados de memoria.
- **Algoritmo:** `BCryptPasswordEncoder` (Spring Security), algoritmo adaptativo basado en Blowfish con factor de costo predeterminado (salt criptográfico aleatorio autogenerado por cada hash).
- **Longitud máxima:** 72 bytes (límite estructural de BCrypt para evitar truncamientos silenciosos). La validación rechaza contraseñas mayores antes del cómputo.
- **Exposición en API:** Los DTOs de salida (`UsuarioRespuesta`, `LoginResponse`, etc.) jamás incluyen el hash ni la contraseña del usuario.

### 1.2. Implementación de HTTPS en todas las comunicaciones
- **Cifrado en tránsito (TLS/HTTPS):** Todas las solicitudes REST y el protocolo de tiempo real STOMP sobre WebSocket se comunican mediante canales cifrados:
  - URLs HTTP base en producción: `https://...`
  - URLs WebSocket en producción: `wss://...` (las arma `urlTiempoReal` a partir de `API_BASE_URL`).
- **Android Network Security Configuration:** Archivo `android/app/src/main/res/xml/network_security_config.xml` configurado con `<base-config cleartextTrafficPermitted="false">`, impidiendo de forma estricta cualquier tráfico en claro (HTTP sin cifrar) en compilaciones de producción. Solo las compilaciones `debug` lo permiten (`android/app/src/debug/res/xml/network_security_config.xml`), para el backend local.
- **HSTS (HTTP Strict Transport Security):** En el backend Spring Boot (`SeguridadConfig.java`), se emite la cabecera `Strict-Transport-Security: max-age=31536000; includeSubDomains` para forzar a navegadores y clientes a utilizar exclusivamente HTTPS. Solo se envía en respuestas HTTPS, como exige el estándar.

#### Backend: cómo se exige HTTPS (SCRUM-100)

En producción el backend corre detrás de un proxy o balanceador que termina TLS (`https://` y `wss://`) y le reenvía las solicitudes por la red interna.

| Variable / propiedad | Valor en producción | Efecto |
|---|---|---|
| `EXIGIR_HTTPS` (`appresso.seguridad.exigir-https`) | `true` | `FiltroExigirHttps` rechaza con **`426 Upgrade Required`** y `{"mensaje": "Esta API solo acepta conexiones cifradas (HTTPS)."}` toda solicitud sin cifrar, incluido el WebSocket. Va **antes** de leer el token. No redirige: una redirección llegaría tarde, porque el token o la contraseña ya habrían viajado en claro. |
| `server.forward-headers-strategy` | `native` (ya configurado) | Tomcat toma `X-Forwarded-Proto: https` del proxy para saber que la solicitud original fue cifrada. Solo confía en proxies de la red interna, así que un cliente externo no puede falsificarlo. |

Excepción: `/actuator/health` responde por HTTP para que el balanceador revise la salud del servicio; no expone datos. En desarrollo local `EXIGIR_HTTPS` queda en `false` y el backend responde por `http://localhost:8080`. Pruebas: `ExigirHttpsTest` y `SinExigirHttpsTest`.

**Probar HTTPS real en local (opcional).** Desde `backend/`, genera un certificado de prueba (no se sube al repositorio, `certs/` está en `.gitignore`):

```
mkdir certs
keytool -genkeypair -alias appresso -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore certs/appresso-local.p12 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" -storepass una_clave_local
```

En `backend/.env`: `spring.profiles.active=dev,https` y `HTTPS_KEYSTORE_CLAVE=una_clave_local`. El backend arranca en `https://localhost:8443` con HTTPS exigido. Prueba con `curl -k https://localhost:8443/api/health` (`-k` porque el certificado es autofirmado) y comprueba que `http://` no responde. La app Android no confía en certificados autofirmados: para la app sigue usando el backend local por HTTP en compilaciones debug.

### 1.3. Almacenamiento seguro de tokens en Android Keystore
- **Qué se guarda:** la sesión (token JWT, rol y nombre) con `AlmacenSesionKeystore`, siempre cifrada. En `SharedPreferences` privadas solo queda el texto cifrado.
- **Clave:** AES-256 creada dentro de Android Keystore (`AndroidKeyStore`). La app la usa pero no puede leer sus bytes ni exportarla; en los dispositivos que lo soportan la custodia el hardware seguro.
- **Cifrado:** `AES/GCM/NoPadding` (`CifradorSesion`), con un vector de inicialización de 12 bytes nuevo en cada cifrado y una etiqueta de 128 bits: un dato alterado no se descifra.
- **Si algo falla:** si no se puede cifrar, la sesión queda solo en memoria; si lo guardado no se puede descifrar, se borra y hay que iniciar sesión. Nunca se guarda sin cifrar.
- **Cierre de sesión:** al cerrar la sesión o ante un `401`, el dato cifrado se elimina.
- **Copias de seguridad:** `allowBackup="false"`, y `data_extraction_rules.xml` excluye los datos de la app de la nube y de la transferencia entre teléfonos (Android 12+).
- **Pruebas:** `CifradorSesionTest` y `AlmacenSesionKeystoreTest` (JVM) y `AlmacenSesionKeystoreInstrumentedTest` (Keystore real, en un dispositivo).

---

## 2. Mecanismos de la Ley 1581 de 2012 (Habeas Data)

### 2.1. Texto de autorización en el registro de clientes
Quien crea su cuenta desde la app (RF-50) ve este texto en el formulario, antes de enviarlo (`cliente_autorizacion_texto` en `strings.xml`). Después lo puede volver a leer en **Mis datos**:

> **Autorización de Datos Personales (Ley 1581 de 2012)**
>
> De conformidad con la Ley Estatutaria 1581 de 2012 y el Decreto Reglamentario 1377 de 2013 de Colombia, autorizo de manera voluntaria, previa, explícita e informada a Cafetería CaffeBar (Appresso), como responsable del tratamiento, para recolectar, almacenar, procesar y utilizar mis datos personales (nombre, apellidos, documento de identidad, número de celular y correo electrónico) con la finalidad de gestionar mi registro en el programa de fidelización, registrar la acumulación y canje de puntos por compras, permitirme recuperar el acceso a mi cuenta y remitir notificaciones sobre promociones y recompensas vigentes.
>
> Como titular de los datos, fui informado de que tengo derecho a: conocer, actualizar y rectificar mis datos personales; solicitar prueba de esta autorización; ser informado sobre el uso que se ha dado a mis datos; presentar quejas ante la Superintendencia de Industria y Comercio por infracciones a la ley; y revocar la autorización y solicitar la supresión de mis datos en cualquier momento. Puedo ejercer estos derechos mediante los mecanismos dispuestos en la aplicación o a través del canal oficial de atención (privacidad@appresso.com). El plazo máximo de respuesta a mis reclamos, incluidos los de supresión, es de 15 días hábiles conforme al artículo 15 de la Ley 1581.
>
> No se recolectan datos sensibles. Mi autorización es voluntaria, pero sin ella no es posible registrarme como cliente.

### 2.2. Exigencia de aceptación explícita
- **Casilla obligatoria:** el formulario tiene una casilla desmarcada por defecto: *"Acepto explícitamente el tratamiento de mis datos personales conforme a la Ley 1581 de 2012."*
- **Validación en la app:** sin marcarla no se envía nada; se muestra *"Completa todos los datos y acepta el tratamiento de datos personales."* y el error junto a la casilla.
- **Validación en el backend:** `POST /api/auth/registro` exige `aceptoTratamientoDatos: true`; si falta o es `false`, responde `400` con el mismo mensaje y no guarda nada.

### 2.3. Trazabilidad del consentimiento (SCRUM-101)
Para garantizar el principio de responsabilidad demostrada (*Accountability*):
1. **Fecha en el cliente:** cada registro guarda `fecha_aceptacion_datos` (`Instant` en UTC) y `acepto_tratamiento_datos`.
2. **Registro inmutable en auditoría** (`registros_auditoria`, no se edita ni se borra):
   - `tipoOperacion`: `CONSENTIMIENTO_DATOS_1581`
   - `entidad` / `entidadId`: `Cliente` y el id del cliente.
   - `usuario`: la cuenta del titular, que se registró desde la app.
   - `fecha`: momento exacto del consentimiento.
   - `detalle`: constancia de la autorización y **versión del texto aceptado** (`ClienteService.VERSION_TEXTO_AUTORIZACION`, hoy `1581-v1-2026-11`). Si el texto de `strings.xml` (`cliente_autorizacion_texto`) cambia, se sube esa versión.
3. **Sin datos personales en la auditoría:** el detalle identifica al cliente solo por su id, nunca por nombre, documento, celular o correo. Como la auditoría es inmutable, si los guardara sobrevivirían a una supresión.
4. **Consulta de la evidencia:** `GET /api/clientes/{id}/consentimiento` (solo administrador) devuelve el historial del cliente: cuándo se otorgó el consentimiento, desde qué cuenta y, si aplica, cuándo se suprimieron sus datos. Funciona aunque el cliente ya haya sido eliminado.

---

## 3. Eliminación de los datos personales

Conforme al **artículo 15 de la Ley 1581 de 2012**, el cliente elimina sus datos desde la app (**Mis datos**), con su contraseña y de inmediato. Si tiene compras que la ley obliga a conservar, quedan sin ningún dato que lo identifique. El personal no puede eliminar clientes. El procedimiento completo, los plazos y su verificación están en [`eliminacion-de-datos.md`](eliminacion-de-datos.md).

---

## 4. Evidencia

Pruebas de contraseñas, HTTPS y token: [`pruebas-seguridad.md`](pruebas-seguridad.md).
