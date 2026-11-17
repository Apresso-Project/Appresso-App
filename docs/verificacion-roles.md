# Verificación con los cinco roles — Appresso

Evidencia del entregable 2.3.1 (control de acceso por rol), subtarea SCRUM-51. Comprueba los criterios de aceptación con el backend y la app reales.

| Criterio de aceptación (2.3.1) | Cómo se verifica |
|---|---|
| Todo endpoint protegido rechaza a los roles no autorizados con 403 | Parte A (script) |
| Cada rol ve solo las opciones que le corresponden, con los cinco roles | Parte B (app) |
| La app muestra "Sin conexión a Internet. Por favor, verifica tu red." y no se cierra | Parte C (app) |
| Las pruebas automatizadas cubren cada rol contra cada endpoint restringido | `PoliticaPermisosTest` (backend, 42 reglas × 5 roles) y `PermisosTest` (app) |

**Fecha:** ____ · **Quién verificó:** ____ · **Commit:** ____ · **Dispositivo:** ____ (Android __)

## Preparación

1. En `backend/.env`, con el perfil `dev`:
   ```
   spring.profiles.active=dev
   DEV_ADMIN_CORREO=admin@appresso.test
   DEV_ADMIN_CONTRASENA=una_clave
   DEV_USUARIOS_CONTRASENA=otra_clave
   ```
2. Encender la base y el backend (`docker compose up -d`, `.\gradlew.bat bootRun`). En la consola deben aparecer los cinco usuarios de prueba (o "ya existe"):

   | Rol | Correo | Contraseña |
   |---|---|---|
   | Administrador | `admin@appresso.test` | `DEV_ADMIN_CONTRASENA` |
   | Cajero | `cajero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Mesero | `mesero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Cocinero | `cocinero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Cliente | `cliente@appresso.test` | `DEV_USUARIOS_CONTRASENA` |

3. Instalar la app en el teléfono conectado al backend (ver `android/README.md`).

## Parte A — Backend: cada rol contra cada endpoint

Desde `backend/`, en PowerShell:

```
.\scripts\verificar-roles.ps1 -ClaveAdmin "una_clave" -ClaveUsuarios "otra_clave"
```

Inicia sesión con los cinco roles y prueba las 42 reglas de [`permisos-por-rol.md`](permisos-por-rol.md) con cada uno y sin token (252 verificaciones). Guarda el detalle en `verificacion-roles.csv`.

| Resultado | |
|---|---|
| Verificaciones correctas | 252 / 252 (17/11/2026) |
| Fallas | 0 |
| Evidencia | `verificacion-roles.csv` + captura de la consola |

## Parte B — App: cada rol ve solo lo suyo

Para cada rol: iniciar sesión, comparar el menú con la columna "Debe ver", abrir una opción, volver con el botón atrás y cerrar sesión. Marcar ✅ o ❌ y adjuntar una captura del inicio.

| Rol | Debe ver (en este orden) | No debe ver (ejemplos) | Menú correcto | Abre y vuelve | Cierra sesión | Captura |
|---|---|---|:-:|:-:|:-:|---|
| Administrador | Usuarios · Productos e inventario · Mesas y comandas · Cocina · Cobros · Caja · Solicitudes de retiro · Clientes · Recompensas y promociones | Pedidos · Mis puntos | | | | |
| Cajero | Cobros · Pedidos · Caja · Solicitudes de retiro | Usuarios · Cocina · Clientes | | | | |
| Mesero | Mesas y comandas · Cobros · Clientes | Usuarios · Caja · Cocina | | | | |
| Cocinero | Cocina | Todo lo demás | | | | |
| Cliente | Mis puntos · Recompensas y promociones | Todo lo del personal | | | | |

## Parte C — App: pérdida de conexión

| Caso | Pasos | Resultado esperado | ✅/❌ |
|---|---|---|:-:|
| Backend apagado al iniciar sesión | Detener el backend (`Ctrl + C`) e intentar entrar | Mensaje "Sin conexión a Internet. Por favor, verifica tu red." y la app sigue abierta | |
| Credenciales erróneas | Contraseña incorrecta | "Credenciales inválidas", sin decir qué dato falló | |
| Recuperación | Encender el backend e iniciar sesión de nuevo | Entra normalmente | |

## Pendiente para cuando existan las pantallas de los módulos

Hoy las secciones son provisionales y no llaman al backend después del login, así que estos dos casos se cubren con pruebas automatizadas y se verifican en la app cuando cada módulo tenga su pantalla:

- **403 en la app:** aviso "No tienes permiso para realizar esta acción." sin cerrar la sesión (`InterceptorErroresTest`, SCRUM-49).
- **401 con la sesión abierta** (por ejemplo, un usuario desactivado por el administrador): la app vuelve sola al login (`InterceptorSesionTest`, SCRUM-46).

## Observaciones

_(Anotar aquí cualquier falla encontrada, cómo se corrigió y el commit de la corrección.)_
