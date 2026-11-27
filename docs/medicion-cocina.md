# Medición de la vista de cocina en dispositivo (SCRUM-93)

**Criterio (RF-18, RF-21, RF-26):** una comanda aparece en la pantalla de cocina, y cada cambio de estado se propaga, en **menos de 3 segundos** desde que el servidor lo publica.

> **Estado: medición en teléfono aplazada.** La verificación funcional en teléfono puede hacerse con el backend y el APK de la misma revisión. Registra tiempos cuando se retome SCRUM-93. [Mediciones del servidor](api-menu-cocina.md)

## Qué se mide

La app mide cada cambio con `MedidorCocina` y escribe una línea por muestra en logcat (etiqueta `Cocina`):

```
evento=COMANDA_ESTADO_CAMBIADO llegada=120 ms actualizacion=85 ms total=205 ms | n=3, p95=205 ms, máximo=205 ms, bajo 3 s: 100.0 %
```

| Campo | Qué es | Reloj |
|---|---|---|
| `llegada` | Del instante en que el servidor publicó el evento a que el teléfono lo recibió (red + WebSocket). | Servidor y teléfono |
| `actualizacion` | De que el teléfono recibió el evento a que la lista nueva ya estaba en el estado de la pantalla (pedir `GET /api/comandas/cocina`, leerla y reemplazarla). | Solo el teléfono |
| `total` | `llegada + actualizacion`, desde la publicación hasta que el estado de la vista tiene los datos nuevos. | Servidor y teléfono |

- Al final de cada línea va el resumen acumulado: `n` muestras, percentil 95, máximo y el porcentaje de cambios por debajo de 3 s.
- Un cambio que llega mientras se está pidiendo la lista se mide cuando la consulta **repetida** lo refleja, no antes.
- Las resincronizaciones (reconexión) no traen fecha del servidor: salen con `llegada=n/d total=n/d` y solo cuentan `actualizacion`.
- Si `llegada` sale **negativa** o muy grande, los relojes no están sincronizados: activa la **hora automática** en el teléfono (y en el PC que corre el backend) y repite.

## Preparación

1. Backend con el perfil `dev` (`backend/README.md`, pasos 1 a 3) y el celular en la misma red Wi-Fi que el PC (puerto 8080 abierto en el firewall de Windows). La app usa la dirección del servidor configurada para el entorno de desarrollo.
2. Teléfono con la hora automática activada. Instala la app (variante debug), con el cable USB y la depuración USB activada.
3. Inicia sesión con una cuenta de **cocinero** (o administrador) y abre **Cocina**. Debe decir «Conectando…» y luego quitarse el aviso (conectado).
4. En el PC, en otra terminal: `adb logcat -s Cocina` (o filtra por la etiqueta `Cocina` en Logcat de Android Studio).

## Qué se puede emitir hoy

Todavía no existen los endpoints para crear y mover comandas desde la app (llegan con las historias de comandas del módulo 4), así que la medición completa «mesero envía → cocina ve» se repite cuando existan. Mientras tanto, con el perfil `dev`, el backend tiene un emisor de eventos de cocina (solo administrador):

```bash
curl -X POST "http://localhost:8080/api/dev/tiempo-real/emitir-cocina?cantidad=50&pausaMs=300" \
     -H "Authorization: Bearer <token del administrador>"
```

Emite `COMANDA_ESTADO_CAMBIADO` y `COMANDA_MODIFICADA` para comandas sintéticas. La app vuelve a consultar la lista y registra cuándo aplicó la respuesta al estado de la vista. Para el piloto, mide también un envío real hasta que la comanda sea visible. Registra los eventos emitidos y las muestras reportadas para detectar actualizaciones ausentes o fallidas.

## Procedimiento

1. Abre Cocina para iniciar un registro nuevo y mantén el teléfono con la pantalla encendida.
2. Emite 50 eventos. Copia el resumen final de logcat y compara el número de muestras con los eventos emitidos.
3. Repite con **dos o más teléfonos** a la vez (Plan de Calidad, RNF-04) y anota cada uno.
4. **Desconexión:** apaga el Wi-Fi del teléfono con Cocina abierta. Debe aparecer «Sin conexión…» en 5 s o menos (RNF-21) y la lista se conserva. Vuelve a conectar: el aviso desaparece y la lista se actualiza sola (`RESINCRONIZACION` en logcat).
5. **Segundo plano:** manda la app a segundo plano y vuelve. Debe reconectar y releer la lista.

## Resultados

> **COMPLETAR con lo medido.**

- **Fecha, commits, red y equipos:** … (backend y APK, Wi-Fi o punto de acceso, modelo y Android de cada teléfono, número de dispositivos conectados).
- **Eventos emitidos:** … (cantidad y pausa).

| Dispositivo | Muestras | `llegada` típica (ms) | `actualizacion` típica (ms) | p95 total (ms) | Máximo total (ms) | % bajo 3 s |
|---|---|---|---|---|---|---|
| … | … | … | … | … | … | … |

Criterio: p95 de `total` y todos los cambios bajo 3 s. Resultado: …

| Prueba | Qué pasó | Tiempo |
|---|---|---|
| Wi-Fi apagado: aviso «Sin conexión» (RNF-21: ≤ 5 s) | … | … |
| Wi-Fi de vuelta: lista actualizada | … | … |
| App en segundo plano y de regreso | … | … |

**No se pudo probar:** …

## Pruebas automáticas relacionadas

`cd android && ./gradlew testDebugUnitTest` incluye `MedidorCocinaTest` (cálculo de `llegada`, `actualizacion`, `total`, percentil 95 y porcentaje bajo 3 s con un reloj simulado), `CocinaViewModelTest` (la medición ocurre cuando la lista nueva está en pantalla, también para un cambio que llega durante una consulta) y `CambiosCocinaTest`. En el backend, `CocinaTiempoRealTest` mide el servidor con un cliente WebSocket real (resultados en `api-menu-cocina.md`).
