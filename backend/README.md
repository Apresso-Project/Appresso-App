# Appresso — Backend

Servidor de aplicaciones de Appresso, hecho con **Spring Boot** y **PostgreSQL**.

Este README está pensado para quien nunca ha usado Spring. Si algo falla, ve a la sección [Errores comunes](#errores-comunes).

---

## 1. Qué necesitas instalado

| Herramienta | Para qué | Cómo comprobar |
|-------------|----------|----------------|
| **JDK 21 o superior** | Compilar y ejecutar el backend | `java -version` |
| **Docker Desktop** | Tener PostgreSQL sin instalarlo | `docker --version` (y que Docker Desktop esté **abierto**) |
| **Git** | Clonar el repositorio | `git --version` |

No hace falta instalar Gradle ni PostgreSQL. Gradle viene dentro del proyecto (`gradlew`) y PostgreSQL corre dentro de Docker.

---

## 2. Primera vez: de cero a un backend funcionando

Todos los comandos se ejecutan **dentro de la carpeta `backend/`**.

### Paso 1. Crear tu archivo `.env`

El `.env` guarda los datos de conexión a la base de datos. No se sube al repositorio (cada quien tiene el suyo).

```bash
# Git Bash, Linux o macOS
cp .env.example .env

# Windows (PowerShell o cmd)
copy .env.example .env
```

Abre `.env` y cambia `DB_PASSWORD` por la clave que quieras para tu base de datos local.

### Paso 2. Encender la base de datos

Abre Docker Desktop, espera a que diga que está en ejecución y luego:

```bash
docker compose up -d
```

La primera vez descarga la imagen de PostgreSQL y tarda un poco. Comprueba que quedó lista:

```bash
docker compose ps
# El STATUS debe decir "(healthy)"
```

### Paso 3. Encender el backend

```bash
# Git Bash, Linux o macOS
./gradlew bootRun

# Windows (PowerShell o cmd)
gradlew.bat bootRun
```

La primera vez Gradle descarga todo lo que necesita (puede tardar varios minutos). Cuando termine verás una línea como:

```
Started AppressoBackendApplication in 6.7 seconds
```

Para detenerlo: `Ctrl + C`.

### Paso 4. Comprobar que funciona

En otra terminal:

```bash
curl http://localhost:8080/api/health
# {"status":"ok"}

curl http://localhost:8080/actuator/health/db
# {"status":"UP"}
```

- `/api/health` responde si el backend está vivo.
- `/actuator/health/db` responde `UP` solo si el backend **logró conectarse a PostgreSQL**. Si dice `DOWN`, la base de datos no está disponible.

---

## 3. Día a día

| Quiero… | Comando (en `backend/`) |
|---------|-------------------------|
| Encender la base de datos | `docker compose up -d` |
| Apagar la base de datos (los datos se conservan) | `docker compose down` |
| Apagar la base **y borrar todos sus datos** | `docker compose down -v` |
| Ver si la base está lista | `docker compose ps` |
| Ejecutar el backend | `./gradlew bootRun` |
| Ejecutar las pruebas | `./gradlew test` |
| Ver los datos de la base con SQL | `docker exec -it appresso-db psql -U appresso -d appresso` |

Orden normal de trabajo: **primero la base de datos, después el backend**.

---

## 4. Cómo funciona (para entender qué estás ejecutando)

```
Tu computador
├── Docker
│   └── contenedor "appresso-db"  ← PostgreSQL (puerto 5432)
│
└── ./gradlew bootRun
    └── Backend Spring Boot (puerto 8080)
         └── se conecta a PostgreSQL con los datos de .env
```

- **Docker solo ejecuta la base de datos.** El backend NO corre dentro de Docker: lo ejecuta Gradle directamente en tu computador.
- La conexión se configura en `src/main/resources/application.properties`. Ese archivo no tiene contraseñas: lee las variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`.
- Esas variables salen de tu archivo `.env`. El mismo `.env` lo lee también Docker Compose para crear la base con ese usuario y esa clave.

### Glosario rápido

| Palabra | Qué es |
|---------|--------|
| **Spring Boot** | Framework de Java que arranca un servidor web con muy poca configuración. |
| **Gradle / `gradlew`** | Herramienta que descarga dependencias, compila, ejecuta y prueba. `gradlew` es una copia incluida en el proyecto para que nadie instale nada. |
| **Starter** | Paquete de dependencias de Spring que se agrega con una línea en `build.gradle` (por ejemplo, el de JPA). |
| **`application.properties`** | Archivo de configuración de Spring Boot: puerto, base de datos, etc. |
| **Controlador (`@RestController`)** | Clase que responde a una dirección web. `HealthController` responde a `/api/health`. |
| **Actuator** | Módulo que expone el estado del servicio (`/actuator/health`). |
| **JPA / Hibernate** | Capa que traduce objetos de Java a tablas de la base de datos. Las entidades (clases `@Entity`) ya existen; las tablas en PostgreSQL las crea otra subtarea (migraciones). |
| **Entidad (`@Entity`)** | Clase de Java que representa una tabla. Ver el [modelo de datos](../docs/modelo-de-datos.md). |
| **DataSource / Hikari** | La conexión a la base de datos y el "pool" (grupo) de conexiones que mantiene abiertas. Verás 10 conexiones desde la app: es normal. |
| **`ddl-auto=none`** | Le dice a Hibernate que **no cree ni modifique tablas**. |
| **Variable de entorno** | Un valor que vive fuera del código (en tu `.env` o en el servidor). Así el mismo código funciona en cualquier lugar. |
| **Docker Compose** | Herramienta que lee `docker-compose.yml` y enciende los contenedores definidos ahí. |
| **H2** | Base de datos pequeña en memoria, que solo se usa en las pruebas. |

---

## 5. Pruebas

```bash
./gradlew test
```

Las pruebas **no necesitan Docker ni `.env`**. Usan una base H2 en memoria, configurada en `src/test/resources/application.properties`.

Importante:

- En las pruebas Hibernate **sí crea las tablas** en H2 a partir de las entidades (`ddl-auto=create-drop`); así `ModeloDatosTest` comprueba que el modelo está bien mapeado.
- Ese archivo de pruebas **reemplaza por completo** al de `src/main/resources` mientras se ejecutan las pruebas. Si agregas una propiedad nueva al de `main` y las pruebas la necesitan, hay que ponerla también en el de pruebas.
- H2 no se comporta igual que PostgreSQL en todo. Sirve para comprobar que la aplicación arranca y se conecta, pero una consulta que funcione en H2 podría fallar en PostgreSQL.

---

## 6. Producción (cómo se conectará en un servidor)

En un servidor no hay archivo `.env`: las variables las define el proveedor del servidor.

- Se pueden definir `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`, igual que en desarrollo.
- O, si el proveedor entrega una sola dirección de conexión, se puede usar `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Spring las usa en lugar de lo que diga `application.properties`.

El despliegue (servidor, HTTPS, copias de seguridad) es el entregable 8.5.1 y no está resuelto aquí.

---

## 7. Errores comunes

Estos mensajes los provocamos a propósito para comprobar que son reales.

### Error 1. `password authentication failed for user "appresso"`

```
Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed for user "appresso"
```

**Causa más probable: no existe el archivo `.env`, o `DB_PASSWORD` no está en él.** Spring no avisa que falta la variable: manda el texto `${DB_PASSWORD}` como si fuera la contraseña, y PostgreSQL lo rechaza. Por eso el mensaje confunde.

**Qué hacer:**
1. Comprueba que existe `backend/.env` (no solo `.env.example`) y que tiene `DB_PASSWORD`.
2. Comprueba que ejecutas el comando **desde la carpeta `backend/`**: Spring busca el `.env` en la carpeta desde donde se ejecuta.
3. Si cambiaste `DB_PASSWORD` **después** de crear la base, PostgreSQL conserva la clave anterior. Apágala borrando sus datos y vuelve a encenderla: `docker compose down -v` y luego `docker compose up -d`. Ojo: eso borra los datos.

### Error 2. `Connection to localhost:5432 refused`

```
PSQLException: Connection to localhost:5432 refused. Check that the hostname and port are correct
and that the postmaster is accepting TCP/IP connections.
```

Más abajo aparece un mensaje largo que termina en `Unable to determine Dialect without JDBC metadata`. Ese es un efecto del error anterior, no la causa. **Busca siempre el primer `Caused by` y el `refused`.**

**Causa:** la base de datos no está encendida.

**Qué hacer:** abre Docker Desktop, ejecuta `docker compose up -d` y espera a que `docker compose ps` diga `(healthy)`.

### Error 3. `failed to connect to the docker API ...`

```
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

**Causa:** Docker Desktop no está abierto. Ábrelo, espera a que arranque y repite el comando.

### Error 4. `/actuator/health/db` responde `DOWN` (HTTP 503) con el backend ya encendido

La base de datos se apagó o dejó de responder. Enciéndela con `docker compose up -d`. El backend se reconecta solo: no hace falta reiniciarlo (lo comprobamos).

---

## 8. Reglas del equipo

- **Nunca subas el `.env`** al repositorio. Ya está en el `.gitignore`. Si necesitas compartir una variable nueva, agrégala al `.env.example` con un valor de ejemplo.
- **No pongas contraseñas en `application.properties`.**
- **No cambies `ddl-auto`** sin acordarlo con quien hace los modelos de datos: define cómo se crean las tablas.

---

## 9. Estructura

```
backend/
├── build.gradle                  # dependencias y compilación
├── settings.gradle
├── gradlew, gradlew.bat          # Gradle incluido (no hay que instalarlo)
├── docker-compose.yml            # PostgreSQL local
├── .env.example                  # plantilla de variables (se sube al repo)
├── .env                          # TUS variables (NO se sube al repo)
└── src/
    ├── main/
    │   ├── java/com/appresso/backend/
    │   │   ├── AppressoBackendApplication.java   # punto de entrada
    │   │   ├── health/HealthController.java      # /api/health
    │   │   ├── usuarios/                         # Usuario, DispositivoPush, Rol
    │   │   ├── inventario/                       # Categoria, Producto, MovimientoInventario
    │   │   ├── comandas/                         # Mesa, Comanda, DetalleComanda, HistorialComanda
    │   │   ├── caja/                             # Jornada, Pago, MovimientoCaja, SolicitudRetiro, ...
    │   │   ├── fidelizacion/                     # Cliente, Recompensa, Promocion, CanjeRecompensa, Notificacion
    │   │   └── auditoria/                        # RegistroAuditoria
    │   └── resources/application.properties      # configuración
    └── test/
        ├── java/com/appresso/backend/            # pruebas
        └── resources/application.properties      # configuración SOLO de pruebas (H2)
```

Paquete base: `com.appresso.backend`. Mapa del modelo de datos: [`docs/modelo-de-datos.md`](../docs/modelo-de-datos.md).
