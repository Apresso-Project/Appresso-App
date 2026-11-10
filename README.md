# Appresso App

Aplicación móvil Android para la gestión integral de la cafetería **CaffeBar**. Reemplaza el registro manual de pedidos, cuadres de caja e inventario con un sistema digital centralizado.

---

## Contexto del proyecto

| Campo | Detalle |
|---|---|
| Cliente | Cafetería CaffeBar (Patrocinador: Pepito Pérez) |
| Gerente | Andrés Felipe Salcedo Buitrago |
| Inicio | 28/10/2026 |
| Entrega final (v1.0) | 17/12/2026 |
| Universidad | Universidad del Valle, sede Tuluá |
| Asignaturas | Introducción a la Gestión de Proyectos de Software + Desarrollo de Aplicaciones Móviles |

### Equipo de desarrollo (Descompiladores)

| Nombre | Rol |
|---|---|
| Andrés Felipe Salcedo Buitrago | Gerente del proyecto |
| Diego Alejandro Gómez Puentes | Desarrollador Backend |
| Diego Bolaños Isiquita | Desarrollador Backend |
| Juan Sebastián Aragón Campo | Desarrollador Android |
| David Stiven Mujanajinsoy Jayoy | Desarrollador Android |
| Juan David Asencio Cacimance | Tester / QA |

---

## Problema que resuelve

La cafetería registraba pedidos e inventario a mano, lo que generaba:
- Errores de transcripción y pérdida de información
- Demoras en la comunicación entre meseros y cocina
- Sin sistema de reconocimiento de clientes frecuentes

---

## Stack tecnológico

- **Frontend/Mobile:** Android (Kotlin/Java)
- **Backend:** Spring Boot
- **Tiempo real:** WebSocket STOMP sobre SockJS (para cocina y notificaciones)
- **Notificaciones push:** Firebase Cloud Messaging (FCM)
- **Red:** Requiere Wi-Fi local para comunicación cocina; admin puede acceder remotamente vía internet
- **Seguridad:** Contraseñas con bcrypt, HTTPS, tokens en Android Keystore

---

## Roles del sistema (5 roles)

1. **Administrador** — gestión de productos, inventario, usuarios, caja, recompensas; acceso remoto
2. **Mesero** — crear comandas por mesa, enviar a cocina, cobrar, buscar clientes
3. **Cajero** — registrar pagos en efectivo, solicitar retiros, cierre de caja
4. **Cocinero** — ver comandas en tiempo real, cambiar estado (pendiente → en preparación → listo)
5. **Cliente** — registrarse, acumular puntos de fidelidad, canjear recompensas, ver promociones

---

## Módulos principales

### Módulo 1 — Usuarios y permisos (RF-01 a RF-05)
- Login con email/contraseña
- CRUD de usuarios + asignación de roles (solo admin)
- Control de acceso: 403 en API + mensaje en UI para acciones no autorizadas
- Acceso remoto del admin desde cualquier red

### Módulo 2 — Catálogo e Inventario (RF-06 a RF-14)
- CRUD de productos y categorías (solo admin)
- Stock que disminuye automáticamente al confirmar comanda
- Ajustes manuales de inventario con justificación
- Umbral mínimo de stock por producto → alerta visual al admin
- Historial de movimientos de inventario con filtros

### Módulo 3 — Comandas y Pedidos (RF-15 a RF-27)
- Mapa de mesas → abrir comanda por mesa
- Agregar productos con cantidades y preferencias (validando stock)
- Subtotal/total con descuentos automáticos
- Envío a cocina en ≤ 3 s (WebSocket)
- Modificación de comandas enviadas (no pagadas)
- Notificación en cocina de productos agregados
- Asociar cliente por documento a la comanda
- Historial de cambios de estado por pedido

### Módulo 4 — Cocina (RF-23 a RF-26)
- Pantalla con comandas ordenadas por llegada
- Detalle: mesa, productos, cantidades, observaciones resaltadas
- Cocinero cambia estado: enviada → en preparación → listo
- Cambio se refleja en el mesero en ≤ 3 s (tiempo real)

### Módulo 5 — Caja y Pagos (RF-28 a RF-49)
- Pagos en efectivo (solo cajero) y por transferencia (mesero/cajero)
- QR de la cafetería en pantalla para transferencias; confirmación manual del comprobante
- Saldos separados: efectivo vs transferencias
- Ingresos manuales y retiros directos (admin); solicitudes de retiro (cajero) con aprobación del admin + push notification
- Cierre diario de jornada con informe: saldo inicial + ingresos − egresos = saldo final
- Apertura de nueva jornada con base del cierre anterior

### Módulo 6 — Fidelización (RF-50 a RF-62)
- Registro de clientes: nombre, apellido, documento, celular + aceptación Ley 1581
- 1 punto por pedido pagado (RF-52); el saldo se guarda en el cliente y los puntos no vencen
- Descuento de puntos al canjear una recompensa o una promoción
- Admin configura recompensas (puntos mínimos, costo en puntos, beneficio) y promociones temporales con costo en puntos (p. ej. 2x1, 50 % de descuento); puede crearlas y desactivarlas
- Búsqueda de cliente por documento en ≤ 2 s
- Recompensas de un solo uso marcadas como usadas para evitar doble canje
- Notificaciones push al cliente sobre nuevas recompensas y promociones vigentes

---

## Hitos del cronograma

| Hito | Fecha |
|---|---|
| Inicio de desarrollo (Sprint 0) | 28/10/2026 |
| Módulos admin, pedidos y cocina terminados | 15/11/2026 |
| Módulo fidelización + pago QR terminados | 29/11/2026 |
| Prueba piloto y entrega final v1.0 | 17/12/2026 |

---

## Fuera del alcance

- Solo Android (no iOS ni web para clientes externos)
- Sin facturación electrónica ni contabilidad formal
- Sin integración con pasarela de pagos (el QR es solo visual; la confirmación es manual)
- Sin pedidos a domicilio
- Sin compra de insumos

---

## Repositorios

- **Este repo** — código fuente de la app Android + backend
- **Documentation-Repo** — acta de constitución, EDT, historias de usuario, requerimientos, cronograma

---

## Restricciones importantes

- La cafetería debe tener Wi-Fi para que los pedidos lleguen a cocina (se usa `ConnectivityManager.NetworkCallback` para detectar desconexión)
- Cumplimiento de Ley 1581/2012 (protección de datos personales en Colombia): formulario de registro exige aceptación explícita
- Contraseñas con hash (bcrypt), HTTPS en producción, sesiones en Android Keystore
- Cobertura mínima de pruebas unitarias: 70% de la lógica crítica
