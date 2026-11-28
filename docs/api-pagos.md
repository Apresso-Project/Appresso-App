# API de pagos — Appresso

Contrato del entregable 6.1.1 (RF-28, RF-29, RF-30, RF-41, RF-43) para quien lo consuma desde la app Android. El código está en `backend/src/main/java/com/appresso/backend/caja/` y las pruebas en `backend/src/test/java/com/appresso/backend/caja/PagoControllerTest.java`.

Todas las peticiones llevan `Authorization: Bearer <token>` (ver [`api-auth.md`](api-auth.md)). Quién puede usar cada ruta lo decide `PoliticaPermisos` (ver [`permisos-por-rol.md`](permisos-por-rol.md)); el servicio vuelve a validar el rol del medio de pago.

| Código | Cuándo | Cuerpo |
|---|---|---|
| **401** | Sin sesión válida | `{ "mensaje": "Tu sesión no es válida o expiró. Inicia sesión de nuevo." }` |
| **403** | El rol no puede usar esa ruta | `{ "mensaje": "No tienes permiso para realizar esta acción." }` |

## Confirmar un pago

| Método y ruta | Quién | Regla |
|---|---|---|
| `POST /api/pagos/efectivo` | Cajero y administrador | RF-29. El mesero, el cocinero y el cliente reciben 403 |
| `POST /api/pagos/transferencia` | Mesero, cajero y administrador | RF-30, RF-41 |

Cuerpo:

```json
{ "comandaId": 17, "identificadorOperacion": "3f2b6c1e-8d4a-4c55-9a0b-1d2e3f4a5b6c" }
```

- `comandaId` es obligatorio.
- **El valor no se envía**: es el total de la comanda y lo calcula el servidor.
- `identificadorOperacion` (UUID) es opcional pero **la app debe enviarlo**: se genera una vez por intento de pago y se reenvía igual si no se supo si la petición llegó (RNF-11). Así un reintento no se confunde con un segundo pago.

Respuesta **201** (pago registrado):

```json
{
  "id": 5,
  "comandaId": 17,
  "mesa": "Mesa 3",
  "valor": 12500.00,
  "medio": "EFECTIVO",
  "fecha": "2026-11-18T15:42:10.123Z",
  "confirmadoPorId": 4,
  "confirmadoPorNombre": "Ana Cajera",
  "identificadorOperacion": "3f2b6c1e-8d4a-4c55-9a0b-1d2e3f4a5b6c",
  "jornadaId": 2,
  "comandaEstado": "CERRADA"
}
```

`medio` es `EFECTIVO` o `TRANSFERENCIA`. `fecha` está en UTC.

### Errores

| Código | Mensaje | Cuándo |
|---|---|---|
| **200** | (el pago ya registrado) | Reintento con el **mismo** `identificadorOperacion`, comanda y medio: devuelve el pago de antes y **no suma otra vez** |
| **400** | `Revisa los datos ingresados.` con `campos.comandaId` | Falta `comandaId` |
| **400** | `La solicitud no tiene un formato válido.` | JSON roto, `comandaId` que no es número o UUID inválido |
| **404** | `Comanda N no existe` | La comanda no existe |
| **409** | `Esta comanda ya fue pagada.` | Ya tiene pago, por cualquier medio o usuario (RF-28, RF-43). También si dos solicitudes llegan a la vez |
| **409** | `Ese identificador de operación ya se usó en otro pago.` | El `identificadorOperacion` pertenece al pago de otra comanda: genera uno nuevo por cada comanda |
| **409** | `La comanda fue cancelada y no admite pagos.` | Está `CERRADA` sin pago: una comanda cerrada sin pago es una cancelada (RF-63) |
| **409** | `La comanda aún no está lista para pagar.` | Su estado no es `LISTA` |
| **409** | `La comanda no tiene un valor por cobrar.` | El total es 0 o menos |

La app debe mostrar el `mensaje` tal cual y, ante un 409 de "ya fue pagada", refrescar la comanda (puede que otro dispositivo la haya cobrado).

## Qué pasa al confirmar

1. Se crea el `Pago` (medio, valor, fecha y hora, usuario que lo confirmó). Una comanda solo admite un pago.
2. La comanda pasa a `CERRADA`, la mesa queda `DISPONIBLE` y se publica `COMANDA_ESTADO_CAMBIADO` por `/topic/comandas` (ver [`tiempo-real.md`](tiempo-real.md)), que ya trae el estado nuevo y el estado anterior.
3. Queda un registro de auditoría `PAGO_REGISTRADO`.

Todo ocurre en una sola transacción: si algo falla, no queda nada a medias.

> Mostrar el código QR no crea ningún registro: el pago existe solo cuando se confirma. El QR y su pantalla son del entregable 6.2.1.

## Decisiones tomadas

- **Jornada automática.** Mientras no exista el flujo de apertura de jornada (RF-49), si se confirma un pago sin jornada abierta se abre una con saldo inicial de efectivo 0 y queda auditado (`JORNADA_ABIERTA_AUTOMATICA`). Cuando se implemente RF-49, esto debería pasar a rechazar el pago con un mensaje.
- **El valor lo pone el servidor**, para que el cliente no pueda cobrar otra cantidad.
- **El cierre normal de una comanda es el pago.** Al confirmar el pago, la comanda `LISTA` pasa a `CERRADA` por `ComandaService.cerrarPorPago`. El `PATCH /api/comandas/{id}/estado` hacia `CERRADA` (HU 4.1.1) sigue existiendo y **no genera pago**: una comanda cerrada sin pago se trata como cancelada. Se revisará con la cancelación de comandas (RF-63, entregable 4.2.2).
- **Pendiente en otras historias:** consultar pagos del día (RF-38), puntos de fidelización por pago (RF-52), QR (6.2.1), ingresos y retiros (6.3.1), saldos (SCRUM-107, mismo entregable).

## Ejemplos

```bash
# Cajero cobra en efectivo
curl -X POST http://localhost:8080/api/pagos/efectivo \
  -H "Authorization: Bearer $TOKEN_CAJERO" -H "Content-Type: application/json" \
  -d '{"comandaId":17,"identificadorOperacion":"3f2b6c1e-8d4a-4c55-9a0b-1d2e3f4a5b6c"}'

# Mesero confirma una transferencia
curl -X POST http://localhost:8080/api/pagos/transferencia \
  -H "Authorization: Bearer $TOKEN_MESERO" -H "Content-Type: application/json" \
  -d '{"comandaId":18}'

```
