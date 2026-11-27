# Appresso — App Android

App móvil de Appresso, hecha con **Kotlin** y **Jetpack Compose**. Paquete `com.appresso.app`, Android 8.0 (API 26) o superior.

## 1. Qué necesitas

| Herramienta | Para qué |
|---|---|
| **Android Studio** (reciente) | Abrir, compilar y ejecutar la app |
| **JDK 21** | Lo exige el proyecto (`gradle/gradle-daemon-jvm.properties`); Android Studio ya trae uno compatible |
| **Android SDK 36.1** | Plataforma que usa la compilación (`compileSdk` 36, revisión menor 1) |

Abre en Android Studio la carpeta **`android/`** (no la raíz del repositorio). Gradle viene incluido (`gradlew`).

## 2. Compilar y probar desde la terminal

Desde `android/`:

```bash
# Git Bash, Linux o macOS
./gradlew assembleDebug testDebugUnitTest lintDebug

# Windows (PowerShell)
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

El pipeline **CI Android** (`.github/workflows/ci-android.yml`, check `build-android`) ejecuta esas tareas. Para pruebas instrumentadas con un teléfono por USB, usa `.\gradlew.bat connectedDebugAndroidTest` desde PowerShell en `android/`.

## 3. A qué backend se conecta

La URL del backend se fija al compilar (`BuildConfig.API_BASE_URL`):

| Compilación | URL |
|---|---|
| **debug** | La que configures (ver abajo). Por defecto `http://10.0.2.2:8080/`, el alias que tiene el **emulador** para el `localhost` de tu computador. |
| **release** | `https://appresso.invalid/` hasta configurar el dominio de producción en 8.5.1. |

La variante **debug** permite el HTTP del backend local mediante `app/src/debug/res/xml/network_security_config.xml`. La configuración de **release** exige HTTPS. Con una URL base HTTPS, la app usa WSS para STOMP.

### Emulador

No hay que configurar nada: levanta el backend (ver `backend/README.md`) y ejecuta la app.

### Teléfono físico

1. Conecta el teléfono y el computador a la **misma red Wi-Fi**, y activa la depuración USB en el teléfono.
2. Averigua la IP de tu computador en esa red: `ipconfig` y busca la "Dirección IPv4" del adaptador **Wi-Fi** (ignora los adaptadores `vEthernet`/WSL).
3. Agrega esta línea a **`android/local.properties`** (archivo personal, no se sube al repo), con tu IP:
   ```
   appresso.api.baseUrl=http://192.168.1.50:8080
   ```
   La barra final es opcional. Vuelve a compilar y a instalar la variante `debug`. También puedes pasar la URL al compilar con `.\gradlew.bat assembleDebug "-Pappresso.api.baseUrl=http://192.168.1.50:8080/"` desde PowerShell.
4. Levanta el backend. La primera vez, Windows pregunta si permite que Java reciba conexiones: acepta para **redes privadas**. Si no preguntó o dijiste que no, habilita el puerto 8080 de entrada en el Firewall de Windows.
5. Comprueba desde el **navegador del teléfono** que `http://<tu IP>:8080/api/health` responde `{"status":"ok"}`. Si no responde, el problema es la red o el firewall, no la app.

6. Prueba de punta a punta: con el backend en marcha y el administrador de prueba creado (`backend/README.md`, paso 1), ejecuta la app e inicia sesión con ese correo y contraseña. El cliente de prueba entra con el documento `1000000001`. Debe aparecer "Panel del administrador" con tu nombre. Con una contraseña equivocada debe decir "Credenciales inválidas", y con el backend apagado, "Sin conexión a Internet. Por favor, verifica tu red.".

Si la red Wi-Fi aísla los dispositivos entre sí (pasa en redes de universidades o públicas), el paso 5 no funcionará. Alternativas: crear un punto de acceso desde otro teléfono, o usar el cable USB con `adb reverse tcp:8080 tcp:8080` y la URL `http://localhost:8080`.

Si `/api/health` responde en el navegador y la app sigue mostrando «Sin conexión», comprueba la IP Wi-Fi actual, la variante instalada y la URL del APK. La URL queda fijada al compilar. Usa backend y APK de la misma revisión para comprobar también los recibos de suscripción de 3.3.1.

### Entorno interno de Render

Cuando el equipo tenga el servicio interno, configura `appresso.api.baseUrl=https://<servicio-interno>.onrender.com/` en `android/local.properties`, o pásala con `-Pappresso.api.baseUrl=...` al compilar. Reinstala la variante `debug` y verifica primero el `/api/health` de ese servicio por HTTPS. La app solo se comunica con el backend.

## 4. Qué hay en el código

```
app/src/main/java/com/appresso/app/
├── MainActivity.kt        # punto de entrada
├── AppressoApp.kt         # Application: crea el contenedor al arrancar
├── ContenedorApp.kt       # reúne los objetos compartidos (almacén de sesión, acceso al backend)
├── data/
│   ├── auth/              # Rol, Sesion, ResultadoLogin, AuthRepository (+ remoto) y AlmacenSesion
│   ├── cuenta/            # registro, recuperación de contraseña y eliminación de los datos del cliente, y ReglasCuenta
│   ├── clientes/          # búsqueda de clientes por documento (ClientesRepository + remoto)
│   ├── catalogo/          # productos, categorías y control de existencias
│   ├── menu/              # consulta del menú de solo lectura
│   ├── cocina/            # consulta de comandas activas
│   └── remote/            # AuthApi, CuentaApi, ClientesApi (Retrofit), los DTO, ClienteHttp (OkHttp, tiempos de espera) e InterceptorSesion (token y 401)
├── permisos/              # Permisos.kt: qué acciones y opciones tiene cada rol (con pruebas)
├── tiemporeal/            # conexión STOMP, reintentos, eventos y medición
└── ui/
    ├── AppressoRaiz.kt    # elige la pantalla según la sesión y la sección abierta
    ├── Destino.kt         # destinoPara(sesion): la regla de a dónde ir (con pruebas)
    ├── PantallaProvisional.kt   # marco de las secciones provisionales
    ├── theme/             # colores, tipografías y tema (guía de estilo de Figma)
    ├── login/             # LoginScreen (dibuja), LoginRoute (la conecta), LoginViewModel (decide) y CampoTexto
    ├── cuenta/            # pantallas del cliente: registro, recuperación de contraseña y «Mis datos»
    ├── clientes/          # búsqueda de clientes por documento (mesero y administrador)
    ├── catalogo/          # gestión de productos, ajustes e historial de existencias
    ├── menu/              # productos disponibles, agrupación, precio y filtro por categoría
    ├── cocina/            # comandas activas, estado y observaciones
    └── inicio/            # InicioScreen (menú de cada rol) y SeccionProvisionalScreen
```

**Después de iniciar sesión (SCRUM-48).** Cada rol llega a su inicio. `opcionesPara(rol)` y `puede(rol, Accion.X)` en `Permisos.kt` determinan qué opciones y acciones muestra Android. `AppressoRaiz` conecta las pantallas disponibles y muestra una vista provisional para las que faltan. La sesión se conserva cifrada en Android Keystore. [Permisos por rol](../docs/permisos-por-rol.md)

El contrato del inicio de sesión que consume la app está en [`docs/api-auth.md`](../docs/api-auth.md). `AuthRepositoryRemoto` traduce cada respuesta (200, 400, 401, 423, sin conexión, error del servidor) a un `ResultadoLogin`, y la pantalla solo conoce la interfaz `AuthRepository`.

**Cómo se reparte el trabajo en el login**
- `LoginScreen` solo dibuja: recibe lo escrito y el error y avisa de lo que hace el usuario.
- `LoginViewModel` es el dueño del estado: valida lo escrito con las mismas reglas del servidor (si trae `@`, correo con formato y máx. 150 caracteres; si no, número de documento de 5 a 20 letras o números; contraseña de máx. 72), evita enviar dos veces seguidas, llama al repositorio y guarda la sesión. Si falla (por ejemplo sin conexión), **conserva lo escrito** para reintentar; si funciona, descarta la contraseña. La contraseña vive solo en el ViewModel, no en el estado guardado de Android.
- `LoginRoute` traduce cada error a un texto de `strings.xml`. Los mensajes del servidor en 400 y 423 se muestran tal cual.
- **Sesión:** `AlmacenSesion` es una interfaz; la app usa `AlmacenSesionKeystore`, que guarda la sesión cifrada (AES-256/GCM) con una clave de Android Keystore, así que **sigue abierta al cerrar la app** hasta que se cierre o venza el token. El cifrado está en `CifradorSesion`. Si el cifrado falla, la sesión queda solo en memoria. El manifest excluye los datos de la app de las copias de seguridad. Pruebas: `CifradorSesionTest` y `AlmacenSesionKeystoreTest` (en la JVM, con una clave por software) y `AlmacenSesionKeystoreInstrumentedTest` (con el Keystore real; necesita un teléfono o emulador: `./gradlew connectedDebugAndroidTest`). Más en [`docs/seguridad-y-ley-1581.md`](../docs/seguridad-y-ley-1581.md).

**Cuenta del cliente (7.1.1).** El personal entra con su correo y el cliente con su número de documento, en el mismo campo. Desde el login, el cliente abre «Crear cuenta» (`RegistroClienteRoute`: nombre, apellido, documento, celular, correo, contraseña y la autorización de la Ley 1581) y «¿Olvidaste tu contraseña?» (`RecuperarContrasenaRoute`: pide un código de 6 dígitos al correo y cambia la contraseña). Al terminar, vuelve al login con el documento ya escrito. `AppressoRaiz` elige entre las tres pantallas cuando no hay sesión. Contratos en [`docs/api-clientes.md`](../docs/api-clientes.md) y [`docs/api-auth.md`](../docs/api-auth.md).

**Clientes (mesero y administrador).** La sección «Clientes» solo busca por documento (`GET /api/clientes/{documento}`) y muestra nombre y puntos, o «Cliente no encontrado» con la indicación de registrarse desde la app. El personal no registra clientes.

**Mis datos (cliente).** `MisDatosRoute` muestra la autorización que dio el cliente y le permite eliminar sus datos (Ley 1581): escribe su contraseña, confirma en un diálogo y, si el servidor acepta (`POST /api/clientes/yo/eliminacion`), la sesión se cierra.

**Mesas y comandas (4.1.1).** El administrador crea mesas y el mapa permite a administradores y meseros armar una comanda en una mesa disponible o abrir el detalle de la comanda activa en una mesa ocupada. Cada producto admite una cantidad y observaciones opcionales de hasta 100 caracteres.

El resumen previo muestra los subtotales y el total. Para guardar, el servidor recalcula el pedido y compara el importe con el total revisado en Android. Si cambió, la app actualiza el resumen y solicita una nueva confirmación. Si coincide, el pedido queda `PENDIENTE` y la app muestra los importes guardados. El envío a cocina corresponde a 4.2.1. [Contrato de comandas y mesas](../docs/api-comandas.md)

**Existencias, menú y cocina.** El administrador ajusta stock, consulta el historial y ve alertas de stock bajo. El menú se actualiza con eventos de catálogo. Cocina muestra las comandas activas por orden de envío y refresca con eventos de comandas. Las pantallas releen por REST tras recibir la confirmación de suscripción y suspenden sus reintentos automáticos al salir o pasar a segundo plano. [Contrato de inventario](../docs/api-inventario.md) · [Contrato de menú y cocina](../docs/api-menu-cocina.md)

### Pruebas en teléfono y evidencia

Registra fecha, commit del backend y del APK, modelo, versión Android, red, resultado y evidencia. Usa cuentas y datos de prueba. Para repetir una prueba de conectividad entre teléfonos, usa el mismo backend y anota cuántos dispositivos estaban conectados.

| Entregable | Evidencia pendiente en dispositivo |
|---|---|
| **2.3.1 · Permisos** | Completar las partes B y C con los cinco roles, navegación, cierre de sesión, error de conexión y recuperación. [Verificación por rol](../docs/verificacion-roles.md#parte-b--app-cada-rol-ve-solo-lo-suyo) |
| **8.2.1 · Tiempo real** | Completar latencia de eventos (p95 y porcentaje bajo 3 s), caída, reconexión y segundo plano. Usar «Appresso Diagnóstico» con un administrador o mesero de prueba, autorizado para ambos tópicos. [Mediciones de tiempo real](../docs/tiempo-real.md#mediciones-en-dispositivo-real-scrum-31-y-scrum-32) |
| **3.3.1 · Menú y cocina** | Prueba funcional con el backend y el APK actuales. Con dos cuentas, ajusta existencias como administrador y verifica que el mesero vea desaparecer y reaparecer un producto al pasar por cero. Revisa conexión, reconexión y recarga en Cocina. La tabla de tiempos de Cocina se llena cuando se retome la medición del SCRUM-93. [Medición de cocina](../docs/medicion-cocina.md#resultados) |
| **8.4.1 · Keystore** | Completar los seis casos de sesión cifrada, incluida la prueba instrumentada, reapertura, cierre, cuenta desactivada y revisión de logs. [Pruebas de seguridad, parte D](../docs/pruebas-seguridad.md#parte-d--token-en-el-teléfono-android-keystore) |
| **8.4.1 · Supresión** | Completar los nueve casos de «Mis datos» y supresión con cuentas de prueba desechables. [Eliminación de datos, sección 6](../docs/eliminacion-de-datos.md#6-verificación) |
| **8.5.1 · Producción** | Completar la verificación de TLS y del APK firmado con el dominio real. [Pruebas en el servidor](../docs/pruebas-seguridad.md#por-repetir-en-el-servidor-851) |

Las pruebas unitarias (`app/src/test`) corren en la JVM, sin emulador ni backend: el repositorio se prueba contra un servidor HTTP simulado (MockWebServer) que responde lo que respondería el real.

**Token y sesión vencida (SCRUM-46).** `ContenedorApp` crea un solo `Retrofit` para toda la app; las APIs nuevas se crean con `contenedor.retrofit.create(MiApi::class.java)`. Su cliente lleva `InterceptorSesion`, que agrega `Authorization: Bearer <token>` a cada petición (menos al login) y, si el backend responde **401**, borra la sesión: la app vuelve sola al login. Un **403** no cierra la sesión; la pantalla debe mostrar el `mensaje` del servidor. Ver [`docs/permisos-por-rol.md`](../docs/permisos-por-rol.md).

## 5. Librerías de red

Retrofit (llamadas HTTP), OkHttp, `kotlinx.serialization` (JSON) y coroutines. Las versiones están en `gradle/libs.versions.toml`. **OkHttp se mantiene en 4.12.0 a propósito**: es la versión con la que se prueba Retrofit 3.0.0; no la subas a 5.x por separado.

## 6. Problemas comunes

**En el navegador del teléfono `http://<IP>:8080/api/health` no responde, pero en el PC sí.** Casi siempre es el firewall de Windows: no hay ninguna regla que deje entrar conexiones al puerto 8080 (las del propio PC no pasan por el firewall, por eso allí sí funciona). Abre PowerShell **como administrador** y ejecuta:

```powershell
New-NetFirewallRule -DisplayName "Appresso backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

Solo aplica a redes **Privadas** (la de tu casa). Para quitarla: `Remove-NetFirewallRule -DisplayName "Appresso backend 8080"`.

**Dejó de conectar de un día para otro.** Comprueba con `ipconfig` la IP actual de Wi-Fi, actualiza `appresso.api.baseUrl` y vuelve a compilar e instalar `debug`. Si ya la actualizaste, revisa que un argumento `-Pappresso.api.baseUrl` de una compilación anterior o el APK que estás instalando usen la IP vigente.

**Android Studio dice "el dispositivo no tiene suficiente poder de cómputo... encoding display".** Es el espejo de pantalla (*Running Devices*), no la app. En teléfonos modestos puede fallar y además estorbar la instalación. Desactívalo en *File → Settings → Tools → Running Devices* (la opción de activar el espejo al conectar un dispositivo físico) o cierra esa pestaña.

**Run no instala la app.** Instala el APK a mano con `adb` (con el teléfono conectado y la depuración USB activa):

```bash
.\gradlew.bat assembleDebug
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Después abre **Appresso** en el teléfono. Si el teléfono muestra un aviso de Play Protect, elige instalar de todos modos: es una app de pruebas sin firmar para la tienda.

**El administrador de prueba quedó bloqueado** (5 intentos fallidos seguidos, 15 minutos):

```bash
docker exec appresso-db psql -U appresso -d appresso -c "update usuarios set intentos_fallidos=0, bloqueado_hasta=null where correo='admin@appresso.test'"
```
