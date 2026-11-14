# Permisos por rol — Appresso

Política de control de acceso del backend (entregable 2.3.1, SCRUM-47). Dice qué rol puede usar cada endpoint. Sale de los requerimientos funcionales (columna **RF**).

> **Fuente de verdad:** `backend/src/main/java/com/appresso/backend/seguridad/PoliticaPermisos.java`. Este documento es su versión legible: si cambias una regla allá, actualízala aquí en el mismo Pull Request. `PoliticaPermisosTest` prueba cada fila con los cinco roles (SCRUM-45).

## Cómo funciona

- Cada solicitud protegida lleva `Authorization: Bearer <token>` (el token del login, ver [`api-auth.md`](api-auth.md)).
- El rol se lee de la **base de datos** en cada solicitud, no del token: si el administrador desactiva una cuenta o le cambia el rol, aplica de inmediato.
- **Sin token, o con token vencido, alterado o de una cuenta desactivada → `401`** `{"mensaje": "Tu sesión no es válida o expiró. Inicia sesión de nuevo."}`. La app debe volver al login.
- **Con token válido pero sin el rol necesario → `403`** `{"mensaje": "No tienes permiso para realizar esta acción."}`. La app muestra el mensaje y se queda donde está.
- **Públicos (sin token):** `POST /api/auth/login`, `GET /api/health`, `GET /actuator/health`.
- Las reglas se evalúan **en orden** y gana la primera que coincide.
- Cada módulo termina con una regla **"cualquier otra operación → nadie"**: un endpoint nuevo que no esté en la política responde `403` en vez de quedar abierto.
- Un endpoint fuera de todos los módulos de la tabla solo exige sesión iniciada (cualquier rol).

`*` = un tramo de la ruta (por ejemplo, un id); `**` = cualquier cosa debajo, incluida la ruta base.

## Matriz

✅ = permitido · vacío = `403`

### Usuarios

| Método | Ruta | Admin | Cajero | Mesero | Cocinero | Cliente | RF |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| Cualquiera | `/api/usuarios/**` | ✅ | | | | | RF-02 |

### Inventario

| Método | Ruta | Admin | Cajero | Mesero | Cocinero | Cliente | RF |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| GET | `/api/categorias/**` | ✅ | | ✅ | | | RF-06, RF-11 |
| Otros | `/api/categorias/**` | ✅ | | | | | RF-06 |
| GET | `/api/productos/**` | ✅ | | ✅ | | | RF-06, RF-11, RF-13 |
| Otros | `/api/productos/**` (incluye umbral mínimo) | ✅ | | | | | RF-06, RF-09 |
| Cualquiera | `/api/inventario/**` (movimientos, alertas, historial) | ✅ | | | | | RF-07, RF-10, RF-14 |

### Comandas y cocina

| Método | Ruta | Admin | Cajero | Mesero | Cocinero | Cliente | RF |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| GET | `/api/mesas/**` | ✅ | | ✅ | | | RF-15 |
| PATCH | `/api/comandas/*/estado` | | | | ✅ | | RF-25 |
| POST | `/api/comandas` | | | ✅ | | | RF-15 |
| POST | `/api/comandas/*/productos` | | | ✅ | | | RF-16, RF-20 |
| POST | `/api/comandas/*/confirmar` | | | ✅ | | | RF-18 |
| PUT | `/api/comandas/*/cliente` | | | ✅ | | | RF-22 |
| POST | `/api/comandas/*/recompensa` | | | ✅ | | | RF-58 |
| GET | `/api/comandas/**` | ✅ | ✅ | ✅ | ✅ | | RF-19, RF-23 |
| Otros | `/api/comandas/**` | | | | | | — |

### Caja y pagos

| Método | Ruta | Admin | Cajero | Mesero | Cocinero | Cliente | RF |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| POST | `/api/pagos/efectivo` | | ✅ | | | | RF-29 |
| POST | `/api/pagos/transferencia` | | ✅ | ✅ | | | RF-30, RF-41, RF-43 |
| GET | `/api/pagos/**` | ✅ | ✅ | | | | RF-38, RF-39 |
| Otros | `/api/pagos/**` | | | | | | — |
| GET | `/api/caja/qr` | | ✅ | ✅ | | | RF-40 |
| GET | `/api/caja/saldos` | ✅ | | | | | RF-39 |
| GET | `/api/caja/movimientos` | ✅ | ✅ | | | | RF-38, RF-39 |
| POST | `/api/caja/ingresos` | ✅ | | | | | RF-32 |
| POST | `/api/caja/retiros` | ✅ | | | | | RF-33 |
| Otros | `/api/caja/**` | | | | | | — |
| PATCH | `/api/solicitudes-retiro/*/decision` | ✅ | | | | | RF-36 |
| POST | `/api/solicitudes-retiro` | | ✅ | | | | RF-34 |
| GET | `/api/solicitudes-retiro/**` | ✅ | ✅ | | | | RF-36 |
| Otros | `/api/solicitudes-retiro/**` | | | | | | — |
| POST | `/api/jornadas/cierre` | | ✅ | | | | RF-44 |
| PATCH | `/api/jornadas/*/base` | ✅ | | | | | RF-47 |
| POST | `/api/jornadas` (abrir jornada) | ✅ | ✅ | | | | RF-49 |
| GET | `/api/jornadas/**` (informes de cierre) | ✅ | ✅ | | | | RF-45, RF-48 |
| Otros | `/api/jornadas/**` | | | | | | — |

### Fidelización

| Método | Ruta | Admin | Cajero | Mesero | Cocinero | Cliente | RF |
|---|---|:-:|:-:|:-:|:-:|:-:|---|
| GET | `/api/clientes/yo` (mis puntos) | | | | | ✅ | RF-55 |
| POST | `/api/clientes` | ✅ | | ✅ | | | RF-50 |
| GET | `/api/clientes/**` (buscar por documento) | ✅ | | ✅ | | | RF-51, RF-57 |
| Otros | `/api/clientes/**` | | | | | | — |
| GET | `/api/recompensas/**` | ✅ | | ✅ | | ✅ | RF-56, RF-57, RF-58 |
| Otros | `/api/recompensas/**` | ✅ | | | | | RF-54, RF-60 |
| GET | `/api/promociones/**` | ✅ | | | | ✅ | RF-62 |
| Otros | `/api/promociones/**` | ✅ | | | | | RF-60 |

## Decisiones pendientes del equipo

La matriz sigue los requerimientos al pie de la letra. Estos puntos no están claros en los RF y hay que confirmarlos:

1. **¿El administrador puede hacer las tareas operativas de otros roles?** Por ejemplo, crear comandas o cobrar en efectivo. Hoy **no**: el RF-29 dice "exclusivamente a los cajeros" para el efectivo, y el RF-25 deja el cambio de estado de preparación solo al cocinero.
2. **Registro de clientes (RF-50):** el criterio habla de que el propio cliente presiona "Registrarme". Si se registra solo desde la app, `POST /api/clientes` debe ser **público**. Hoy lo hacen el administrador y el mesero.
3. **¿Quién abre la jornada (RF-49)?** Hoy: administrador y cajero.
4. **¿El cajero y el cocinero necesitan ver el catálogo de productos?** Hoy solo administrador y mesero.
5. **Endpoints no declarados:** fuera de los módulos de la tabla, hoy basta con iniciar sesión. Se podría cambiar a "denegar todo lo no declarado" para que nada quede abierto por olvido.
6. **Rutas:** son una propuesta. Quien implemente cada módulo debe usarlas o actualizar la regla y esta tabla.
