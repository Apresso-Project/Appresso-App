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

## Mesas (RF-64)

| Método y ruta | Quién | Descripción | Respuesta |
|---|---|---|---|
| `POST /mesas` `{"nombre"}` | Solo administrador | Crea la mesa; queda `DISPONIBLE` | 201 + `Location` |
| `GET /mesas` | Administrador y mesero | Mapa de mesas, ordenado por nombre sin distinguir mayúsculas | 200 |

`MesaRespuesta`: `id, nombre, estado` (`DISPONIBLE` / `OCUPADA`). El estado no se edita a mano: una mesa pasa a `OCUPADA` al crear su comanda y vuelve a `DISPONIBLE` al cerrarla.

Nombre: obligatorio (vacío o solo espacios → **400** con `campos.nombre`), ≤30 caracteres (400), **único** sin distinguir mayúsculas ni espacios de los extremos (repetido → **409** con `campos.nombre`). Mesero, cajero, cocinero o cliente que intenten crear → **403**; sin sesión → **401**. Crear queda en la auditoría como `MESA_CREADA` (RNF-12). Las mesas no se editan ni se borran en esta historia.
