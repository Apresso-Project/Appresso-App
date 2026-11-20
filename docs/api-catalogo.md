# API de catálogo: productos y categorías (HU 3.1.1)

Base: `/api`. Autenticación: `Authorization: Bearer <JWT>`. Permisos definidos en `PoliticaPermisos` (ver `permisos-por-rol.md`).

| Quién | Qué puede |
|---|---|
| `ADMINISTRADOR` | Todo (lectura y escritura, incluye desactivados con `?todas=true` / `?todos=true`) |
| `MESERO` | Solo leer el menú (lo activo). Pedir `todas`/`todos` o escribir → 403 |
| `CAJERO`, `COCINERO`, `CLIENTE` | 403 en todo |
| Sin sesión | 401 |

## Categorías

| Método y ruta | Descripción | Respuesta |
|---|---|---|
| `GET /categorias[?todas=true]` | Lista ordenada por nombre; `todas` (solo admin) incluye desactivadas | 200 |
| `GET /categorias/{id}` | Detalle; para no admin una desactivada es 404 | 200 / 404 |
| `POST /categorias` `{"nombre"}` | Crea (activa) | 201 + `Location` |
| `PUT /categorias/{id}` `{"nombre"}` | Renombra | 200 |
| `POST /categorias/{id}/desactivar` | Idempotente | 200 |
| `POST /categorias/{id}/activar` | Idempotente | 200 |

`CategoriaRespuesta`: `id, nombre, activa, totalProductos`. Nombre obligatorio, ≤80, único sin distinguir mayúsculas (409 con `campos.nombre`).

## Productos

| Método y ruta | Descripción | Respuesta |
|---|---|---|
| `GET /productos[?categoriaId=][&todos=true]` | Lista ordenada por nombre; `todos` (solo admin) incluye los fuera del menú | 200 |
| `GET /productos/{id}` | Detalle; fuera del menú → 404 para no admin | 200 / 404 |
| `POST /productos` | Crea | 201 + `Location` |
| `PUT /productos/{id}` | Edita | 200 |
| `POST /productos/{id}/desactivar` / `activar` | Idempotentes | 200 |

Crear: `{nombre ≤120, descripcion ≤255?, precio >0 (2 decimales), categoriaId, imagenUrl? (http/https ≤500), existenciasIniciales? 0..1.000.000, umbralMinimo? 0..1.000.000}`.
Editar: igual sin `existenciasIniciales`; las existencias **no** se cambian aquí (solo con ajustes de inventario, RF-07); `umbralMinimo` ausente conserva el actual.
`ProductoRespuesta`: `id, categoriaId, categoriaNombre, categoriaActiva, nombre, descripcion, precio, imagenUrl, stockActual, umbralMinimo, activo`.

## Errores (`{"mensaje", "campos"?}`)

- **400**: validación (`campos` por campo), JSON inválido, categoría inexistente (`campos.categoriaId`).
- **401 / 403**: ver arriba. **404**: id inexistente. **405**: no existe DELETE.
- **409**: nombre de categoría repetido; crear/mover un producto a una categoría desactivada (`campos.categoriaId`).

## Reglas de negocio e integridad referencial

- Nada se borra: solo se desactiva. Las comandas, movimientos y auditoría siguen apuntando a registros existentes.
- Menú = producto activo **y** categoría activa. Desactivar una categoría oculta sus productos sin cambiar su estado; al reactivarla vuelven.
- Un producto fuera del menú no se puede agregar a una comanda.
- Cambiar el precio no altera comandas ya hechas (`DetalleComanda.precioUnitario` guarda el precio del momento).
- Existencias iniciales > 0 generan un movimiento `ENTRADA_MANUAL` "Existencias iniciales" (RF-14).
- Cada operación queda en auditoría (RNF-12): `CATEGORIA_CREADA/RENOMBRADA/DESACTIVADA/ACTIVADA`, `PRODUCTO_CREADO/EDITADO/DESACTIVADO/ACTIVADO`. Operaciones sin cambio real no auditan ni publican.

## Tiempo real

`/topic/catalogo`: `PRODUCTO_CREADO`, `PRODUCTO_ACTUALIZADO` (edición y activar/desactivar), `CATEGORIA_CREADA`, `CATEGORIA_ACTUALIZADA`. Detalle en `tiempo-real.md`.

## Ejemplo

```bash
curl -X POST http://localhost:8080/api/productos -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Latte","precio":6500,"categoriaId":1,"existenciasIniciales":20,"umbralMinimo":5}'
```

## Decisión: imágenes

Se guarda solo la URL (`imagenUrl`); no se suben archivos. Subir archivos queda para una historia aparte.

## Cliente Android (SCRUM-70, 71, 72 y 73)

- **API y repositorio** (`data/remote/CatalogoApi.kt`, `data/catalogo/`): un método por endpoint de este contrato. Cada llamada devuelve un `ResultadoCatalogo` (éxito, datos inválidos 400, conflicto 409, sesión inválida 401, sin permiso 403, no encontrado 404, sin conexión o error del servidor), sin lanzar excepciones. El precio se lee como `BigDecimal` (nunca `Double`) y se envía como texto.
- **Pantalla de categorías** (`ui/catalogo/`): solo la ve el administrador (`Accion.GESTIONAR_CATALOGO`, opción «Productos e inventario»); los demás roles siguen en la pantalla provisional. Lista las categorías con las desactivadas (`?todas=true`), con su conteo de productos, y permite crear, renombrar, desactivar (con confirmación que indica cuántos productos salen del menú) y activar.
- **Validación**: nombre obligatorio y de máximo 80 caracteres, y sin repetir (ignorando mayúsculas) se comprueban en el teléfono; el servidor las vuelve a validar y su error `campos.nombre` se muestra junto al campo sin borrar lo escrito.
- **Tiempo real**: mientras la sección «Productos e inventario» está abierta y la app en primer plano, la app mantiene la conexión STOMP (`ContenedorApp.tiempoReal`, una sola para toda la app, en `ws(s)://<servidor>/ws/websocket`, derivada de `API_BASE_URL`). Cada evento `CATEGORIA_*`, `PRODUCTO_*` o `EXISTENCIAS_*` (`cambiosDeCatalogo()`), y cada reconexión, que pudo perder eventos, recarga en silencio la lista visible por REST (`recargarEnSilencio()`), sin indicador de carga y sin tocar un formulario abierto. Al salir de la sección o pasar a segundo plano, la conexión se detiene.
- **Pantalla de productos** (SCRUM-72, `ui/catalogo/Productos*.kt`): se abre desde «Ver productos» en categorías (y se vuelve con «Categorías»). Lista todos los productos, incluso los fuera del menú (`?todos=true`), con filtro por categoría. Cada tarjeta muestra imagen, precio, categoría, **existencias con su estado** y si está en el menú o por qué no (producto desactivado o categoría desactivada).
- **Existencias (RNF-17)**: «Disponible: N» si superan el umbral, «Pocas unidades: N» si son iguales o menores al umbral y mayores a cero, «Agotado: 0 unidades» si son cero. Siempre en texto, además del color. En el administrador los agotados se muestran (en el menú del mesero no, RF-13).
- **Imagen**: el backend guarda solo la URL. La app la descarga con su propio cliente HTTP, **sin el token de sesión** (es un sitio externo), la reduce al decodificarla y guarda las últimas 24 en memoria. Sin URL muestra «Sin imagen»; si no carga, «No se pudo cargar». Una URL `http://` solo carga si la red del teléfono la permite (Android bloquea texto plano por defecto desde la API 28); se recomienda `https://`.
- **Formulario de producto**: nombre (≤120), descripción (≤255), precio (> 0, hasta 2 decimales, solo números), categoría (solo activas), imagen (http/https ≤500), existencias iniciales y umbral (0 a 1.000.000). Las **existencias iniciales solo se piden al crear**: al editar se muestran sin poder cambiarlas (se ajustan con inventario, RF-07). Los errores de campo del servidor (`campos.*`) salen junto a su campo y no se borra lo escrito; un 409 por categoría desactivada sale junto a la categoría.
- **Pruebas** (SCRUM-73): `CatalogoRepositoryRemotoTest` (contrato HTTP de cada endpoint y cada código de error) y `CategoriasViewModelTest` / `ProductosViewModelTest` (validación, flujos de crear/editar/desactivar, errores, doble toque, recarga por tiempo real).
