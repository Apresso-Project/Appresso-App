# API de clientes — Appresso

Contrato del registro del cliente, la búsqueda por documento y la consulta de la propia cuenta (entregable 7.1.1; RF-50, RF-51, RF-55, RF-57). El código está en `backend/src/main/java/com/appresso/backend/fidelizacion/` y las pruebas en `backend/src/test/java/com/appresso/backend/fidelizacion/ClienteControllerTest.java`.

El inicio de sesión del cliente (con su documento) y la recuperación de su contraseña están en [`api-auth.md`](api-auth.md). Qué rol puede usar cada ruta está en [`permisos-por-rol.md`](permisos-por-rol.md).

## Resumen

| Ruta | Quién | Qué hace |
|---|---|---|
| `POST /api/auth/registro` | Público (sin token) | El cliente crea su cuenta |
| `GET /api/clientes/{documento}` | Mesero y administrador | Busca un cliente por documento |
| `GET /api/clientes/yo` | Cliente | Sus propios datos y puntos |

**El personal no registra, edita ni elimina clientes.** Cada cliente crea su cuenta desde la app; el mesero y el administrador solo los buscan. No hay lista completa de clientes: se consultan de a uno, por documento (RNF-09). La eliminación de los datos personales la pide el propio cliente (entregable 8.4.1).

## Registrarse — `POST /api/auth/registro`

### Solicitud

| Campo | Tipo | Reglas |
|---|---|---|
| `nombre` | texto | Obligatorio. Máximo 80. |
| `apellido` | texto | Obligatorio. Máximo 80. |
| `documento` | texto | Obligatorio. Entre 5 y 20 letras o números, después de quitar espacios, puntos y guiones; se guarda así y en mayúsculas (`1.234.567` → `1234567`). **Único.** Con él inicia sesión. |
| `celular` | texto | Obligatorio. 7 a 20 dígitos, puede empezar con `+` y tener espacios (se guardan sin espacios). |
| `correo` | texto | Obligatorio. Formato de correo, máximo 150. **Único** entre todas las cuentas (también las del personal), sin distinguir mayúsculas. Sirve para recuperar la contraseña (RF-65). |
| `contrasena` | texto | Obligatorio. Entre 8 y 72 caracteres, y no más de 72 bytes (la ñ y las tildes ocupan 2). Se guarda con BCrypt. |
| `aceptoTratamientoDatos` | booleano | Debe ser `true` (Ley 1581 de 2012). |

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "documento": "1234567890",
  "celular": "300 123 4567",
  "correo": "ana@correo.co",
  "contrasena": "Clave-Cliente-1",
  "aceptoTratamientoDatos": true
}
```

### Respuestas

| Código | Cuándo | `mensaje` |
|---|---|---|
| **201** | Cuenta creada con 0 puntos | "Registro exitoso. ¡Ya puedes empezar a acumular tus puntos de fidelidad!" |
| **400** | Falta algún dato o no aceptó el tratamiento de datos | "Completa todos los datos y acepta el tratamiento de datos personales." |
| **400** | Todo está, pero algún dato tiene mal formato | El del primer campo con problema, en el orden del formulario. Por ejemplo "La contraseña debe tener entre 8 y 72 caracteres." o "Ingresa un correo electrónico válido." |
| **400** | JSON mal formado | "La solicitud no tiene un formato válido." |
| **409** | El documento ya está registrado | "Este documento ya está registrado." (`campos.documento`) |
| **409** | El correo ya pertenece a otra cuenta | "Este correo ya está registrado." (`campos.correo`) |
| **409** | Dos registros simultáneos con el mismo dato | "Este documento o correo ya está registrado." (sin `campos`) |

`201`:

```json
{ "mensaje": "Registro exitoso. ¡Ya puedes empezar a acumular tus puntos de fidelidad!", "nombre": "Ana", "puntos": 0 }
```

`400` con `campos` (uno por campo, para señalarlo en el formulario):

```json
{
  "mensaje": "Completa todos los datos y acepta el tratamiento de datos personales.",
  "campos": {
    "nombre": "Ingresa tu nombre.",
    "aceptoTratamientoDatos": "Debes aceptar el tratamiento de datos personales."
  }
}
```

### Qué guarda

- Una cuenta (`usuarios`) con rol `CLIENTE`, el correo y el hash de la contraseña, y su registro de cliente (`clientes`) con 0 puntos, la aceptación del tratamiento de datos y su fecha. Las dos cosas, o ninguna.
- Un registro de auditoría `CONSENTIMIENTO_DATOS_1581` que dice que el titular aceptó, con la fecha y la cuenta, **sin copiar** nombre, documento ni otros datos personales.
- El registro **no inicia sesión**: después, el cliente entra con su documento y su contraseña.

## Buscar por documento — `GET /api/clientes/{documento}`

Para el mesero y el administrador (RF-51, RF-57). El documento se normaliza igual que en el registro.

| Código | Cuándo | Cuerpo |
|---|---|---|
| **200** | El cliente existe | `{ "id", "nombre", "apellido", "documento", "puntos" }` |
| **404** | No hay cliente con ese documento | `{ "mensaje": "Cliente no encontrado" }` |
| **403** | Cajero, cocinero o cliente | `{ "mensaje": "No tienes permiso para realizar esta acción." }` |

```json
{ "id": 7, "nombre": "Ana", "apellido": "Pérez", "documento": "1234567890", "puntos": 3 }
```

Solo lo necesario para identificar al cliente y ver sus puntos: **no** incluye celular, correo ni datos del consentimiento (RNF-09). Ante un `404`, la app indica que el cliente puede crear su cuenta desde la app.

## Mi cuenta — `GET /api/clientes/yo`

Solo para el cliente (RF-55). Responde lo mismo que la búsqueda, con sus propios datos. Un `404` significa que la cuenta no tiene registro de cliente.

## Probarlo a mano

```bash
curl -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Ana","apellido":"Pérez","documento":"1234567890","celular":"3001234567","correo":"ana@correo.co","contrasena":"Clave-Cliente-1","aceptoTratamientoDatos":true}'

curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"identificador":"1234567890","contrasena":"Clave-Cliente-1"}'

# Con el token de un mesero o del administrador:
curl http://localhost:8080/api/clientes/1234567890 -H "Authorization: Bearer <token>"
```
