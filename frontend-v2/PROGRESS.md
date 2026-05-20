# Frontend v2 — Progreso

> **Snapshot**: 2026-05-20, tras integrar el bundle de mejoras de UI/UX.
> **Objetivo**: SPA del SaaS Óptima, adaptada al backend real (auditado en
> `docs/audit/`).
> **Para retomarlo en otra sesión**: lee esta página entera. El backend
> está documentado en el `CLAUDE.md` de la raíz del repo y en `docs/audit/`.

---

## 1. Reglas innegociables del proyecto

1. **NO TOCAR EL BACKEND.** Solo lectura. La carpeta `frontend/` original se
   borró; todo el trabajo va en `frontend-v2/`.
2. **Avanzar pantalla por pantalla** y **parar para pedir confirmación** al
   usuario al final de cada bloque.
3. **Adaptar el frontend al backend**, no al revés. Ante una duda de
   validación o status code, mirar el `.java` correspondiente en modo
   lectura.
4. **Idioma**: español, nivel estudiante de DAM.
5. **Citar `archivo:línea`** cuando se referencie código.
6. **Sin emojis** en archivos a menos que el usuario los pida.

---

## 2. Stack

- **React 19.2** · **Vite 8.0** · **React Router 7.15** · **axios 1.16**
- **Tailwind CSS 4.3** (vía `@tailwindcss/vite`; sin reset propio — el
  preflight de Tailwind ya aplica `box-sizing` y resets de margen dentro de
  `@layer base`). DM Sans. Paleta navy `#1e3a5f` con acento cyan-blue.
- **Radix UI** (dialog, select, dropdown-menu, avatar, toast, label,
  separator, slot) para primitivas accesibles.
- **lucide-react** para iconos · **Leaflet** para el mapa del registro.
- Helpers: `clsx`, `class-variance-authority`, `tailwind-merge`.
- Alias `@` → `./src` (configurado en `vite.config.js`).
- **Fix consciente**: `vite.config.js` lleva
  `server: { host: '127.0.0.1', port: 5173, strictPort: true }` para forzar
  IPv4 (Vite por defecto solo bindea a `[::1]` en este entorno).

---

## 3. Estructura de `frontend-v2/src/`

```
src/
├── App.jsx                 # rutas: 4 públicas + 8 protegidas bajo AppLayout
├── main.jsx
├── index.css               # Tailwind + keyframes fadeIn / slideInRight
├── App.css
├── assets/                 # hero.png, react.svg, vite.svg
├── components/
│   ├── appointments/
│   │   ├── AppointmentWizard.jsx       # alta de cita por pasos + availability
│   │   └── AppointmentDetailModal.jsx  # detalle + cambio de estado / pago
│   ├── layout/
│   │   ├── AppLayout.jsx               # gate por user; Sidebar + topbar móvil
│   │   ├── Sidebar.jsx                 # nav + command palette + colapsable + drawer móvil
│   │   └── BusinessSwitcher.jsx        # cambio de negocio (multi-membership)
│   └── ui/
│       ├── Badge.jsx     Button.jsx    Card.jsx      EmptyState.jsx
│       ├── Input.jsx     Modal.jsx     Pagination.jsx
│       ├── Select.jsx    Textarea.jsx  Toast.jsx
│       ├── LocationMap.jsx             # mapa Leaflet
│       └── LogoMark.jsx
├── context/
│   ├── AuthContext.jsx     # login 2 pasos + selectBusiness + enrichWithMe
│   └── CatalogContext.jsx  # carga /api/roles + /api/appointment-statuses
├── hooks/
│   └── usePagedFetch.js    # listados paginados sobre Page<T> de Spring
├── lib/
│   ├── api.js              # axios + interceptor 401/403/429 + getErrorMessage
│   ├── format.js           # toHms, hhmm, formatDateTime, formatDateLong, totalBooked
│   └── utils.js
└── pages/
    ├── Login.jsx  Register.jsx  ForgotPassword.jsx  ResetPassword.jsx  (públicas)
    └── Dashboard.jsx  Clientes.jsx  Empleados.jsx  Catalogo.jsx
        Citas.jsx  Calendario.jsx  Configuracion.jsx  Perfil.jsx       (protegidas)

public/                     # favicon.svg, icons.svg + logos de marca (5 SVG)
```

---

## 4. Recorrido: los 14 PASOS (todos cerrados)

El plan original tenía 14 pasos. **Todos cerrados.** Lo que sigue **vigente**
de la infraestructura construida en ellos:

- **PASO 2 — cliente HTTP** (`lib/api.js`): `baseURL` desde `VITE_API_URL`;
  interceptor que ante **401** limpia storage y redirige a `/login` (salvo
  en `PUBLIC_PATHS = ['/login', '/forgot-password', '/reset-password']`),
  marca **403** (`error.isForbidden`) y **429** (`error.isRateLimited` +
  `error.retryAfter`); `getErrorMessage(error, fallback)` lee el `message`
  en español que ya da el backend.
- **PASO 2 — `AuthContext`**: login en 2 pasos (`POST /api/auth/token` → si
  `tokenType="identity"`, `selectBusiness`). `enrichWithMe` mezcla
  `GET /api/me` sobre los claims del JWT. El tenant token y el perfil van a
  `localStorage`; el identity token (efímero) a `sessionStorage`.
- **PASO 6 — paginación**: `hooks/usePagedFetch.js` (→ `{ items, page, size,
  totalPages, totalElements, loading, setPage, refresh }`, acepta `params`
  de filtro y resetea a page 0 al cambiarlos) + `components/ui/Pagination.jsx`.
  Mapea el `Page<T>` de Spring.
- **PASO 12 — `CatalogContext`**: carga `/api/roles` y
  `/api/appointment-statuses` una sola vez al arrancar la app; expone
  `statusLabel` / `roleLabel` (traducción ES centralizada de los catálogos
  que el backend devuelve en inglés).

Las **páginas** se construyeron paso a paso (Login y auth en pasos 3-4,
Dashboard 5, Clientes 6, Empleados 7, Catálogo 8, Citas 9, Configuración 10,
Perfil 11, Calendario 13) más una **fase de pulido visual** (paso 14 — causa
raíz detectada: un reset CSS sin `@layer` que pisaba el spacing de Tailwind
4). Todo ese código de páginas quedó **superseded por el bundle de mejoras
del 2026-05-20** (ver §5): el contenido actual de las 12 páginas es la
versión del bundle.

---

## 5. Bundle de mejoras de UI/UX (2026-05-20)

Drop-in que reemplazó 12 páginas + 2 componentes de layout por versiones
mejoradas y añadió 5 logos de marca. **Cero cambios de backend** — solo
consume endpoints ya existentes (verificado contra los controllers).

**Origen**: carpeta externa `C:\Users\diego\Downloads\optima final\
implementation\` — 14 `.jsx`, `assets/` con 5 `.svg`, y 3 docs
(`README.md`, `IMPLEMENTATION_PROMPT.md`, `BACKEND_REQUIRED.md`). Esos 3
docs son meta-documentación del bundle y **por diseño no se copian al
repo**.

**Lo que entró** — los 5 pasos del `IMPLEMENTATION_PROMPT.md`:

1. 12 páginas → `src/pages/` (Dashboard, Clientes, Empleados, Catalogo,
   Citas, Calendario, Configuracion, Perfil, Login, Register,
   ForgotPassword, ResetPassword).
2. 2 componentes → `src/components/layout/` (Sidebar, BusinessSwitcher).
3. 5 logos → `public/` (logo, logo-white, logo-horizontal,
   logo-horizontal-white, logo-wordmark).
4. Keyframes `fadeIn` y `slideInRight` al final de `src/index.css`
   (animación de entrada de los drawers laterales).
5. Verificación de build.

**Qué aportan las versiones nuevas** (sin tocar el contrato de la API):

- **Drawers laterales** estandarizados en Clientes, Empleados, Catálogo,
  Citas y Perfil.
- **Filtros, orden y paginación** con tamaño de página configurable;
  preferencias **persistidas en `localStorage`** (~30 claves `optima_*`).
- **Command palette** (`Ctrl/⌘+K`) y **atajos de teclado** (`1`-`7`,
  `Ctrl/⌘+B`, `?`, `Esc`) en el Sidebar; **modo colapsado** (72 px solo
  iconos).
- Calendario con filtros, "color por", línea "ahora", rango horario
  dinámico desde `business_hours`, atajos y print.
- KPIs reales en el Dashboard y en el drawer de Empleados; stepper de 3
  pasos en Register; strength meter de contraseña en las pantallas de auth
  y en Perfil.

**Verificado en esta sesión (2026-05-20):**

- Los 19 archivos copiables (14 `.jsx` + 5 `.svg`) son **byte-idénticos**
  al bundle de origen.
- Los keyframes `fadeIn` / `slideInRight` están en `index.css` (líneas
  60-68).
- `npm run build` → **verde**: 1827 módulos, 0 errores. Único warning no
  bloqueante: chunk JS > 500 kB.

---

## 6. Estado actual y QA

**Hecho**: los 14 pasos del plan + el bundle de UI/UX + el drawer móvil del
Sidebar + una QA completa + la feature Archivar/Reactivar en los 6 recursos
(2026-05-20). `vite build` verde.

### QA del 2026-05-20

Pruebas con el backend levantado: smoke test de la API (~25 llamadas — auth,
CRUDs, casos de error) y recorrido con Playwright de las 12 pantallas y sus
controles (drawers, modales, filtros, CRUD real, command palette, calendario,
drawer móvil). La app funciona; la QA encontró **4 bugs, todos corregidos**:

- **Empleados estaba rota** (frontend): ordenaba por `fullName`, campo que
  el endpoint de usuarios (pagina `Membership`) rechaza con 400 — la lista
  salía vacía con un toast de error. Arreglado: ordena por el path anidado
  `user.fullName` (`Empleados.jsx`).
- **Warning de React `key`** (frontend): el `Stepper` de Register devolvía
  un fragment corto `<>` (que no admite `key`). Arreglado con
  `<Fragment key=...>` (`Register.jsx`).
- **Modales sin cierre con Escape** (frontend): los modales no respondían a
  `Esc` (los drawers sí). Arreglado: handler de teclado en `Modal.jsx`.
- **`appointments?sort=<inválido>` → 500** (backend, corregido con
  autorización explícita del usuario para este fix concreto): la query de
  citas es `@Query` JPQL; un sort sobre un campo inexistente hacía fallar la
  traducción del HQL. Arreglado: `AppointmentService.searchAppointments`
  valida el sort contra una whitelist (`id`, `startDateTime`, `endDateTime`,
  `createdAt`, `isPaid`) → 400 con mensaje claro. Complementa el fix
  `57e88a0`, que solo cubría los listados de query derivada.

### Archivar / Reactivar (2026-05-20)

Los 6 recursos con soft delete (clientes, empleados, servicios, categorías,
impuestos, cabinas) ganan poder **ver los archivados y restaurarlos** — antes
archivar era un callejón sin salida (un recurso archivado no se podía
recuperar ni recrear, chocaba con el `UNIQUE`).

- **Backend**: cada recurso expone `GET .../<recurso>?active=false` (los
  archivados) y `PATCH .../<recurso>/{id}/reactivate`. Patrón calcado de
  `BusinessController/Service.reactivate`. Sin cambios de schema.
- **Frontend**: las 6 pantallas tienen un control segmentado
  "Activos / Archivados" (mismo patrón que el filtro de rol de Empleados);
  en la vista Archivados cada ficha muestra "Restaurar" / "Reactivar" — un
  `PATCH` directo, sin modal. Nueva variante `success` (verde) en `Button`.
  Pantallas tocadas: `Empleados.jsx`, `Clientes.jsx`, `Catalogo.jsx`
  (servicios + categorías), `Configuracion.jsx` (impuestos + cabinas) y el
  componente compartido `Toolbar` (slot de filtro).
- Verificado: ciclo completo (crear → archivar → ver en Archivados →
  restaurar) en los 6 recursos, con Playwright y smoke de API.

**Pendiente**: solo el **commit** — el bundle, el drawer móvil, los 4 fixes
de la QA y la feature Archivar/Reactivar siguen sin commitear. Las 8 mejoras
de `BACKEND_REQUIRED.md` siguen siendo opcionales y no bloqueantes.

---

## 7. Convenciones aplicadas

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
{isAdmin && <Button onClick={openCreate}>…</Button>}
```

### LocalDateTime al backend

```js
// HTML datetime-local devuelve 'yyyy-MM-ddTHH:mm' -> añadir segundos.
// NUNCA añadir 'Z' (audit H.005: el backend ignora la Z silenciosamente).
const toLocalDt = (dt) => (dt.length === 16 ? dt + ':00' : dt)
```

---

## 8. Hallazgos del backend que el frontend YA esquiva

| Hallazgo | Severidad | Cómo lo esquiva el frontend |
|---|---|---|
| **Race condition CRIT** en `POST /appointments` (D.2.001/002/005) | CRIT | `AppointmentWizard` pide `GET /availability` antes del `POST` y deshabilita el botón durante la petición. El backend además ya cierra la race con UNIQUE sobre columnas virtuales (schema v20). |
| **`startDateTime` con sufijo `Z` ignorado** (H.005) | MEDIA | Las pantallas de citas envían `:00` sin `Z`, formato local. |
| **`serviceIds=,` → 500** (F.005) | MEDIA | `serviceIds` se envía siempre como array nativo, nunca CSV. |
| **Sort por campo inexistente → 500** (I.010) | ALTA | Solo se exponen campos ordenables seguros (en citas, `startDateTime`; los listados usan los sorts que Spring Pageable acepta). |
| **N+1 latente** (L.001) | ALTA | El frontend pagina (tamaño configurable) para limitar el impacto; el backend ya mitiga con `@EntityGraph`. |
| **DELETE idempotente devuelve 400** (M.010) | BAJA | `getErrorMessage` propaga el mensaje "ya está desactivado". |
| **PUT users solo cambia `roleId`** (C.10.003) | ALTA UX | El modal de Empleados solo manda `roleId`; nombre/email/teléfono se editan en Perfil. |
| **PUT absences sin `@FutureOrPresent`** (C.14.006) | INFO | El modal de editar ausencia omite `min={now}` para permitir editar ausencias pasadas. |

---

## 9. Estado del backend / DB

- **Backend** vía `docker compose up -d` en la raíz del repo.
  Containers: `mysqldb_optima` (3307→3306), `api_optima_dev` (8080).
- Schema: `docs/schema_v20.sql` (no tocar).
- Si docker se cerró entre sesiones: `docker compose up -d` y esperar ~90 s
  a que Spring arranque (`GET /api/roles` → 200).

### Credenciales demo (seed v20)

| Email | Password | Memberships en seed | Notas |
|---|---|---|---|
| `admin@optima.com` | `12345678` | B1 ADMIN (id_membership=1) | tenant directo |
| `empleado@optima.com` | `12345678` | B1 EMPLOYEE (id_membership=2) | tenant directo |
| `maria@optima.com` | `12345678` | B1 EMPLOYEE (id_membership=3) **+ B2 ADMIN (id_membership=10, TEMPORAL)** | identity flow (multi-membership) |
| `carlos@optima.com` | `12345678` | (membership desactivada en runs previos del audit) | login 401 |

> **Nota de DB**: el seed está *drifted* respecto al original (extras de
> runs de auditoría previos) pero es funcional. La membership
> `id_membership=10` (maria como ADMIN de B2) es un INSERT temporal para
> validar el flujo identity; revertir con
> `DELETE FROM memberships WHERE id_membership = 10;` cuando ya no haga falta.

---

## 10. Cómo continuar en otra sesión

### Setup mínimo

```powershell
# Verificar que el backend está levantado
docker compose -f C:\Users\diego\Documents\tfg-backend\docker-compose.yml ps
# Si no, levantarlo y esperar ~90 s
docker compose -f C:\Users\diego\Documents\tfg-backend\docker-compose.yml up -d
curl http://localhost:8080/api/roles   # debe devolver los 2 roles

# Frontend
cd C:\Users\diego\Documents\tfg-backend\frontend-v2
npm run dev         # dev server en http://127.0.0.1:5173
npm run build       # build de producción (verificación)
```

### Login en navegador

`http://127.0.0.1:5173/login` → `admin@optima.com` / `12345678` → `/dashboard`.

### Próximos frentes (ver §6)

1. Smoke runtime de las 12 pantallas con el backend levantado.
2. Responsive móvil (hamburguesa + overlay del Sidebar) — habilita la app
   Android con Capacitor.
3. Commit del bundle como punto de retorno.

### Estilo de trabajo del usuario

- Avanza **paso a paso**; párate y pide confirmación.
- Cita `archivo:línea` cuando hagas referencia a código.
- Si encuentras algo crítico que rompa el plan, **párate y avisa**.
- **No tocar el backend.** Lectura sí, edición no.
