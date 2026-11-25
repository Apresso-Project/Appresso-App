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
| `LISTA → CERRADA` | Cajero (efectivo) o mesero (transferencia) | Libera la mesa (`DISPONIBLE`) |

El **administrador** puede ejecutar cualquier transición. La regla vive en `PoliticaTransiciones` (única fuente) y cada cambio queda en `historial_comanda` y se publica por tiempo real (`COMANDA_ESTADO_CAMBIADO`, ver `tiempo-real.md`).

Lo que **no** cubre esta historia: la cancelación (RF-63, entregable 4.2.2) y comprobar el pago real al cerrar (módulo de pagos, 6.x). Aquí cerrar solo exige el rol.

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
  "recompensaId": 4
}
```

`clienteId` y `recompensaId` son opcionales (el descuento exige cliente). La comanda nace `PENDIENTE`, la mesa pasa a `OCUPADA`, y la respuesta (`201` + `Location`) trae `lineas[].subtotal`, `subtotal`, `descuento` y `total`, para que el mesero los vea **antes** de confirmar. Para confirmar se envía a cocina con `PATCH /comandas/{id}/estado` (`ENVIADA`).

### Cómo se calcula (`CalculadoraComanda`)

- Subtotal de la línea = cantidad × precio unitario. El precio queda guardado en la línea: cambiar el precio del producto después no altera la comanda.
- Subtotal = suma de los subtotales de las líneas. Total = subtotal − descuento. Todo en pesos con 2 decimales, redondeo HALF_UP.
- Descuento autorizado = el de una **recompensa** de fidelización (RF-54, RF-58): `DESCUENTO_PORCENTAJE` → subtotal × valor / 100; `DESCUENTO_VALOR` → el valor, sin pasar del subtotal (el total nunca es negativo). La recompensa se guarda en la comanda (`recompensa_id`, migración V4), así que subtotal, descuento y total se **recalculan** cada vez que se agrega un producto.
- Una recompensa **no autorizada** (no existe, no es de tipo descuento, está inactiva, fuera de su vigencia, o el cliente no tiene los puntos mínimos) o un descuento sin cliente se rechaza con **400** (`campos.recompensaId`) y no se crea la comanda: el total nunca se modifica.
- Descontar los puntos del cliente (el canje) y aplicar una recompensa a una comanda ya creada son del módulo de fidelización (6.x, RF-58/RF-59); esta historia solo calcula y valida el descuento.

### Errores

| Código | Cuándo |
|---|---|
| **400** | Sin productos o `items` vacío (`campos.items`), cantidad < 1, observaciones > 100, `mesaId` / `clienteId` / producto / recompensa inexistente, descuento no autorizado |
| **409** | La mesa ya tiene una comanda abierta (RF-15: una activa por mesa), producto desactivado o de categoría desactivada, más cantidad que el stock (RF-16) |
| **401 / 403** | Sin sesión / rol sin permiso (cajero, cocinero, cliente) |

## Mesas (RF-64)

| Método y ruta | Quién | Descripción | Respuesta |
|---|---|---|---|
| `POST /mesas` `{"nombre"}` | Solo administrador | Crea la mesa; queda `DISPONIBLE` | 201 + `Location` |
| `GET /mesas` | Administrador y mesero | Mapa de mesas, ordenado por nombre sin distinguir mayúsculas | 200 |

`MesaRespuesta`: `id, nombre, estado` (`DISPONIBLE` / `OCUPADA`). El estado no se edita a mano: una mesa pasa a `OCUPADA` al crear su comanda y vuelve a `DISPONIBLE` al cerrarla.

Nombre: obligatorio (vacío o solo espacios → **400** con `campos.nombre`), ≤30 caracteres (400), **único** sin distinguir mayúsculas ni espacios de los extremos (repetido → **409** con `campos.nombre`). Mesero, cajero, cocinero o cliente que intenten crear → **403**; sin sesión → **401**. Crear queda en la auditoría como `MESA_CREADA` (RNF-12). Las mesas no se editan ni se borran en esta historia.

## Pruebas que cubren la historia (SCRUM-96)

Todas corren con `cd backend && ./gradlew test` (H2, mismas migraciones de Flyway que producción):

| Clase | Qué prueba |
|---|---|
| `CalculadoraComandaTest` | Subtotal por línea, suma, descuento por porcentaje y valor fijo, redondeo, total nunca negativo, total = subtotal − descuento y la validación del descuento autorizado (activo, vigente, puntos mínimos), contra cálculos hechos a mano |
| `PoliticaTransicionesTest` | Los cinco estados; cada transición con cada uno de los cinco roles; ningún salto, retroceso ni repetición |
| `ComandasControllerTest` | De punta a punta con la seguridad real: mesas (crear, nombre repetido/vacío/largo, 401/403, mapa), crear comanda (totales, descuentos válidos y rechazados sin crear nada, mesa ocupada, stock), ciclo completo con cada rol, transiciones inválidas con 400 sin cambiar el estado, comanda vacía, y el **contrato** (campos exactos del JSON y formato de error) |
| `ServiciosTiempoRealTest` (existente) | Cada operación publica su evento de tiempo real |
