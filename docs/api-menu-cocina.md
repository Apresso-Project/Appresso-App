# API del menú y de la vista de cocina (HU 3.3.1)

Base: `/api`. Autenticación: `Authorization: Bearer <JWT>`. Permisos definidos en `PoliticaPermisos` (ver `permisos-por-rol.md`).

## Menú (SCRUM-87)

`GET /api/menu`: lo que se puede pedir ahora, agrupado por categoría. Solo lectura (RF-11).

| Quién | Resultado |
|---|---|
| `ADMINISTRADOR`, `MESERO`, `CLIENTE` | 200 |
| `CAJERO`, `COCINERO` | 403 |
| Sin sesión | 401 |

Un producto aparece **solo si** cumple las tres condiciones (RF-11, RF-13):

1. el producto está activo,
2. su categoría está activa,
3. le queda al menos una unidad.

Una categoría sin productos disponibles no aparece. Las categorías van por nombre y, dentro de cada una, los productos por nombre (sin distinguir mayúsculas). Se calcula en cada consulta con los datos vigentes, sin caché: un ingreso de existencias, un cambio de precio o desactivar algo se ve en la siguiente consulta.

```json
[
  {
    "id": 1,
    "nombre": "Bebidas",
    "productos": [
      {
        "id": 9,
        "nombre": "Latte",
        "descripcion": "Con leche",
        "precio": 6500.00,
        "imagenUrl": null,
        "existencias": 12,
        "disponibilidad": "DISPONIBLE"
      }
    ]
  }
]
```

`disponibilidad` (RNF-17, se muestra con texto y no solo con color):

| Valor | Cuándo | Texto sugerido |
|---|---|---|
| `DISPONIBLE` | existencias **mayores** al umbral mínimo | «Disponible: N» |
| `POCAS_UNIDADES` | existencias **iguales o menores** al umbral y mayores a cero | «Pocas unidades: N» |

El umbral mínimo no se envía (es un dato del administrador, `/api/productos`). Un producto con cero existencias no llega; para el cliente es como si no existiera.

> `GET /api/productos` (3.1.1) sigue siendo el listado de **gestión** del administrador (incluye lo desactivado con `?todos=true`). La pantalla de menú debe usar `/api/menu`, que además oculta lo agotado.

## Tiempo real del menú

Para mantener la pantalla al día sin recargar, el cliente escucha `/topic/catalogo` (`PRODUCTO_*`, `EXISTENCIAS_ACTUALIZADAS`, `CATEGORIA_*`; ver `tiempo-real.md`) y, ante cualquiera, vuelve a pedir `GET /api/menu`. El evento es una foto resumida; el menú completo siempre se lee por REST.

## Comandas activas para cocina (SCRUM-88)

`GET /api/comandas/cocina`: las comandas que la pantalla de cocina debe mostrar (RF-23, RF-24).

| Quién | Resultado |
|---|---|
| `ADMINISTRADOR`, `MESERO`, `CAJERO`, `COCINERO` | 200 (regla `GET /api/comandas/**`, RF-19 y RF-23) |
| `CLIENTE` | 403 |
| Sin sesión | 401 |

Salen las comandas en estado `ENVIADA`, `EN_PREPARACION` o `LISTA`, **de la más antigua a la más reciente por hora de envío** (`fechaEnvio`; si coincide, por id). No salen las `PENDIENTE` (creadas pero no enviadas, RF-23 criterio 2) ni las `CERRADA` (pagadas o canceladas).

```json
[
  {
    "id": 7,
    "mesaId": 3,
    "mesa": "Mesa 3",
    "estado": "ENVIADA",
    "estadoCocina": "Pendiente",
    "fechaEnvio": "2026-11-22T20:15:03.412Z",
    "items": [
      { "productoId": 9, "producto": "Latte", "cantidad": 2, "observaciones": "sin azúcar", "agregadoDespuesDeEnvio": false },
      { "productoId": 4, "producto": "Croissant", "cantidad": 1, "observaciones": null, "agregadoDespuesDeEnvio": true }
    ]
  }
]
```

| `estado` | `estadoCocina` (nombre en pantalla) |
|---|---|
| `ENVIADA` | Pendiente |
| `EN_PREPARACION` | En preparación |
| `LISTA` | Lista |

- `items` va en el orden en que se agregaron. Las observaciones salen completas (hasta 100 caracteres) para no truncarlas (RF-24).
- `agregadoDespuesDeEnvio = true` es lo que cocina resalta como «Agregado» (RF-21). Eliminar productos de una comanda enviada aún no existe en el backend (RF-20 queda para 4.2.2), así que no hay marca de «Eliminado».
- No lleva precios ni totales: cocina no los necesita.

## Publicación por WebSocket (SCRUM-88)

Los eventos de comanda ya los publica `ComandaService` en `/topic/comandas` (8.2.1), **después del commit**, así que cocina nunca ve algo que luego se revierte. Esta es la regla que debe seguir la pantalla de cocina (RF-26):

| Evento | Qué hace cocina |
|---|---|
| `COMANDA_CREADA` | Nada: nace `PENDIENTE` y aún no está confirmada. Puede ignorarse. |
| `COMANDA_ESTADO_CAMBIADO` | Vuelve a pedir `GET /api/comandas/cocina`. Aparece al pasar a `ENVIADA`, cambia de columna con `EN_PREPARACION` y `LISTA`, y desaparece al pasar a `CERRADA`. |
| `COMANDA_MODIFICADA` | Vuelve a pedir la lista; los productos nuevos vienen con `agregadoDespuesDeEnvio = true`. El evento trae además `itemsAgregados`, útil para mostrar la alerta de RF-21 sin esperar la respuesta. |

El evento es una foto resumida (`comandaId`, `mesaId`, `estado`…); el detalle completo siempre sale de la lista REST. Además, al conectar, al reconectar y cuando la `secuencia` salta (`tiempo-real.md`), la pantalla pide la lista por REST: así recupera lo que pasó mientras estuvo desconectada.

**Plazo:** la comanda aparece en cocina y los cambios de estado se propagan en menos de 3 segundos (RF-18, RF-21, RF-26). `CocinaTiempoRealTest` lo comprueba con un cliente WebSocket real y commits reales: mide desde que se pide el cambio hasta que el evento llega y la lista REST ya lo muestra (envío, producto agregado, preparación, lista y cierre), e imprime los tiempos. Es una prueba en una sola máquina (mide el servidor, no la red del local).

## Rendimiento del menú (SCRUM-89)

La consulta del menú es una sola consulta a la base (`ProductoRepository.findDelMenu`, con la categoría cargada junto al producto, sin consultas por fila). `MenuTest` comprueba con un catálogo grande (10 categorías × 100 productos, más de 50 veces el tamaño esperado de una cafetería) que la respuesta completa tarda **2 segundos o menos**, y que esa cifra incluye seguridad, consulta y conversión a JSON.

## Pruebas (SCRUM-89)

`cd backend && ./gradlew test` incluye:

| Clase | Qué comprueba |
|---|---|
| `MenuTest` | Solo salen productos activos, de categoría activa y con stock; agrupación y orden; precio y existencias vigentes (ingreso manual reaparece, venta que agota lo quita, cambio de precio); `DISPONIBLE` y `POCAS_UNIDADES` (igual al umbral cuenta como pocas); el umbral no se envía; roles (admin, mesero y cliente 200; cajero y cocinero 403; sin sesión 401); solo lectura; **rendimiento ≤ 2 s** con 1000 productos. |
| `ComandaCocinaTest` | Pendientes y cerradas no salen; los tres estados con su nombre; orden por hora de envío (no por creación ni por estado); mesa, productos, cantidades y observaciones sin recortar; producto agregado marcado; sin precios; roles (cliente 403, sin sesión 401). |
| `CocinaTiempoRealTest` | De punta a punta con WebSocket real: la comanda aparece en cocina y cada cambio de estado se propaga en **menos de 3 s**. |
| `PoliticaPermisosTest` | La regla nueva de `/api/menu/**`, con los cinco roles. |

Los tiempos son de una sola máquina (miden el servidor, no la red del local). La medición bajo condiciones normales de red se repite con los teléfonos en la prueba piloto (`tiempo-real.md`, mediciones en dispositivo real).
