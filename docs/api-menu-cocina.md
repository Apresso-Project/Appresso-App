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
