# Documentación Backend

- API (BE): "`api.optima.com`"

## Definición de los flujos de la aplicación
### 1. Endpoints Registro de Organización y Usuario

`GET /auth/validation/email/{email}`

Este endpoint es utilizado por el Frontend durante el proceso de registro para validar de forma asíncrona si un correo electrónico ya está registrado en la plataforma.

---

#### Parámetros de Entrada (Path Parameters)

| Parámetro | Tipo | Obligatorio | Descripción |
| :--- | :--- | :--- | :--- |
| `email` | `string` | Sí | El correo electrónico que se desea comprobar. |

---

#### Lógica de Negocio
1. **Saneamiento:** El backend debe normalizar el email (convertir a minúsculas y eliminar espacios en blanco).
2. **Validación de Formato:** Antes de consultar la base de datos, verificar si el string tiene un formato de email válido.
3. **Consulta de Existencia:**
   - Buscar en la tabla `users` si existe algún registro con ese email.
4. **Respuesta:** Retornar un booleano o un mensaje de estado indicando si el email está "disponible" para un nuevo registro o "ocupado".

---

#### Respuestas (Responses)

**Éxito: Email Disponible (200 OK)**

El email no existe en la base de datos y puede ser utilizado.

```json
{
  "available": true,
  "message": "El correo electrónico está disponible."
}
```

**Conflicto: Email ya Registrado (409 Conflict)**

El email ya pertenece a un usuario existente.

```json
{
  "available": false,
  "message": "El correo electrónico ya se encuentra registrado."
}
```

**Error: Formato Inválido (422 Unprocessable Entity)**

El string enviado no cumple con los requisitos de un correo electrónico.

```json
{
  "status": "error",
  "message": "El formato del correo electrónico no es válido."
}
```

----

`GET /auth/validation/organization/{organization_name}`

Este endpoint es utilizado por el Frontend durante el proceso de registro para validar de forma asíncrona si el nombre de una organización (y el slug derivado) ya está registrado en la plataforma.

#### Parámetros de Entrada (Path Parameters)

| Parámetro | Tipo | Obligatorio | Descripción |
| :--- | :--- | :--- | :--- |
| `organization_name` | `string` | Sí | El nombre comercial de la organización a comprobar. |

---

#### Lógica de Negocio
1. **Normalización:** El backend recibe el nombre y elimina espacios innecesarios.
2. **Generación de Slug:** Se genera un *slug* a partir del nombre (ej: "Mi Empresa ABC" $\rightarrow$ `mi-empresa-abc`).
3. **Consulta de Existencia:**
   - Se busca en la tabla `organizations` si existe algún registro cuyo `slug` coincida con el generado.
   - (Opcional) Se puede verificar también la coincidencia exacta del nombre para evitar confusiones.
4. **Respuesta:** Retornar el estado de disponibilidad y, preferiblemente, el slug que se ha generado para que el frontend pueda mostrarlo.

---

#### Respuestas (Responses)

**Éxito: Organización Disponible (200 OK)**

Ni el nombre ni el slug resultante existen en la base de datos.

```json
{
  "available": true,
  "slug": "nombre-de-la-organizacion",
  "message": "El nombre de la organización está disponible."
}
```

**Conflicto: Organización ya Registrada (409 Conflict)**

El nombre o el slug ya están siendo utilizados por otra cuenta.

```json
{
  "available": false,
  "message": "Este nombre de organización ya no está disponible."
}
```

**Error: Formato Inválido (422 Unprocessable Entity)**

El nombre enviado contiene caracteres no permitidos o es demasiado corto/largo.

```json
{
  "status": "error",
  "message": "El nombre de la organización no cumple con los requisitos mínimos."
}
```

**Notas de Implementación**

- **Slugificación**: El backend debe usar una librería o función estándar para asegurar que caracteres especiales (tildes, ñ, etc.) se conviertan correctamente a caracteres URL-friendly.

- **UX**: Al devolver el slug en la respuesta exitosa, el frontend puede mostrar una vista previa de cómo quedará la URL del cliente (ej: optima.com/mi-empresa).

---

`POST /auth/register`

Este endpoint procesa el formulario de registro inicial. Crea de forma atómica una nueva organización y el usuario administrador (OWNER) asociado a ella.

---

#### Parámetros de Entrada (Body JSON)

| Campo | Tipo | Obligatorio | Descripción |
| :--- | :--- | :--- | :--- |
| `user_name` | `string` | Sí | Nombre completo del usuario. |
| `email` | `string` | Sí | Email del usuario (se usará para login). |
| `password` | `string` | Sí | Contraseña de acceso. |
| `password_confirmation` | `string` | Sí | Debe coincidir exactamente con `password`. |
| `organization_name` | `string` | Sí | Nombre comercial de la organización. |

---

#### Lógica de Negocio (Backend)

1.  **Validación de Integridad:** - Comprobar que `password` y `password_confirmation` sean idénticos.
    - Validar formato de email y fortaleza de contraseña.
2.  **Verificación de Disponibilidad:** Re-comprobar que el `email` y el `organization_name` (slug) no existan ya.
3.  **Transacción de Base de Datos (Atomicidad):**
    - **Paso 1:** Crear registro en tabla `organizations` generando el `slug` automáticamente.
    - **Paso 2:** Crear registro en tabla `users` con el `organization_id` recién creado y el password hasheado.
    - **Paso 3:** Asignar el rol `OWNER` al usuario en la tabla de permisos.
    - **Paso 4:** Generar y guardar el hash/token de validación único para el email.
4.  **Proceso de Email:** Disparar de forma asíncrona el envío del correo de bienvenida con el enlace de validación.

---

#### Respuestas (Responses)

**Éxito: Registro Completado (201 Created)**

La organización y el usuario han sido creados correctamente.

```json
{
  "status": "success",
  "message": "Organización registrada correctamente. Por favor, verifica tu email.",
  "data": {
    "organization_slug": "nombre-de-la-organizacion",
    "email": "usuario@ejemplo.com"
  }
}
```
**Error: Datos Inválidos o Duplicados (400 Bad Request)**

Cuando las validaciones fallan (ej: las contraseñas no coinciden o el email ya existe).

```json
{
  "status": "error",
  "errors": {
    "email": ["El correo electrónico ya está en uso."],
    "password": ["Las contraseñas no coinciden."]
  }
}
```

**Error: Error Interno del Servidor (500 Internal Server Error)**

Si falla la transacción en la base de datos o el servicio de correo.

```json
{
  "status": "error",
  "message": "No se pudo completar el registro en este momento. Inténtelo más tarde."
}
```