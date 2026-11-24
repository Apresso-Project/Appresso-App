# Eliminación de los datos personales — Appresso

Mecanismo para que un cliente pida la supresión de sus datos (Ley 1581 de 2012, art. 15). Documento del entregable 8.4.1 (SCRUM-105). Las demás medidas están en [`seguridad-y-ley-1581.md`](seguridad-y-ley-1581.md); el contrato técnico, en [`api-clientes.md`](api-clientes.md).

## 1. En resumen

- **Quién la pide:** el propio cliente, desde la app, en cualquier momento y sin costo.
- **Qué pasa:** sus datos se borran de inmediato y su cuenta se cierra. Es irreversible.
- **Qué se conserva:** las compras que la ley obliga a guardar, sin ningún dato que lo identifique, y la constancia de que autorizó y de que pidió la eliminación.
- **Quién no puede hacerlo:** el personal de la cafetería, incluido el administrador.

## 2. Cómo lo hace el cliente

1. Inicia sesión con su número de documento y su contraseña.
2. En el inicio, abre **Mis datos**. Ahí puede volver a leer la autorización que dio.
3. Lee qué se borra y qué se conserva, y escribe su contraseña.
4. Toca **Eliminar mis datos** y confirma con **Sí, eliminar**.
5. Aparece *"Tus datos personales se eliminaron y tu cuenta se cerró."*. Con **Entendido** vuelve al inicio de sesión.

| Situación | Qué ve | Qué pasa con sus datos |
|---|---|---|
| No escribe la contraseña | "Escribe tu contraseña para confirmar." | Nada |
| Contraseña incorrecta | "La contraseña no es correcta." | Nada; la sesión sigue abierta |
| Cinco contraseñas incorrectas | Cuenta bloqueada 15 minutos | Nada |
| Olvidó la contraseña | La recupera con el código enviado a su correo (RF-65) y repite el proceso | Nada, hasta que confirme |
| Se queda sin conexión | "Sin conexión a Internet. Por favor, verifica tu red." | Puede repetir. Si ya se habían eliminado, la app vuelve al inicio de sesión |

## 3. Qué se elimina y qué se conserva

| Dato | Sin compras ni canjes | Con compras o canjes |
|---|---|---|
| Nombre, apellido, documento y celular | Se borran | Se reemplazan por valores sin identidad (`[DATOS ELIMINADOS]`, `ELIM-<id>`…) |
| Puntos | Se borran con el registro | El cliente los pierde; el número queda en el registro anónimo |
| Correo y nombre de la cuenta | Se reemplazan por valores sin identidad | Igual |
| Contraseña (su hash) | Se reemplaza por el de un valor que nadie conoce | Igual |
| Notificaciones, dispositivos y códigos de recuperación | Se borran | Igual |
| Comandas y canjes | No hay | **Se conservan**, ligados al registro anónimo (obligaciones comerciales y tributarias) |
| Constancia de la autorización y de la supresión | **Se conserva**, sin datos personales | Igual |

La cuenta queda desactivada y anónima porque la auditoría la referencia. El token deja de servir en el acto. La persona puede registrarse otra vez con el mismo documento y correo: empieza como cliente nuevo, con 0 puntos.

## 4. Cómo funciona

`POST /api/clientes/yo/eliminacion`, solo con la sesión del cliente y su contraseña. `ClienteService.eliminarMisDatos` hace todo en una transacción: o se completa, o no cambia nada.

1. Confirma la contraseña. Los fallos cuentan como los del inicio de sesión (RNF-26).
2. Borra notificaciones, dispositivos y códigos de recuperación.
3. Desactiva y anonimiza la cuenta.
4. Si hay comandas o canjes, anonimiza el registro del cliente y revoca la autorización; si no, lo borra.
5. Registra `SUPRESION_DATOS_LEY_1581` en la auditoría: cliente (id), cuenta ya anónima, fecha y si se eliminó o se anonimizó.

**Demostrar el cumplimiento:** `GET /api/clientes/{id}/consentimiento` (solo administrador) muestra cuándo se otorgó la autorización, qué versión del texto y cuándo se suprimieron los datos, aunque el cliente ya no exista. No trae datos personales. El `id` lo da la búsqueda por documento **antes** de la eliminación; después, se localiza en la auditoría por la fecha.

## 5. Solicitudes por correo

**`privacidad@appresso.com`**, asunto *"Solicitud de Supresión de Datos Personales - Ley 1581"*, es el canal para consultas y reclamos, o para quien no puede usar la app.

| Paso | Qué se hace | Plazo |
|---|---|---|
| 1. Recibir | Anotar la fecha y confirmar el recibo. Si la solicitud está incompleta, pedir lo que falta dentro de los 5 días siguientes | — |
| 2. Orientar | Indicar cómo hacerlo desde **Mis datos** y, si olvidó la contraseña, cómo recuperarla | Cuanto antes |
| 3. Si no puede usar la app | Verificar la identidad del titular y escalar al responsable (ver pendientes) | — |
| 4. Responder | Informar el resultado al titular | **15 días hábiles** desde el día siguiente al recibo |
| 5. Si no se alcanza | Avisar el motivo y la nueva fecha antes de que venza | Hasta **8 días hábiles** más |

Desde la app la supresión es inmediata, así que estos plazos solo aplican a las solicitudes por correo.

## 6. Verificación

**Backend, 22/11/2026** (Felipe Salcedo; PostgreSQL 17, datos de prueba; el método está en [`pruebas-seguridad.md`](pruebas-seguridad.md)). Se buscaron los datos del cliente en el volcado completo de la base, antes y después: sin compras, de 7 datos quedaron 0; con una comanda, de 5 quedaron 0.

| Caso | Comprobación | Resultado |
|---|---|:-:|
| D0 | El administrador ve la evidencia del consentimiento | ✅ |
| D1 | Sin contraseña no se elimina (400) | ✅ |
| D2 | Con contraseña incorrecta no se elimina (400) y la sesión sigue | ✅ |
| D3 | El administrador no puede eliminar clientes (403) | ✅ |
| D4 | Sin historial: responde 204 y el registro del cliente se borra | ✅ |
| D5 | Ningún dato personal queda en toda la base (volcado completo) | ✅ |
| D6 | Se borran sus notificaciones, dispositivos y códigos de recuperación | ✅ |
| D7 | La cuenta queda inactiva y anónima | ✅ |
| D8 | El token deja de servir (401) y no se puede volver a entrar | ✅ |
| D9 | Queda la evidencia de autorización y supresión, sin datos personales | ✅ |
| D10 | El mismo documento y correo se pueden registrar de nuevo, con 0 puntos | ✅ |
| D11 | Con historial: 204 y el cliente queda anónimo | ✅ |
| D12 | La comanda se conserva, ligada al cliente anónimo | ✅ |
| D13 | Ningún dato personal queda en toda la base (volcado completo) | ✅ |
| D14 | El personal ya no lo encuentra por su documento (404) | ✅ |
| D15 | Cinco contraseñas incorrectas bloquean la eliminación (423) | ✅ |
| D16 | Ningún dato personal eliminado aparece en el registro del backend | ✅ |

Automatizadas: `ClienteSupresionTest` y `ClienteSupresionBloqueoTest` (backend); `MisDatosRepositoryRemotoTest` y `MisDatosViewModelTest` (app).

**En el teléfono, por hacer** (con un cliente de prueba que se pueda perder):

| # | Pasos | Esperado | ✅/❌ |
|---|---|---|:-:|
| 1 | Registrar un cliente y abrir **Mis datos** | Se lee la autorización completa y qué se elimina | |
| 2 | Tocar **Eliminar mis datos** sin contraseña | "Escribe tu contraseña para confirmar." | |
| 3 | Con una contraseña incorrecta, confirmar | "La contraseña no es correcta." y la sesión sigue | |
| 4 | Con la correcta, tocar **Cancelar** en el diálogo | No pasa nada | |
| 5 | Sin conexión (modo avión), confirmar | Aviso de sin conexión; no se cierra la app | |
| 6 | Con la correcta, **Sí, eliminar** | Mensaje de éxito y, con **Entendido**, inicio de sesión | |
| 7 | Intentar entrar con ese documento | "Credenciales inválidas" | |
| 8 | Como mesero, buscar ese documento | "Cliente no encontrado" | |
| 9 | Entrar como administrador, mesero, cajero y cocinero | Ninguno ve **Mis datos** ni puede eliminar clientes | |

**Dispositivo:** ____ (Android __) · **Fecha:** ____ · **Capturas:** ____

## 7. Pendientes por definir

| # | Pendiente | Quién decide |
|---|---|---|
| 1 | Confirmar que `privacidad@appresso.com` existe y quién lo atiende | Cafetería CaffeBar |
| 2 | Cómo se atiende a quien no puede entrar a la app ni a su correo: hoy el sistema no tiene otra vía para suprimir sus datos | Equipo y cafetería |
| 3 | Agregar al texto de autorización la dirección y el teléfono del responsable (Ley 1581, art. 12). Al cambiarlo, subir `VERSION_TEXTO_AUTORIZACION` | Cafetería CaffeBar |
| 4 | Cuánto tiempo se guardan las copias de seguridad: la supresión no las modifica, y los datos salen de ellas cuando vencen | 8.5.1 |
| 5 | Revisión del texto y de este procedimiento por alguien con criterio legal | Cafetería CaffeBar |
