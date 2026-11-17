# Sincronización en tiempo real (8.2.1)

El backend avisa a todos los dispositivos conectados cuando cambia una comanda o el catálogo, usando **WebSocket con STOMP sobre SockJS**. Código: `backend/src/main/java/com/appresso/backend/tiemporeal/`.

## Cómo conectarse

| Quién | Dirección |
|---|---|
| Navegadores / SockJS | `http://<servidor>:8080/ws` |
| **App Android (WebSocket puro)** | `ws://<servidor>:8080/ws/websocket` |

Android no tiene un cliente STOMP oficial. La app usa su propio cliente (`android/app/src/main/java/com/appresso/app/tiemporeal/`, ver «Cliente Android») sobre el WebSocket puro de OkHttp. SockJS solo hace falta si el cliente es un navegador.

Después de conectar, el cliente se **suscribe** a los tópicos que necesite:

| Tópico | Eventos | Quién lo usa |
|---|---|---|
| `/topic/comandas` | `COMANDA_CREADA`, `COMANDA_MODIFICADA`, `COMANDA_ESTADO_CAMBIADO` | cocina, meseros, administrador |
| `/topic/catalogo` | `PRODUCTO_CREADO`, `PRODUCTO_ACTUALIZADO`, `EXISTENCIAS_ACTUALIZADAS` | meseros, administrador |

Los clientes solo **reciben**; no envían mensajes por el WebSocket. Los cambios se hacen por la API REST y el servidor los difunde.

## Formato de los mensajes

Todos los mensajes llevan el mismo sobre (`EventoTiempoReal`), en JSON:

```json
{
  "id": "6f1c0b0e-0a52-4c0b-9a52-1b7b8d6a9c11",
  "secuencia": 42,
  "tipo": "COMANDA_MODIFICADA",
  "fecha": "2026-11-09T20:15:03.412Z",
  "datos": {
    "comandaId": 7,
    "mesaId": 3,
    "estado": "EN_PREPARACION",
    "estadoAnterior": null,
    "total": 13500.00,
    "itemsAgregados": [
      { "productoId": 12, "cantidad": 1, "observaciones": "sin azúcar" }
    ]
  }
}
```

| Campo | Significado |
|---|---|
| `id` | Identificador único. Si llega repetido, se ignora. |
| `secuencia` | Sube de 1 en 1 mientras el servidor no se reinicie. |
| `tipo` | Qué ocurrió (ver tabla de tópicos). |
| `fecha` | Momento en que el servidor lo envió (ISO-8601, UTC). |
| `datos` | `ComandaEventoDatos` (tópico de comandas) o `ProductoEventoDatos` (catálogo). |

`estadoAnterior` solo viene en `COMANDA_ESTADO_CAMBIADO`; `itemsAgregados` solo en `COMANDA_MODIFICADA` (es lo que cocina resalta, RF-21). El evento es una **foto resumida**: para ver todo el detalle de una comanda el cliente consulta la API REST.

Datos de un producto (`ProductoEventoDatos`): `productoId`, `categoriaId`, `nombre`, `precio`, `stockActual`, `umbralMinimo`, `activo`.

## Reconexión y recuperación (lo que debe hacer el cliente Android)

El servidor **no guarda** los eventos que se envían mientras un dispositivo está desconectado. Por eso el cliente debe:

1. **Detectar la caída.** Con los latidos de STOMP (cada 10 s) y con `ConnectivityManager.NetworkCallback` para reaccionar al instante. Mostrar el indicador «Sin conexión» (RNF-21).
2. **Reintentar con retroceso exponencial:** esperar `min(30 s, 1 s × 2^intento)` con una variación aleatoria de ±20 %. Es decir: 1 s, 2 s, 4 s, 8 s, 16 s, 30 s, 30 s… El contador vuelve a cero cuando la conexión se logra.
3. **Al reconectar:** volver a suscribirse a los tópicos y **volver a consultar el estado por REST** (comandas del día y catálogo) antes de seguir con los eventos nuevos.
4. **Vigilar la `secuencia`:** si llega un número que no es el anterior + 1, o uno menor al último (el servidor se reinició), también hay que reconsultar por REST.

> Las consultas REST de comandas y catálogo las construyen otras tareas; esta capa solo define cuándo usarlas.

## Cliente Android (SCRUM-29 y SCRUM-30)

Código en `android/app/src/main/java/com/appresso/app/tiemporeal/`. Es un cliente STOMP propio y pequeño (sin librería STOMP externa) sobre OkHttp, separado en piezas que se pueden probar sin celular:

| Pieza | Qué hace |
|---|---|
| `CodecoStomp`, `MarcoStomp` | Convierte marcos STOMP a texto y de vuelta (STOMP 1.2); reconoce los latidos. |
| `ClienteStomp` | Conecta, manda `CONNECT` con latidos de 10 s, se suscribe, entrega eventos, detecta caídas y **reconecta solo**. |
| `PoliticaReintento` | Retroceso exponencial: `min(30 s, 1 s × 2^intento)` ± 20 %. |
| `EventoTiempoReal` | Sobre JSON del servidor (`id`, `secuencia`, `tipo`, `fecha`, `datos`), más `comanda()` / `producto()` para leer `datos`. |
| `MedidorLatencia` | Calcula el percentil 95 y el porcentaje de eventos bajo 3 s. |
| `FabricaOkHttp`, `ProgramadorExecutor` | Las piezas reales (WebSocket y temporizadores). En las pruebas se reemplazan por simuladas. |
| `ConexionTiempoReal` | Lo que usa la app: une el cliente con el **ciclo de vida** (conecta en `onStart`, desconecta en `onStop`) y con `ConnectivityManager.NetworkCallback`. |

Cómo lo usa una pantalla:

```kotlin
val tiempoReal = ConexionTiempoReal(applicationContext, "ws://192.168.1.10:8080/ws/websocket")
tiempoReal.adjuntar(lifecycle)                       // conecta y desconecta según la pantalla
lifecycleScope.launch { tiempoReal.eventos.collect { actualizarPantalla(it) } }
lifecycleScope.launch { tiempoReal.resincronizar.collect { recargarPorRest() } }
// Indicador «Sin conexión» (RNF-21): tiempoReal.estado  (StateFlow<InfoConexion>)
```

Qué hace el cliente por su cuenta:

- **Suscripciones:** se recuerdan y se restauran después de cada reconexión.
- **Caídas:** cierre o error del socket, mensaje `ERROR`, falta de `CONNECTED` en 10 s, o más de 25 s sin recibir nada (2,5 latidos). Al volver la red (`onAvailable`) reintenta de inmediato y reinicia el retroceso; al perderla (`onLost`) no espera a los latidos.
- **Resincronización:** avisa por `resincronizar` al reconectar, cuando la `secuencia` salta o cuando baja (servidor reiniciado). La app debe responder consultando por REST. La detección de saltos solo vale si el cliente escucha **todos** los tópicos, porque la secuencia es global; con un solo tópico se desactiva sola (`ConexionTiempoReal` lo decide).
- **Duplicados:** un `id` ya visto se ignora.

Las dependencias nuevas son OkHttp, Gson y coroutines (`android/gradle/libs.versions.toml`). El manifiesto pide `INTERNET` y `ACCESS_NETWORK_STATE`; la versión de depuración permite `ws://` sin TLS (en producción se usará `wss://`).

## Cómo publican los servicios

Los servicios inyectan `PublicadorTiempoReal` y lo llaman **dentro de su método transaccional**:

```java
@Transactional
public void enviarAComanda(Long comandaId) {
    // ... cambiar el estado, descontar el inventario, guardar ...
    publicador.publicarComanda(TipoEvento.COMANDA_ESTADO_CAMBIADO,
            new ComandaEventoDatos(comanda.getId(), comanda.getMesa().getId(),
                    "ENVIADA", "PENDIENTE", comanda.getTotal(), null));
}
```

El mensaje sale **solo después del commit**. Si la transacción se revierte, no se envía nada (RNF-10). Si no hay transacción, se envía de inmediato.

Dónde publicar:

| Cuando… | Tipo de evento |
|---|---|
| se abre una comanda | `COMANDA_CREADA` |
| el mesero la envía, cocina la prepara o la termina, o caja la cierra | `COMANDA_ESTADO_CAMBIADO` |
| se agregan productos a una comanda ya enviada | `COMANDA_MODIFICADA` |
| se crea un producto | `PRODUCTO_CREADO` |
| cambia nombre, descripción, precio, umbral mínimo o activo | `PRODUCTO_ACTUALIZADO` |
| cambia el stock (venta o ajuste manual) | `EXISTENCIAS_ACTUALIZADAS` |

## Servicios que ya publican (SCRUM-28)

Servicios base, sin controladores todavía (los endpoints y la validación de roles llegan con 3.1.1 / 4.1.1):

| Servicio | Método | Evento |
|---|---|---|
| `ComandaService` | `crear` (ocupa la mesa; una sola comanda abierta por mesa, RF-15) | `COMANDA_CREADA` |
| `ComandaService` | `cambiarEstado` (solo al estado siguiente y solo el rol autorizado; guarda historial) | `COMANDA_ESTADO_CAMBIADO` |
| `ComandaService` | `agregarProducto` (RF-20: permitido hasta que se pague; marca `agregadoDespuesDeEnvio`; recalcula total) | `COMANDA_MODIFICADA` |
| `ProductoService` | `crear` | `PRODUCTO_CREADO` |
| `ProductoService` | `actualizar` | `PRODUCTO_ACTUALIZADO` |
| `ProductoService` | `ajustarExistencias` (RF-07: exige justificación; no deja el stock en negativo) y `descontarPorVenta` | `EXISTENCIAS_ACTUALIZADAS` |

Reglas tomadas de los requisitos:

- **Transiciones por rol (RF-COMANDA-ESTADO):** `MESERO` envía, `COCINERO` pasa a preparación y a lista, `CAJERO` cierra.
- **Al enviar (RF-18):** la comanda no puede estar vacía, se guarda `fechaEnvio` y se descuenta el inventario (movimiento `VENTA` ligado a la comanda). Si falta stock, no se envía nada.
- **Al agregar a una comanda ya enviada (RF-20):** se valida el stock y se descuenta de inmediato.
- **Productos (RF-12, RF-16):** no se piden cantidades mayores a las existencias ni productos desactivados; observaciones de hasta 100 caracteres.
- **Al cerrar:** la mesa vuelve a `DISPONIBLE`.

Pendiente para otras historias: controladores, convertir los errores en código 400, restringir `ajustarExistencias` al administrador (RF-07) y cancelación de comandas (la documentación no define ese estado).

## Cómo probarlo

### Pruebas automáticas

| Comando | Qué prueba |
|---|---|
| `cd backend && ./gradlew test` | Servidor: entrega, tópicos, reversa de transacciones, **reconexión** de un cliente STOMP real, **carga** (30 dispositivos × 50 eventos, p95 < 3 s) y las reglas de los servicios. |
| `cd android && ./gradlew testDebugUnitTest` | Cliente Android: codificación STOMP, retroceso exponencial, conexión, suscripciones, **caída y reconexión**, latidos, resincronización y medición de latencia. Usan un servidor y un reloj simulados, así que no necesitan celular ni esperar de verdad. |

Cada prueba de reconexión del cliente comprueba, entre otras cosas: esperas de 1, 2, 4, 8, 16 y 30 s; que el contador vuelva a cero al conectar; que las suscripciones se restauren; que se pida resincronizar; y que `detener()` no reintente.

### Prueba manual con celular o emulador (SCRUM-31 y SCRUM-32)

1. Enciende el backend con el perfil `dev` (`backend/README.md`, pasos 1 a 3). Con ese perfil existe un emisor de eventos de prueba: `POST /api/dev/tiempo-real/emitir?cantidad=N&pausaMs=M`. No existe en producción.
2. Instala la versión **debug** de la app. Aparece un segundo ícono, **Appresso Diagnóstico**.
3. En la pantalla, escribe la dirección del servidor: `ws://10.0.2.2:8080/ws/websocket` en el emulador, o `ws://<IP del PC>:8080/ws/websocket` en un celular (mismo Wi-Fi que el PC; abre el puerto 8080 en el firewall de Windows). Pulsa **Conectar**: debe decir «🟢 Conectado».
4. **Latencia (SCRUM-31):** deja el reloj del celular en hora automática y emite eventos:
   ```bash
   curl -X POST "http://localhost:8080/api/dev/tiempo-real/emitir?cantidad=200&pausaMs=100"
   ```
   La pantalla muestra cada evento con su latencia y el resumen `p95 = … ms · … % bajo 3 s`. **Criterio: al menos 95 % de los eventos en menos de 3 s.** Repite con varios dispositivos a la vez (Plan de Calidad) y anota los resultados.
5. **Desconexión y reconexión (SCRUM-32):**
   - *Servidor caído:* detén el backend con `Ctrl + C`. La pantalla pasa a «🔴 Sin conexión · reintento en 1 s», luego 2 s, 4 s, 8 s… hasta 30 s. Vuelve a encenderlo: reconecta solo, aparece una línea `↻ RESINCRONIZAR` y los eventos nuevos vuelven a llegar.
   - *Red caída:* activa el modo avión unos segundos y desactívalo. Debe reconectar casi al instante (no espera el retroceso).
   - *Segundo plano:* manda la app a segundo plano; la conexión se cierra, y al volver se reabre.
   - *Eventos perdidos:* con la app desconectada emite eventos; al volver verás la línea `↻ RESINCRONIZAR` (la app real consultará por REST).

> La latencia que mide la pantalla incluye la diferencia entre el reloj del servidor y el del celular. Si ambos están en hora automática, el error es de milisegundos. Esta prueba mide la red local, no la del local de la cafetería: la medición final se repite allí (Plan de Calidad, riesgo R-11).

## Mediciones en dispositivo real (SCRUM-31 y SCRUM-32)

> **COMPLETAR con lo medido.** Anota los valores tal cual; si algo no se pudo probar, escribe «no se probó» y el motivo.

- **Fecha, red y equipos:** … (Wi-Fi o punto de acceso; modelo y Android de cada teléfono; cuántos dispositivos conectados a la vez).
- **Eventos emitidos:** … (cantidad y pausa usadas).

| Dispositivo | Eventos recibidos | p95 (ms) | % bajo 3 s |
|---|---|---|---|
| … | … | … | … |

Criterio: al menos 95 % de los eventos bajo 3 s. Resultado: …

| Prueba | Qué pasó | Tiempo en reconectar |
|---|---|---|
| Servidor caído y de vuelta | … | … |
| Modo avión | … | … |
| App en segundo plano | … | … |
| Eventos perdidos (aparece `↻ RESINCRONIZAR`) | … | — |
| Wi-Fi sin Internet: tiempo hasta «Sin conexión» (RNF-21: ≤ 5 s) | … | — |

**No se pudo probar:** …

## Pruebas del servidor

`ServiciosTiempoRealTest` comprueba, con un publicador simulado, que cada operación publica el evento correcto, que se cumplen las reglas de arriba y que una operación inválida no publica nada.

`./gradlew test` ejecuta `TiempoRealTest`, que levanta el servidor y usa clientes STOMP reales:

- entrega de eventos y separación de tópicos;
- un tipo de evento no se puede publicar en el tópico equivocado;
- una transacción revertida no envía nada;
- reconexión: un cliente que vuelve a conectar sigue recibiendo y ve el salto en la `secuencia`;
- **carga básica:** 30 dispositivos conectados (RNF-04) y 50 eventos; verifica que llegan todos y que el percentil 95 de la latencia es menor a 3 s. Imprime la latencia medida en la salida de la prueba.

La prueba de carga corre en una sola máquina, así que mide el servidor, no la red del local. La medición real en la cafetería se hace en la prueba piloto.

## Límites actuales

- **Cliente sin REST de resincronización:** el cliente avisa cuándo consultar por REST, pero las consultas de comandas y catálogo las construyen otras tareas.
- **Sin autenticación en el WebSocket:** el login ya existe (2.1.1), pero el `CONNECT` todavía no valida el token, así que cualquiera que llegue al servidor puede suscribirse. Esa validación queda para la capa de autorización 2.3.1 (RNF-07).
- **Broker en memoria:** sirve con un solo servidor. Con varios habría que usar un broker externo.
- **Los eventos no se guardan:** la recuperación depende de reconsultar por REST.
