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
  - URLs WebSocket en producción: `wss://...` (mapeadas automáticamente por `ClienteHttp.urlWebSocket`).
- **Android Network Security Configuration:** Archivo `android/app/src/main/res/xml/network_security_config.xml` configurado con `<base-config cleartextTrafficPermitted="false">`, impidiendo de forma estricta cualquier tráfico en claro (HTTP sin cifrar) en compilaciones de producción.
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
- **Custodia por hardware:** El token JWT de sesión y la información sensible de la sesión activa se gestionan mediante `AlmacenSesionKeystore`.
- **Clave criptográfica:** Se genera una clave simétrica **AES-256** dentro del contenedor seguro de Android (`AndroidKeyStore`), aislada en el TEE (Trusted Execution Environment) o StrongBox del dispositivo móvil, de modo que la clave privada nunca puede ser extraída por software ni por otras aplicaciones.
- **Modo de cifrado:** `AES/GCM/NoPadding` con vector de inicialización (IV) de 12 bytes aleatorio por operación y tag de autenticación GCM de 128 bits para garantizar confidencialidad e integridad.
- **Almacenamiento persistente:** El paquete cifrado (IV + texto cifrado) se almacena en `SharedPreferences` privadas en modo `Context.MODE_PRIVATE`.
- **Cierre de sesión:** Al pulsar "Cerrar sesión" o ante un código HTTP 401, el token cifrado se elimina inmediatamente del almacenamiento local.

---

## 2. Mecanismos de la Ley 1581 de 2012 (Habeas Data)

### 2.1. Texto de autorización en el registro de clientes
Todo cliente registrado en el programa de fidelización (módulo 6, RF-50) visualiza obligatoriamente el siguiente texto legal antes de proporcionar sus datos:

> **Autorización para el Tratamiento de Datos Personales (Ley 1581 de 2012):**  
> De conformidad con la Ley Estatutaria 1581 de 2012 y el Decreto Reglamentario 1377 de 2013 de la República de Colombia, autorizo de manera voluntaria, previa, explícita e informada a **Cafetería CaffeBar (Appresso)**, en calidad de Responsable del Tratamiento, para recolectar, almacenar, procesar y utilizar mis datos personales (nombre, apellidos, documento de identidad y número de celular) con la finalidad de:  
> 1. Gestionar mi registro y participación en el programa de fidelización de clientes.  
> 2. Registrar y consultar la acumulación y canje de puntos por compras en el establecimiento.  
> 3. Remitir notificaciones sobre promociones, beneficios y recompensas vigentes.  
>  
> Como titular de los datos, manifiesto que fui informado sobre mis derechos legales a: conocer, actualizar, rectificar y **solicitar la supresión de mis datos personales** en cualquier momento mediante los mecanismos dispuestos en la aplicación o a través del canal de atención (**privacidad@appresso.com**).

### 2.2. Exigencia de aceptación explícita
- **Casilla de verificación obligatoria:** El formulario de registro contiene un checkbox desmarcado por defecto: *"Acepto explícitamente el tratamiento de mis datos personales conforme a la Ley 1581 de 2012"*.
- **Validación en cliente:** El botón "Registrar" permanece deshabilitado hasta que el usuario marque activamente la casilla de consentimiento. Intentar registrar sin aceptación genera un mensaje de error y no realiza ninguna petición de red.
- **Validación en backend:** El servicio `ClienteService` valida estrictamente `aceptoTratamientoDatos == true`. Si el valor es nulo o falso, responde `400 Bad Request` con mensaje *"El registro de clientes exige la aceptación explícita de la política de tratamiento de datos personales conforme a la Ley 1581 de 2012"*.

### 2.3. Trazabilidad del consentimiento (SCRUM-101)
Para garantizar el principio de responsabilidad demostrada (*Accountability*):
1. **Fecha en el cliente:** cada registro guarda `fecha_aceptacion_datos` (`Instant` en UTC) y `acepto_tratamiento_datos`.
2. **Registro inmutable en auditoría** (`registros_auditoria`, no se edita ni se borra):
   - `tipoOperacion`: `CONSENTIMIENTO_DATOS_1581`
   - `entidad` / `entidadId`: `Cliente` y el id del cliente.
   - `usuario`: el mesero o administrador que hizo el registro.
   - `fecha`: momento exacto del consentimiento.
   - `detalle`: constancia de la autorización y **versión del texto aceptado** (`ClienteService.VERSION_TEXTO_AUTORIZACION`, hoy `1581-v1-2026-11`). Si el texto de `strings.xml` (`cliente_autorizacion_texto`) cambia, se sube esa versión.
3. **Sin datos personales en la auditoría:** el detalle identifica al cliente solo por su id, nunca por nombre, documento o celular. Como la auditoría es inmutable, si los guardara sobrevivirían a una supresión.
4. **Consulta de la evidencia:** `GET /api/clientes/{id}/consentimiento` (solo administrador) devuelve el historial del cliente: cuándo se otorgó el consentimiento, quién lo registró y, si aplica, cuándo se suprimieron sus datos. Funciona aunque el cliente ya haya sido eliminado.

---

## 3. Mecanismo documentado para solicitar la eliminación de datos personales

Conforme al **Artículo 15 de la Ley 1581 de 2012**, los titulares o sus causahabientes que consideren que la información contenida en el sistema debe ser objeto de corrección, actualización o supresión podrán presentar una solicitud ante el Responsable del Tratamiento.

### 3.1. Canales habilitados para el ejercicio del derecho de supresión
1. **Canal en la aplicación móvil (Autogestión / Operativa):**
   - Desde la sección de Clientes en la app, seleccionando la ficha del cliente y presionando el botón **"Solicitar eliminación de datos"**.
   - Se despliega un diálogo explicativo donde se confirma la voluntad de suprimir los datos personales y se puede registrar un motivo opcional.
   - La solicitud invoca el endpoint `POST /api/clientes/{id}/solicitar-eliminacion` o `DELETE /api/clientes/{id}`.
2. **Canal electrónico (Vía correo electrónico):**
   - Envío de solicitud al correo: **`privacidad@appresso.com`**
   - Asunto: *"Solicitud de Supresión de Datos Personales - Ley 1581"*.
   - Contenido requerido: Nombre completo, número de documento de identidad y solicitud puntual.
3. **Canal presencial:**
   - Manifestación verbal o escrita en el punto de atención de la cafetería **CaffeBar**, atendida por el administrador en turno.

### 3.2. Procedimiento técnico y ciclo de vida de la supresión
Quién la ejecuta: `POST /api/clientes/{id}/solicitar-eliminacion` (administrador o mesero, con un `motivo` opcional de hasta 500 caracteres) o `DELETE /api/clientes/{id}` (solo administrador). El motivo no debe incluir datos personales: queda en la auditoría. Al procesar la solicitud (`ClienteService.suprimirDatosPersonales`, en una sola transacción):

1. **Notificaciones:** se borran siempre (son solo mensajes para el cliente).
2. **Cuenta de usuario del cliente** (si tiene una para entrar a la app): se desactiva y se anonimiza (`nombre` → `[DATOS ELIMINADOS]`, `correo` → `eliminado-<id>@appresso.invalid`).
3. **Se revisa el historial comercial antes de borrar** (comandas y canjes de recompensas):
   - **Sin historial:** eliminación física del cliente.
   - **Con historial** (se conserva por exigencia del Código de Comercio y normas tributarias): **anonimización irreversible**:
     - `nombre` → `[DATOS ELIMINADOS]`
     - `apellido` → `[LEY 1581]`
     - `documento` → `ELIM-<id>` (el documento real desaparece; el valor sigue siendo único)
     - `celular` → `[ELIMINADO]`
     - `aceptoTratamientoDatos` → `false` (revocación de la autorización)
4. **Registro de la supresión** en auditoría: `tipoOperacion` `SUPRESION_DATOS_LEY_1581`, id del cliente, quién la atendió, fecha, si los datos se eliminaron o se anonimizaron y el motivo.

Responde `204` si se ejecutó y `404` si el cliente no existe. Pruebas: `ClienteSupresionTest`.

### 3.3. Tiempos máximos legales de respuesta
- **Término ordinario:** Quince (**15**) días hábiles contados a partir del día siguiente a la recepción de la solicitud (Art. 15, Ley 1581).
- **Prórroga extraordinaria:** Cuando no fuere posible atender la solicitud en dicho término, se informará al interesado antes del vencimiento expresando los motivos de demora y la fecha en que se atenderá, la cual no superará los ocho (**8**) días hábiles siguientes al vencimiento del primer término.
- En la aplicación móvil, cuando el operador o administrador ejecuta la solicitud, la supresión y anonimización técnica se aplica de forma **inmediata y en tiempo real**.
