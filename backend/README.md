# Appresso — Backend

Servidor de aplicaciones de Appresso, hecho con Spring Boot.

## Requisitos

- JDK 21 o superior (`java -version`).
- Conexión a Internet la primera vez, para que Gradle descargue sus dependencias.

No hace falta instalar Gradle: el proyecto incluye el *wrapper* (`gradlew`).

## Ejecutar

Desde esta carpeta (`backend/`):

```bash
# Linux / macOS / Git Bash
./gradlew bootRun

# Windows (PowerShell o cmd)
gradlew.bat bootRun
```

El servidor queda en `http://localhost:8080`. Para detenerlo, `Ctrl + C`.

## Comprobar que responde

```bash
curl http://localhost:8080/api/health
# {"status":"ok"}

curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

## Ejecutar las pruebas

```bash
./gradlew test
```

## Estructura

```
backend/
├── build.gradle                 # dependencias y configuración de compilación
├── settings.gradle
├── gradlew, gradlew.bat         # wrapper de Gradle
└── src/
    ├── main/
    │   ├── java/com/appresso/backend/
    │   │   ├── AppressoBackendApplication.java   # punto de entrada
    │   │   └── health/HealthController.java      # endpoint de prueba
    │   └── resources/application.properties
    └── test/java/com/appresso/backend/           # pruebas
```

Paquete base: `com.appresso.backend`.
