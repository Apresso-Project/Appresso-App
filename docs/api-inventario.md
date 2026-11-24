# Control de existencias (3.2.1)

Los endpoints de inventario requieren `Authorization: Bearer <JWT>` de un administrador. Responden
`401` sin sesión y `403` para otros roles. El formato de error es `{ "mensaje": "...", "campos": { ... } }`.

| Método y ruta | Resultado |
|---|---|
| `POST /api/inventario/productos/{id}/ajustes` | `200` con el producto actualizado (mismo objeto que [el catálogo](api-catalogo.md)). |
| `GET /api/inventario/movimientos` | `200` con los movimientos del más reciente al más antiguo. |
| `GET /api/inventario/alertas` | `200` con los productos activos cuyo stock es menor o igual al umbral. |

## Ajuste manual

```json
{"tipo":"SALIDA_MANUAL","cantidad":2,"concepto":"Unidades vencidas"}
```

`ENTRADA_MANUAL` suma unidades. `SALIDA_MANUAL` y `AJUSTE_MERMA` restan. La cantidad es un entero
positivo y el concepto es obligatorio (hasta 255 caracteres). `VENTA` se registra mediante la
confirmación de una comanda. Un cuerpo inválido devuelve `400`, un producto inexistente devuelve
`404` y una salida superior al stock devuelve `409` sin guardar el movimiento.

## Historial

Los filtros opcionales son `producto` (parte del nombre, sin distinguir mayúsculas), `desde` y
`hasta` (`AAAA-MM-DD`, días inclusivos en la zona horaria del negocio). Un rango invertido devuelve
`400`. Cada movimiento incluye `id`, `productoId`, `producto`, `tipo`, `cantidad`,
`stockAnterior`, `stockResultante`, `concepto`, `fecha` (instante ISO-8601), `usuarioId` y `usuario`.

## Alerta y descuento automático

El umbral se configura en el alta o edición del [producto](api-catalogo.md). La alerta se activa
también al alcanzar exactamente el umbral y desaparece cuando el stock lo supera.

El flujo de confirmación existente toma un bloqueo de escritura sobre la comanda y sobre los
productos en orden de ID. El descuento, el cambio de estado y los movimientos de venta forman una
sola transacción. Si alguna línea excede la disponibilidad, se revierte la operación completa y
`StockInsuficienteException` identifica el producto. El controlador de envío pertenece a 4.2.1.
Los ajustes manuales usan el mismo bloqueo de producto. `EXISTENCIAS_ACTUALIZADAS` se publica en
`/topic/catalogo` después de una operación confirmada.
