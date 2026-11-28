# API de comandas y mesas (HU 4.1.1)

Base: `/api`. Autenticación: `Authorization: Bearer <JWT>`. Quién entra a cada ruta lo define `PoliticaPermisos` (ver `permisos-por-rol.md`). Errores con el formato de siempre: `{"mensaje", "campos"?}`.

## Ciclo de vida

Una comanda pasa por **cinco estados** y solo avanza **un paso por vez** (sin saltos ni retrocesos):

```
PENDIENTE → ENVIADA → EN_PREPARACION → LISTA → CERRADA
```

| Transición | Quién la ejecuta | Qué pasa además |
|---|---|---|
| `PENDIENTE → ENVIADA` | Mesero | Debe tener al menos un producto. Descuenta el inventario (RF-18) y guarda la hora de envío |
| `ENVIADA → EN_PREPARACION` | Cocinero | |
| `EN_PREPARACION → LISTA` | Cocinero | |
| `LISTA → CERRADA` | Cajero (efectivo o transferencia) o mesero (transferencia) | Libera la mesa (`DISPONIBLE`) |

El **administrador** puede ejecutar cualquier transición. La regla vive en `PoliticaTransiciones` (única fuente) y cada cambio queda en `historial_comanda` y se publica por tiempo real (`COMANDA_ESTADO_CAMBIADO`, ver `tiempo-real.md`).

El pedido se entrega al cliente cuando está listo. En la operación normal, el módulo de pagos (6.1.1, `docs/api-pagos.md`) confirma el cobro y cierra la comanda. El `PATCH` a `CERRADA` de 4.1.1 solo valida el rol y no genera pago. La cancelación (RF-63) corresponde a 4.2.2.

### `PATCH /comandas/{id}/estado`

Cuerpo: `{"estado": "ENVIADA"}`. Entran administrador, cajero, mesero y cocinero (los demás, 403; sin sesión, 401); lo que cada rol puede poner lo decide la tabla de arriba.

| Resultado | Cuándo |
|---|---|
| **200** `ComandaRespuesta` | Transición válida |
| **400** | Estado que no es el siguiente, estado inexistente o faltante, rol que no corresponde, o enviar una comanda sin productos |
| **404** | La comanda no existe |

### `GET /comandas/{id}`

Detalle (`ComandaRespuesta`). Lo pueden consultar administrador, cajero, mesero y cocinero.

`ComandaRespuesta`: `id, mesaId, mesaNombre, meseroId, clienteId, estado, lineas[{id, productoId, productoNombre, cantidad, precioUnitario, subtotal, observaciones, agregadoDespuesDeEnvio}], subtotal, descuento, total, fechaCreacion, fechaEnvio`.

## Crear comanda (RF-15, RF-16, RF-17)

### `POST /comandas`

Administrador y mesero. El mesero queda registrado como autor. Cuerpo:

```json
{
  "mesaId": 3,
  "items": [ {"productoId": 7, "cantidad": 2, "observaciones": "sin azúcar"} ],
  "clienteId": 12,
  "recompensaId": 4,
  "totalEsperado": "6500.00"
}
```

`clienteId` y `recompensaId` son opcionales (el descuento exige cliente). Android envía `totalEsperado` con el importe que vio el mesero. Si difiere del cálculo vigente, el servidor devuelve **409** con `campos.totalEsperado`. La app actualiza el resumen para que el mesero decida si quiere guardar con el precio nuevo. Los consumidores que omiten el campo reciben el cálculo vigente del servidor.

Al guardar, la comanda nace `PENDIENTE`, la mesa pasa a `OCUPADA` y la respuesta (`201` + `Location`) trae los subtotales, el descuento y el total oficiales. La creación bloquea la fila de la mesa mientras comprueba si tiene una comanda activa. El flujo de envío desde Android pertenece a 4.2.1.

### Cómo se calcula (`CalculadoraComanda`)

- Subtotal de la línea = cantidad × precio unitario. El precio queda guardado en la línea: cambiar el precio del producto después no altera la comanda.
- Subtotal = suma de los subtotales de las líneas. Total = subtotal − descuento. Todo en pesos con 2 decimales, redondeo HALF_UP.
- Descuento autorizado = el de una **recompensa** de fidelización (RF-54, RF-58): `DESCUENTO_PORCENTAJE` → subtotal × valor / 100; `DESCUENTO_VALOR` → el valor, sin pasar del subtotal (el total nunca es negativo). La recompensa se guarda en la comanda (`recompensa_id`, migración V4), así que subtotal, descuento y total se **recalculan** cada vez que se agrega un producto.
- Una recompensa **no autorizada** (no existe, no es de tipo descuento, está inactiva, fuera de su vigencia, o el cliente no tiene los puntos mínimos) o un descuento sin cliente se rechaza con **400** (`campos.recompensaId`) y no se crea la comanda: el total nunca se modifica.
- El canje de puntos y la aplicación de una recompensa a una comanda ya creada corresponden al módulo de fidelización (7.x, RF-58/RF-59).

### Errores

| Código | Cuándo |
|---|---|
| **400** | Sin productos o `items` vacío (`campos.items`), cantidad < 1, observaciones > 100, `mesaId` / `clienteId` / producto / recompensa inexistente, descuento no autorizado |
| **409** | La mesa ya tiene una comanda abierta (RF-15), producto desactivado o con stock insuficiente (RF-16), o el total vigente difiere del revisado |
| **401 / 403** | Sin sesión / rol sin permiso (cajero, cocinero, cliente) |

## Mesas (RF-64)

| Método y ruta | Quién | Descripción | Respuesta |
|---|---|---|---|
| `POST /mesas` `{"nombre"}` | Solo administrador | Crea la mesa; queda `DISPONIBLE` | 201 + `Location` |
| `GET /mesas` | Administrador y mesero | Mapa de mesas, ordenado por nombre sin distinguir mayúsculas | 200 |

`MesaRespuesta`: `id, nombre, estado, comandaActivaId`. La referencia es nula si la mesa está libre. Si hay una comanda activa, Android usa su ID para consultarla con `GET /comandas/{id}`, incluso tras un error de conexión al guardar. La mesa pasa a `OCUPADA` al crear la comanda y vuelve a `DISPONIBLE` al cerrarla.

Nombre: obligatorio (vacío o solo espacios → **400** con `campos.nombre`), ≤30 caracteres (400), **único** sin distinguir mayúsculas ni espacios de los extremos (repetido → **409** con `campos.nombre`). Mesero, cajero, cocinero o cliente que intenten crear → **403**; sin sesión → **401**. Crear queda en la auditoría como `MESA_CREADA` (RNF-12). Las mesas no se editan ni se borran en esta historia.

La migración V5 guarda el nombre normalizado con una restricción única. Si una base existente ya contiene nombres que coinciden al normalizarse, deben resolverse antes de aplicar la migración.

## Pruebas que cubren la historia (SCRUM-96)

Todas corren con `cd backend && ./gradlew test` (H2, mismas migraciones de Flyway que producción):

| Clase | Qué prueba |
|---|---|
| `CalculadoraComandaTest` | Subtotal por línea, suma, descuento por porcentaje y valor fijo, redondeo, total nunca negativo, total = subtotal − descuento y la validación del descuento autorizado (activo, vigente, puntos mínimos), contra cálculos hechos a mano |
| `PoliticaTransicionesTest` | Los cinco estados; cada transición con cada uno de los cinco roles; ningún salto, retroceso ni repetición |
| `ComandasControllerTest` | De punta a punta con la seguridad real: mesas (crear, nombre repetido/vacío/largo, 401/403, mapa), crear comanda (totales, descuentos válidos y rechazados sin crear nada, mesa ocupada, stock), ciclo completo con cada rol, transiciones inválidas con 400 sin cambiar el estado, comanda vacía, y el **contrato** (campos exactos del JSON y formato de error) |
| `ComandasConcurrenciaTest` | Dos solicitudes simultáneas dejan una sola comanda activa por mesa y una sola mesa por nombre normalizado. |
| `ServiciosTiempoRealTest` (existente) | Cada operación publica su evento de tiempo real |
