# Appresso — App Android

App móvil de Appresso, hecha con **Kotlin** y **Jetpack Compose**. Paquete `com.appresso.app`, Android 8.0 (API 26) o superior.

## 1. Qué necesitas

| Herramienta | Para qué |
|---|---|
| **Android Studio** (reciente) | Abrir, compilar y ejecutar la app |
| **JDK 21** | Lo exige el proyecto (`gradle/gradle-daemon-jvm.properties`); Android Studio ya trae uno compatible |
| **Android SDK 36** | Se instala desde el SDK Manager de Android Studio |

Abre en Android Studio la carpeta **`android/`** (no la raíz del repositorio). Gradle viene incluido (`gradlew`).

## 2. Compilar y probar desde la terminal

Desde `android/`:

```bash
# Git Bash, Linux o macOS
./gradlew assembleDebug testDebugUnitTest lintDebug

# Windows (cmd o PowerShell)
gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Es lo mismo que ejecuta el pipeline **CI android** (`.github/workflows/ci-android.yml`, check `build-android`) en cada Pull Request. Si pasa en tu máquina, casi seguro pasa en GitHub. La primera vez tarda varios minutos mientras Gradle descarga todo.

## 3. A qué backend se conecta

La URL del backend se fija al compilar (`BuildConfig.API_BASE_URL`):

| Compilación | URL |
|---|---|
| **debug** | La que configures (ver abajo). Por defecto `http://10.0.2.2:8080/`, el alias que tiene el **emulador** para el `localhost` de tu computador. |
| **release** | `https://appresso.invalid/`, un marcador a propósito: todavía no hay servidor desplegado (entregable 8.5.1). |

Solo la compilación **debug** permite HTTP sin cifrar (`app/src/debug/AndroidManifest.xml`), porque el backend local no usa HTTPS. El release nunca lo permite.

### Emulador

No hay que configurar nada: levanta el backend (ver `backend/README.md`) y ejecuta la app.

### Teléfono físico

1. Conecta el teléfono y el computador a la **misma red Wi-Fi**, y activa la depuración USB en el teléfono.
2. Averigua la IP de tu computador en esa red: `ipconfig` y busca la "Dirección IPv4" del adaptador **Wi-Fi** (ignora los adaptadores `vEthernet`/WSL).
3. Agrega esta línea a **`android/local.properties`** (archivo personal, no se sube al repo), con tu IP:
   ```
   appresso.api.baseUrl=http://192.168.1.50:8080
   ```
   La barra final es opcional. Después sincroniza Gradle en Android Studio. También puedes pasarla sin tocar el archivo: `gradlew.bat assembleDebug -Pappresso.api.baseUrl=http://192.168.1.50:8080`.
4. Levanta el backend. La primera vez, Windows pregunta si permite que Java reciba conexiones: acepta para **redes privadas**. Si no preguntó o dijiste que no, habilita el puerto 8080 de entrada en el Firewall de Windows.
5. Comprueba desde el **navegador del teléfono** que `http://<tu IP>:8080/api/health` responde `{"status":"ok"}`. Si no responde, el problema es la red o el firewall, no la app.

6. Prueba de punta a punta: con el backend en marcha y el administrador de prueba creado (`backend/README.md`, paso 1), ejecuta la app e inicia sesión con ese correo y contraseña. Debe aparecer "Panel del administrador" con tu nombre. Con una contraseña equivocada debe decir "Credenciales inválidas", y con el backend apagado, "Sin conexión a Internet. Por favor, verifica tu red.".

Si la red Wi-Fi aísla los dispositivos entre sí (pasa en redes de universidades o públicas), el paso 5 no funcionará. Alternativas: crear un punto de acceso desde otro teléfono, o usar el cable USB con `adb reverse tcp:8080 tcp:8080` y la URL `http://localhost:8080`.

La IP del computador puede cambiar al reconectarte a la red: si la app deja de conectar, revísala primero.

## 4. Qué hay en el código

```
app/src/main/java/com/appresso/app/
├── MainActivity.kt        # punto de entrada
├── AppressoApp.kt         # Application: crea el contenedor al arrancar
├── ContenedorApp.kt       # reúne los objetos compartidos (almacén de sesión, acceso al backend)
├── data/
│   ├── auth/              # Rol, Sesion, ResultadoLogin, AuthRepository (+ remoto) y AlmacenSesion
│   └── remote/            # AuthApi (Retrofit), los DTO, ClienteHttp (OkHttp, tiempos de espera) e InterceptorSesion (token y 401)
├── permisos/              # Permisos.kt: qué acciones y opciones tiene cada rol (con pruebas)
└── ui/
    ├── AppressoRaiz.kt    # elige la pantalla según la sesión y la sección abierta
    ├── Destino.kt         # destinoPara(sesion): la regla de a dónde ir (con pruebas)
    ├── PantallaProvisional.kt   # marco de las secciones provisionales
    ├── theme/             # colores, tipografías y tema (guía de estilo de Figma)
    ├── login/             # LoginScreen (dibuja), LoginRoute (la conecta) y LoginViewModel (decide)
    └── inicio/            # InicioScreen (menú de cada rol) y SeccionProvisionalScreen
```

**Después de iniciar sesión (SCRUM-48).** Cada rol llega a su **inicio**, con solo las opciones que le corresponden y un botón para cerrar sesión. Qué ve y qué puede hacer cada rol está en `permisos/Permisos.kt`: `opcionesPara(rol)` arma el menú y `puede(rol, Accion.X)` sirve para mostrar u ocultar botones dentro de una pantalla. Refleja la política del backend ([`docs/permisos-por-rol.md`](../docs/permisos-por-rol.md)); si cambia una regla allá, se cambia aquí. Las secciones aún son provisionales: cada módulo conecta su pantalla real en `AppressoRaiz` (función `Seccion`), por ejemplo `Opcion.USUARIOS -> UsuariosRoute(...)`. La sesión sigue solo en memoria: cerrar la app del todo vuelve al login.

El contrato del inicio de sesión que consume la app está en [`docs/api-auth.md`](../docs/api-auth.md). `AuthRepositoryRemoto` traduce cada respuesta (200, 400, 401, 423, sin conexión, error del servidor) a un `ResultadoLogin`, y la pantalla solo conoce la interfaz `AuthRepository`.

**Cómo se reparte el trabajo en el login**
- `LoginScreen` solo dibuja: recibe lo escrito y el error y avisa de lo que hace el usuario.
- `LoginViewModel` es el dueño del estado: valida lo escrito con las mismas reglas del servidor (correo con formato y máx. 150 caracteres, contraseña de máx. 72), evita enviar dos veces seguidas, llama al repositorio y guarda la sesión. Si falla (por ejemplo sin conexión), **conserva lo escrito** para reintentar; si funciona, descarta la contraseña. La contraseña vive solo en el ViewModel, no en el estado guardado de Android.
- `LoginRoute` traduce cada error a un texto de `strings.xml`. Los mensajes del servidor en 400 y 423 se muestran tal cual.
- **Sesión:** `AlmacenSesion` es una interfaz; hoy se usa `AlmacenSesionEnMemoria`, así que la sesión **se pierde al cerrar la app** y hay que volver a iniciar sesión. Es provisional a propósito: guardar el token en disco sin cifrar sería peor. El almacenamiento cifrado (Android Keystore) llega con 8.4.1 y bastará con otra implementación de la interfaz. Al implementarlo hay que revisar `android:allowBackup` del manifest, que hoy está activo.

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

**Dejó de conectar de un día para otro.** La IP del PC cambió (el router las reparte). Revisa con `ipconfig`, actualiza `appresso.api.baseUrl` en `android/local.properties` y **vuelve a instalar la app**: la URL queda escrita dentro del APK al compilar. No hace falta "sincronizar Gradle" para este cambio; basta con ejecutar de nuevo.

**Android Studio dice "el dispositivo no tiene suficiente poder de cómputo... encoding display".** Es el espejo de pantalla (*Running Devices*), no la app. En teléfonos modestos puede fallar y además estorbar la instalación. Desactívalo en *File → Settings → Tools → Running Devices* (la opción de activar el espejo al conectar un dispositivo físico) o cierra esa pestaña.

**Run no instala la app.** Instala el APK a mano con `adb` (con el teléfono conectado y la depuración USB activa):

```bash
gradlew.bat assembleDebug
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Después abre **Appresso** en el teléfono. Si el teléfono muestra un aviso de Play Protect, elige instalar de todos modos: es una app de pruebas sin firmar para la tienda.

**El administrador de prueba quedó bloqueado** (5 intentos fallidos seguidos, 15 minutos):

```bash
docker exec appresso-db psql -U appresso -d appresso -c "update usuarios set intentos_fallidos=0, bloqueado_hasta=null where correo='admin@appresso.test'"
```
