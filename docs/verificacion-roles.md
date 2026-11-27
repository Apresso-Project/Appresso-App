# Verificación con los cinco roles — Appresso

Evidencia del entregable 2.3.1 (control de acceso por rol), subtarea SCRUM-51. Comprueba los criterios de aceptación con el backend y la app reales.

| Criterio de aceptación (2.3.1) | Cómo se verifica |
|---|---|
| Todo endpoint protegido rechaza a los roles no autorizados con 403 | Parte A (script) |
| Cada rol ve solo las opciones que le corresponden, con los cinco roles | Parte B (app) |
| La app muestra "Sin conexión a Internet. Por favor, verifica tu red." y no se cierra | Parte C (app) |
| Las pruebas automatizadas cubren cada rol contra cada endpoint restringido | `PoliticaPermisosTest` (backend, cada regla × 5 roles) y `PermisosTest` (app) |

**Evidencia del teléfono pendiente.** Registra fecha, persona que verifica, commit, modelo y versión de Android.

## Preparación

1. En `backend/.env`, con el perfil `dev`:
   ```
   spring.profiles.active=dev
   DEV_ADMIN_CORREO=admin@appresso.test
   DEV_ADMIN_CONTRASENA=una_clave
   DEV_USUARIOS_CONTRASENA=otra_clave
   ```
2. Encender la base y el backend (`docker compose up -d`, `.\gradlew.bat bootRun`). En la consola deben aparecer los cinco usuarios de prueba (o "ya existe"):

   | Rol | Correo (o documento) | Contraseña |
   |---|---|---|
   | Administrador | `admin@appresso.test` | `DEV_ADMIN_CONTRASENA` |
   | Cajero | `cajero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Mesero | `mesero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Cocinero | `cocinero@appresso.test` | `DEV_USUARIOS_CONTRASENA` |
   | Cliente | documento `1000000001` (su cuenta es `cliente@appresso.test`, pero los clientes entran con el documento) | `DEV_USUARIOS_CONTRASENA` |

3. Instalar la app en el teléfono conectado al backend (ver `android/README.md`).

## Parte A — Backend: cada rol contra cada endpoint

Desde `backend/`, en PowerShell:

```
.\scripts\verificar-roles.ps1 -ClaveAdmin "una_clave" -ClaveUsuarios "otra_clave"
```

El script inicia sesión con los cinco roles y prueba las rutas de su lista con cada uno y sin token (6 verificaciones por regla). Guarda el detalle en `verificacion-roles.csv`. Para un nuevo registro de backend, falta actualizar esa lista con las reglas vigentes de [permisos-por-rol.md](permisos-por-rol.md), incluida `GET /api/menu`.

| Resultado | |
|---|---|
| Verificaciones correctas | 258 / 258 (22/11/2026, antes de integrar el menú 3.3.1) |
| Fallas | 0 |
| Evidencia | `verificacion-roles.csv` + captura de la consola |

## Parte B — App: cada rol ve solo lo suyo

Para cada rol: iniciar sesión, comparar el menú con la columna "Debe ver", abrir una opción, volver con el botón atrás y cerrar sesión. Marcar ✅ o ❌ y adjuntar una captura del inicio.

| Rol | Debe ver (en este orden) | No debe ver (ejemplos) | Menú correcto | Abre y vuelve | Cierra sesión | Captura |
|---|---|---|:-:|:-:|:-:|---|
| Administrador | Usuarios · Productos e inventario · Menú · Mesas y comandas · Cocina · Cobros · Caja · Solicitudes de retiro · Clientes · Recompensas y promociones | Pedidos · Mis puntos | | | | |
| Cajero | Cobros · Pedidos · Caja · Solicitudes de retiro | Usuarios · Menú · Cocina · Clientes | | | | |
| Mesero | Mesas y comandas · Menú · Cobros · Clientes | Usuarios · Caja · Cocina | | | | |
| Cocinero | Cocina | Todo lo demás | | | | |
| Cliente | Menú · Mis puntos · Recompensas y promociones · Mis datos | Usuarios · Productos e inventario · Cocina · Caja | | | | |

## Parte C — App: pérdida de conexión

| Caso | Pasos | Resultado esperado | ✅/❌ |
|---|---|---|:-:|
| Backend apagado al iniciar sesión | Detener el backend (`Ctrl + C`) e intentar entrar | Mensaje "Sin conexión a Internet. Por favor, verifica tu red." y la app sigue abierta | |
| Credenciales erróneas | Contraseña incorrecta | "Credenciales inválidas", sin decir qué dato falló | |
| Recuperación | Encender el backend e iniciar sesión de nuevo | Entra normalmente | |

## Verificación de sesión en una pantalla conectada

Las pruebas automatizadas cubren ambos casos. Para comprobar la sesión vencida o revocada en el teléfono, usa una cuenta de prueba y una pantalla conectada al backend como Menú o Cocina. Usa la prueba del backend de la parte A para validar la denegación por rol cuando la app ya oculta la opción.

- **403 en la app:** aviso "No tienes permiso para realizar esta acción." sin cerrar la sesión (`InterceptorErroresTest`, SCRUM-49).
- **401 con la sesión abierta** (por ejemplo, un usuario desactivado por el administrador): la app vuelve sola al login (`InterceptorSesionTest`, SCRUM-46).

## Observaciones

_(Anotar aquí cualquier falla encontrada, cómo se corrigió y el commit de la corrección.)_
