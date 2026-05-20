# Frontend v2 — Progreso

> **Snapshot**: 2026-05-19, tras cerrar PASO 8.
> **Objetivo**: rehacer el frontend desde cero adaptándolo al backend real (auditado en `docs/audit/`).
> **Para retomarlo en otra sesión**: lee primero esta página entera y los 4 docs de `docs/audit/`.

---

## 1. Reglas innegociables del proyecto

1. **NO TOCAR EL BACKEND.** Solo lectura. La carpeta `frontend/` original se borró; todo el trabajo va en `frontend-v2/`.
2. **Avanzar pantalla por pantalla** y **parar al final de cada PASO** para pedir confirmación al usuario.
3. **Adaptar el frontend al backend**, no al revés. Si una validación o status code no está claro, mirar el `.java` correspondiente en modo lectura.
4. **Idioma**: español, nivel estudiante de DAM.
5. **Citar archivo:línea** cuando se referencie código.
6. **Sin emojis** en archivos a menos que el usuario los pida explícitamente.

---

## 2. Stack confirmado

- React 19.2.6 · Vite 8.0.12 · React Router 7.15 · axios 1.16
- Tailwind 4.3.0 · DM Sans · paleta navy `#1e3a5f` con acento cyan‑blue
- Radix UI (dialog/select/dropdown/etc.) · `lucide-react` para iconos
- Helpers: `clsx`, `class-variance-authority`, `tailwind-merge`
- Alias `@` → `./src` (configurado en `vite.config.js`)
- **Fix consciente**: `vite.config.js` lleva `server: { host: '127.0.0.1', port: 5173, strictPort: true }` para forzar IPv4 (Vite por defecto solo bindea a `[::1]` en este entorno — bug heredado de la sesión S751).

---

## 3. Estructura de `frontend-v2/src/`

```
src/
├── App.jsx                       # rutas (públicas + protegidas con AppLayout)
├── main.jsx
├── App.css
├── index.css
├── assets/
├── components/
│   ├── layout/
│   │   ├── AppLayout.jsx        # gate por user; Sidebar + Outlet
│   │   └── Sidebar.jsx          # navegación principal
│   └── ui/
│       ├── Badge.jsx
│       ├── Button.jsx
│       ├── Card.jsx
│       ├── Input.jsx
│       ├── Modal.jsx
│       ├── Pagination.jsx       # ← NUEVO PASO 6
│       ├── Select.jsx
│       ├── Textarea.jsx
│       └── Toast.jsx
├── context/
│   └── AuthContext.jsx          # login + selectBusiness + enrichWithMe
├── hooks/
│   └── usePagedFetch.js         # ← NUEVO PASO 6
├── lib/
│   ├── api.js                   # axios + interceptor 401/403/429 + getErrorMessage
│   └── utils.js
└── pages/
    ├── Login.jsx                # actualizado PASO 3, link forgot añadido en PASO 4
    ├── ForgotPassword.jsx       # ← NUEVO PASO 4
    ├── ResetPassword.jsx        # ← NUEVO PASO 4
    ├── Dashboard.jsx            # toast en error (PASO 5)
    ├── Clientes.jsx             # paginación + fullName + getErrorMessage (PASO 6)
    ├── Empleados.jsx            # paginación + roles dinámicos + PUT absences (PASO 7)
    ├── Catalogo.jsx             # 2 listas paginadas + aux dropdowns (PASO 8)
    ├── Citas.jsx                # PENDIENTE PASO 9
    └── Configuracion.jsx        # PENDIENTE PASO 10

docs/
└── visual/                      # prototipo HTML SOLO referencia visual
    ├── index.html, Login.html
    └── src/CalendarView.jsx, Modals.jsx, …
```

---

## 4. PASOs completados (0 → 8)

### PASO 0 — Setup
- Borrada `frontend/`, creada `frontend-v2/` desde `optima-frontend.zip` (con `tar --strip-components=1`).
- `frontend-v2/docs/visual/` poblada con `visual.zip`.
- `frontend-v2/.env` con `VITE_API_URL=http://localhost:8080`.
- `npm install` (ZIP traía `node_modules/`); `npm run dev` en background; fix IPv4 en `vite.config.js`.
- **Dev server**: `http://127.0.0.1:5173/`.

### PASO 1 — Auditoría cruzada
- Leídos los 4 docs de `docs/audit/` enteros (00 mapa real, 01 plan, 02 auth, 03 negocio).
- Inventario completo de las llamadas API en el frontend base.
- 3 hallazgos críticos identificados (ver §7).

### PASO 2 — Cliente HTTP robusto
- `src/lib/api.js`:
  - `baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080'`.
  - Interceptor: **401** limpia storage + redirige a `/login` (salvo en `PUBLIC_PATHS = ['/login', '/forgot-password', '/reset-password']`); **403** marca `error.isForbidden`; **429** marca `error.isRateLimited` + `error.retryAfter`.
  - Exportado `getErrorMessage(error, fallback)` — lee `response.data.message` (el backend ya da mensajes en español), maneja 429 con retryAfter y fallo de red.
- `src/context/AuthContext.jsx`:
  - Helpers `decodeJwt` / `persistUser` / `enrichWithMe`.
  - Tras tenant login y `selectBusiness`, llama a `GET /api/me` y mezcla `{ identityId, fullName, email, phone }` sobre los claims del JWT. Si `/api/me` da 401 → propaga (el interceptor desloguea). Otros errores → `console.warn` y se queda con datos mínimos.

### PASO 3 — Login + selección negocio
- `Login.jsx` usa `getErrorMessage(err)` (anti-enumeration: respeta el mismo "Credenciales incorrectas" del backend para los 4 caminos de fallo).
- `handleSelectBusiness` igual.
- Validación curl 10/10:
  - Login OK + `/api/me` enrichment funciona
  - 401 idéntico para password mal / email inexistente / cuenta inactiva
  - 400 vacíos validados
  - Identity flow con maria: 200 + `businesses[]` con 2 entradas
  - `select-business/{id}` válido y 403 si no hay membership
  - `/api/me/businesses` lista correcta
- 429 manejado vía `getErrorMessage` (el backend ya da el segundo en el mensaje).

### PASO 4 — Forgot / Reset password
- `src/pages/ForgotPassword.jsx` (nueva): card centrada, mensaje genérico anti-enumeration tras POST.
- `src/pages/ResetPassword.jsx` (nueva): lee `?token=…`; si no hay token → `<Navigate to="/forgot-password">`; doble input password + confirm + eye toggle; success screen con CTA "Ir al login".
- `App.jsx`: 2 rutas públicas nuevas fuera del `AppLayout`.
- `Login.jsx`: link "¿Olvidaste?" a la derecha del label "Contraseña".
- Validación curl 5/5: 204 anti-enumeration, 400 email mal formado, 400 token inexistente, 400 newPassword corta. Tabla `password_resets` registra solo cuando el email existía.

### PASO 5 — Dashboard
- 4 `Promise.all([clients, users, appointments, services])` con `?size=100` (dashboard = vista resumen, no navegable → `?size=100` está OK aquí).
- `.catch(() => {})` silencioso reemplazado por `toast({ type: 'error', message: getErrorMessage(err, 'Error al cargar el dashboard.') })`.
- Status names del filtro (PENDING/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/NO_SHOW) coinciden 1:1 con `statusName` del backend.
- Curl verificó 4/4 endpoints + shape `AppointmentResponse` correcta.

### PASO 6 — Clientes
- **Nuevos archivos reutilizables**:
  - `src/hooks/usePagedFetch.js` → `{ items, page, size, totalPages, totalElements, loading, setPage, refresh }`. Default size=20. Acepta `params` (filtros) y resetea page=0 al cambiarlos. Cancela responses si el componente se desmonta. Toast con `getErrorMessage` en error.
  - `src/components/ui/Pagination.jsx` → control prev/next + "Página X de Y · N registros". Auto-oculta si `totalPages ≤ 1`.
- `Clientes.jsx` reescrito:
  - `usePagedFetch('/api/businesses/{bId}/clients')` reemplaza `useState + load() + ?size=100`.
  - `form.full_name` → `form.fullName` (unificado camelCase).
  - `handleDelete` usa `getErrorMessage` → el 2º DELETE muestra "El cliente ya está desactivado" (audit M.010/G.019).
- Curl verificado: `page=0&size=2 → totalPages=4 totalElements=7`; doble DELETE → 204 luego 400 con mensaje claro.

### PASO 7 — Empleados
- `Empleados.jsx` reescrito + extraído `EmployeePanel` (componente hijo en el mismo archivo) para sub-paneles.
- `usePagedFetch('/users')` size=20 + `<Pagination>` bajo la rejilla.
- `/api/roles` cargado dinámicamente; **eliminado** el fallback `ADMIN=1, EMPLOYEE=2`. Botón "Nuevo empleado" deshabilitado si roles no carga.
- **PUT honesto con el backend**: el modal de edición SOLO envía `{ roleId }` (audit C.10.003: `UpdateUserRequest` solo expone roleId, fullName/email/phone se ignorarían). UI muestra fullName/email/phone como **disabled** con aviso "Para cambiar el nombre/email/teléfono ve a tu perfil".
- `EmployeePanel`:
  - `/schedules` direct fetch (List<>).
  - `/absences` paginado con `usePagedFetch(..., { size: 10 })` + `<Pagination>` interno.
  - **Modal "Editar ausencia" añadido** (PUT `/absences/{id}` no se usaba). En edit mode no aplica `min={now}` porque `UpdateEmployeeAbsenceRequest` no lleva `@FutureOrPresent` (audit C.14.006).
- `startDateTime` con sufijo `:00` añadido pero **sin `Z`** (audit H.005: el backend ignora silenciosamente la Z).
- Curl verificado: pagination, PUT roleId-only, PUT absence pasada con nuevo reason → 200.

### PASO 8 — Catálogo
- `Catalogo.jsx` reescrito.
- 2 `usePagedFetch` (size=20) para servicios y categorías + auxiliares `aux = { categories, taxes, services }` con `?size=100` para:
  - Dropdown categoría en modal servicio
  - Dropdown impuesto en modal servicio
  - Recuento "N servicios" por categoría
- `bumpAux()` invocado tras cualquier CRUD → dropdowns y count siempre frescos.
- Permisos: TODOS los botones de mutación gateados por `isAdmin && …`. Audit verificado:
  - EMPLOYEE POST → 403 "No tienes permisos suficientes para esta operacion"
  - EMPLOYEE GET → 200 (lectura abierta)
- `<Pagination>` por tab, oculto si `totalPages <= 1`.

---

## 5. PASOs pendientes (9 → 14)

### PASO 9 — Citas (CRÍTICO)
- Refactor del modal "Nueva cita" en 3 pasos:
  1. Datos básicos (cliente, empleado, servicios, fecha, cabina opcional).
  2. **`GET /api/businesses/{bId}/availability?date=&serviceIds=&membershipId=&boothId=`** → mostrar slots como botones-pastilla. Mitiga la race condition CRIT del backend (D.2.001/002/005).
  3. Confirmación + `POST /appointments` (botón deshabilitado durante la petición).
- `startDateTime` se envía SIN `Z`, formato local `yyyy-MM-ddTHH:mm:ss`.
- `serviceIds` como array nativo (NUNCA CSV — F.005).
- Filtros backend-side en la query del GET: `from`, `to`, `membershipId`.
- State machine `VALID_TRANSITIONS` coincidiendo con la del backend (PENDING→{CONFIRMED,CANCELLED}; CONFIRMED→{IN_PROGRESS,CANCELLED,NO_SHOW}; IN_PROGRESS→{COMPLETED,CANCELLED}; finales: COMPLETED, CANCELLED, NO_SHOW).
- PATCH `/appointments/{id}/status` y `/payment` con getErrorMessage.

### PASO 10 — Configuración
- Pestañas: Negocio (`PUT /businesses/{id}`), Taxes, Hours, Booths, Schedule-blocks.
- Hours: `List<>` plano (NO paginar — audit confirma sin Pageable).
- Resto: paginación real con `usePagedFetch`.
- Schedule-blocks: no hay PUT en el backend, solo POST/DELETE.

### PASO 11 — Perfil `/me`
- Nueva ruta `/perfil`.
- `GET /api/me` (ya disponible en `user`); `PUT /api/me` (fullName, email, phone); `PUT /api/me/password`.
- Enlace en `Sidebar.jsx`.

### PASO 12 — Catálogos dinámicos
- Crear `CatalogContext` que cargue `/api/appointment-statuses` y `/api/roles` al montar la app.
- Refactor `Citas.jsx`: sustituir `LABEL_ES` hardcoded por el contexto.
- Refactor `Empleados.jsx`: sustituir el array `roles` local por el contexto.

### PASO 13 — Calendario
- Nueva ruta `/calendario`.
- Tomar `docs/visual/src/CalendarView.jsx` como **referencia de diseño** (vistas Mes/Semana/Día).
- Adaptar a stack real (ES modules, no `window.X`).
- Datos reales: `GET /api/businesses/{bId}/appointments?from=&to=`.
- Click en cita → modal detalle. Click en hueco → modal "Nueva cita" pre-rellenado.

### PASO 14 — Pulido visual final
- Repaso estilístico de todas las vistas.
- Skeletons en loading, estados vacíos con CTA.
- Resumen final.

---

## 6. Convenciones aplicadas

### Imports estándar de página

```jsx
import { useState, useEffect, ... } from 'react'
import { /* icons */ } from 'lucide-react'
import { Button, Input, Select, Textarea, Modal } from '@/components/ui/...'
import { Pagination } from '@/components/ui/Pagination'
import { useAuth } from '@/context/AuthContext'
import { useToast } from '@/components/ui/Toast'
import { usePagedFetch } from '@/hooks/usePagedFetch'
import api, { getErrorMessage } from '@/lib/api'
```

### Patrón de listado paginado

```jsx
const { items, page, totalPages, totalElements, loading, setPage, refresh } =
  usePagedFetch(bId ? `/api/businesses/${bId}/<recurso>` : null)
```

### Patrón de catch

```jsx
} catch (err) {
  toast({ type: 'error', message: getErrorMessage(err, 'Mensaje fallback en castellano.') })
}
```

### Permisos

```jsx
const isAdmin = user?.role === 'ADMIN'
// ...
{isAdmin && <Button onClick={openCreate}>…</Button>}
```

### LocalDateTime al backend

```js
// HTML datetime-local devuelve 'yyyy-MM-ddTHH:mm' -> añadir segundos.
// NUNCA añadir 'Z' (audit H.005: el backend ignora la Z silenciosamente).
const toLocalDt = (dt) => (dt.length === 16 ? dt + ':00' : dt)
```

---

## 7. Hallazgos del backend que el frontend YA esquiva

| Hallazgo | Severidad | Cómo lo esquiva el frontend |
|---|---|---|
| **Race condition CRIT** en `POST /appointments` (D.2.001/002/005) | CRIT | PENDIENTE PASO 9: `GET /availability` antes de POST + botón disable durante request |
| **`startDateTime` con sufijo `Z` ignorado** (H.005) | MEDIA | Ya: `Empleados.jsx` envía `:00` sin `Z`. Aplicar también en PASO 9. |
| **`serviceIds=,` → 500** (F.005) | MEDIA | PENDIENTE PASO 9: enviar siempre array nativo, nunca CSV |
| **Sort por campo inexistente → 500** (I.010) | ALTA | Whitelist de campos ordenables; PASO 9/10 si se exponen sort UI |
| **N+1 latente** (L.001) | ALTA | Aceptado del lado backend; frontend pagina (size=20) para limitar el impacto |
| **DELETE idempotente devuelve 400** (M.010) | BAJA | Ya: `getErrorMessage` propaga el mensaje "ya está desactivado" |
| **PUT users solo cambia roleId** (C.10.003) | ALTA UX | Ya: edit modal desactiva los demás campos y avisa al usuario |
| **PUT absences sin @FutureOrPresent** (C.14.006) | INFO | Ya: edit modal omite `min={now}` para permitir editar pasadas |

---

## 8. Estado del backend / DB

- **Backend levantado** vía `docker compose up -d` en `C:\Users\diego\Documents\tfg-backend\`.
- Containers: `mysqldb_optima` (port 3307→3306), `api_optima_dev` (port 8080).
- Schema: `docs/schema_v20.sql` (no tocar).
- Si se cierra docker entre sesiones: `docker compose up -d` y esperar ~90s a que Spring arranque (`GET /api/roles` → 200).

### Credenciales demo (seed v20)

| Email | Password | Memberships en seed | Notas |
|---|---|---|---|
| `admin@optima.com` | `12345678` | B1 ADMIN (id_membership=1) | tenant directo |
| `empleado@optima.com` | `12345678` | B1 EMPLOYEE (id_membership=2) | tenant directo |
| `maria@optima.com` | `12345678` | B1 EMPLOYEE (id_membership=3) **+ B2 ADMIN (id_membership=10, TEMPORAL)** | identity flow (multi-membership) |
| `carlos@optima.com` | `12345678` | (membership desactivada en runs previos del audit) | login 401 |

### ⚠️ Pendiente de limpieza

**INSERT temporal hecho en PASO 3** (autorizado por el usuario para validar el flujo identity):
```sql
INSERT INTO memberships (id_user, id_business, id_role, is_active) VALUES (3, 2, 1, TRUE);
-- → id_membership=10 (maria como ADMIN de B2)
```
**Cuando el usuario pida revertir**:
```sql
DELETE FROM memberships WHERE id_membership = 10;
```
(idéntico a la limpieza §9 del `03_RESULTADOS_NEGOCIO.md` del audit).

**Otras modificaciones DB hechas durante las pruebas** (todas ya revertidas):
- PASO 6: `DELETE clients/9` y `UPDATE clients SET is_active=1 WHERE id_client=9` → cliente restaurado.
- PASO 7: PUT `users/2` roleId 2→1→2 → empleado vuelto a EMPLOYEE.
- PASO 7: PUT `absences/1` reason editado y restaurado a "Cita médica".

El seed actual está drifted respecto al original (extras de runs del audit previo: 7 clients, 6 services, 5 categories, etc.) pero es funcional.

---

## 9. Cómo continuar en otra sesión

### Setup mínimo

```powershell
# Verificar que backend está levantado
docker compose -f C:\Users\diego\Documents\tfg-backend\docker-compose.yml ps

# Si no, levantarlo
docker compose -f C:\Users\diego\Documents\tfg-backend\docker-compose.yml up -d

# Esperar ~90s y comprobar
curl http://localhost:8080/api/roles
# Debería devolver: [{"id":1,"name":"ADMIN"},{"id":2,"name":"EMPLOYEE"}]
```

```powershell
# Verificar que el frontend Vite sigue arrancado (PID puede haber cambiado)
curl -I http://127.0.0.1:5173/
# Si no responde, arrancar:
cd C:\Users\diego\Documents\tfg-backend\frontend-v2
npm run dev
```

### Login en navegador

`http://127.0.0.1:5173/login` → `admin@optima.com` / `12345678` → `/dashboard`.

### Lectura mínima antes de continuar

1. Este archivo (`PROGRESS.md`) entero.
2. `docs/audit/00_MAPA_REAL.md` (los 72 endpoints reales).
3. `docs/audit/03_RESULTADOS_NEGOCIO.md` §10 (bugs CRIT/ALTA a esquivar).
4. Para PASO 9: `docs/audit/00_MAPA_REAL.md` §4 (15 pasos de createAppointment) y §5 (state machine).

### Estilo de trabajo del usuario

- Avanza **paso a paso**, párate al final de cada PASO y pide confirmación.
- Si el usuario dice "confirmo" / "sigue" / "hazlo" → pasa al siguiente PASO.
- Cita `archivo:línea` cuando hagas referencia a código.
- Si encuentras algo crítico que rompa el plan, **párate y avisa** antes de continuar.
- **No tocar el backend**. Lectura sí, edición no.

---

## 10. Tareas (TaskList) — espejo en este snapshot

| # | Estado | Paso |
|---|---|---|
| 1 | completed | PASO 0 — Setup frontend-v2 |
| 2 | completed | PASO 1 — Lectura backend + frontend base |
| 3 | completed | PASO 2 — Robustecer cliente HTTP |
| 4 | completed | PASO 3 — Login + selección negocio |
| 5 | completed | PASO 4 — Forgot / Reset password |
| 6 | completed | PASO 5 — Dashboard |
| 7 | completed | PASO 6 — Clientes |
| 8 | completed | PASO 7 — Empleados |
| 9 | completed | PASO 8 — Catálogo |
| 10 | pending | PASO 9 — Citas (crítico) |
| 11 | pending | PASO 10 — Configuración |
| 12 | pending | PASO 11 — Perfil /me |
| 13 | pending | PASO 12 — Catálogos dinámicos |
| 14 | pending | PASO 13 — Calendario |
| 15 | pending | PASO 14 — Pulido visual final |

> En la siguiente sesión, recrear las tareas pendientes con TaskCreate (10–15) y marcar la primera como `in_progress` al arrancar.
