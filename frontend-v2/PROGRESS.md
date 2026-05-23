# Frontend v2 — Progreso

> **Snapshot vigente**: 2026-05-23, tras los bloques del backlog del tester
> y las sesiones de pulido posteriores (ver §11).
> **Snapshot anterior** (referencia para §1-§10): 2026-05-21, tras el layout
> fluido full-width y su verificación 2K/4K.
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

### Layout fluido full-width + verificación 2K/4K (2026-05-21)

Las 7 pantallas de datos (Dashboard, Clientes, Empleados, Catálogo, Citas,
Calendario, Configuración) pasaron de `max-w-7xl mx-auto` a un layout fluido
full-width (`px-4 sm:px-6 lg:px-8 xl:px-10 py-8`, sin tope de ancho). Perfil
y la pestaña Negocio de Configuración siguen acotadas (`max-w-5xl`, son
formularios y no deben estirar sus campos). Verificado con Playwright a
**390 / 1440 / 2560 / 3840 px**; la verificación encontró y corrigió **2 bugs
de layout** (solo frontend):

- **Rejillas de tarjetas clavadas en 4 columnas** por encima de 2200 px. El
  `min-[2200px]:grid-cols-5` que la versión anterior añadió no se aplicaba:
  Tailwind v4 emite las variantes custom y arbitrarias `min-[...]` **antes**
  que los breakpoints integrados, así que `2xl:grid-cols-4` ganaba siempre
  la cascada. Solución: las rejillas se definen como clases propias
  `.card-grid` y `.appt-grid` en `index.css` (`@layer components`) con media
  queries en orden ascendente — cascada correcta por construcción.
  `.card-grid` refluye **1→2→3→4→5→6** columnas (clientes, empleados,
  servicios, categorías, cabinas).
- **Lista de Citas estirada** a ~3470 px de ancho en 4K (una sola columna),
  con un hueco enorme entre los datos y el importe. Solución: `.appt-grid`,
  refluye **1→2→3** columnas en 2K/4K.

Resultado: sin overflow ni recortes a 390 px, aspecto intacto a 1440 px y, en
2K/4K, el contenido llena la pantalla con las rejillas reflu­yendo. Las 12
pantallas cargan sin errores de consola y Archivar/Reactivar sigue
funcionando (ciclo completo verificado). `vite build` verde.

### Revisión quirúrgica del proyecto (2026-05-21)

Auditoría línea a línea de backend + frontend con 8 agentes en paralelo +
pruebas dinámicas. Informe completo en **`docs/REVISION_QUIRURGICA.md`** (67
hallazgos, 0 críticos). Se **corrigieron 16 hallazgos de frontend**: 9 dependencias
fantasma eliminadas de `package.json`, `App.css` muerto borrado, parseo
defensivo del JWT/`localStorage` en `AuthContext`, IDs únicos de `Toast`,
asociación `label`↔control en `Input/Select/Textarea`, accesibilidad del
`Modal` (`role=dialog`, foco), doble-fetch de `usePagedFetch`, código muerto
de `BusinessSwitcher`, y varios bugs de páginas (entre ellos `Perfil` leía
`b.roleName` cuando la API devuelve `role`). En backend se corrigieron
después los 26 hallazgos accionables (3 ALTA + 6 MEDIA + 17 BAJA) — ver
`docs/REVISION_QUIRURGICA.md`. Verificado: `mvnw test` verde,
`vite build` verde, E2E Playwright 30/30, smoke de API OK.

**Pendiente**: el frontend (bundle, drawer móvil, QA, Archivar/Reactivar,
layout fluido y los 16 fixes de la revisión) ya está commiteado; quedan sin
trackear solo los **4 logos** de `public/`. Las 8 mejoras de
`BACKEND_REQUIRED.md` siguen siendo opcionales y no bloqueantes; los 26
hallazgos accionables de backend de la revisión quirúrgica sí se corrigieron
todos (ver `docs/REVISION_QUIRURGICA.md`).

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

---

## 11. Trabajo posterior (2026-05-22 → 2026-05-23)

El detalle granular vive en `docs/BACKLOG.md` ("Trabajo posterior al
backlog"). Esto es el resumen ejecutivo de la nueva vertical y los
puntos donde la arquitectura cambió respecto a §3-§10.

### Nueva vertical: backlog del tester
Documento `docs/BACKLOG.md` con 13 puntos de feedback de un tester. Estado
al cerrar la sesión: 11 hechos + 2 decisiones conscientes (P3, P4) + P11
sin acción (confusión del tester). Los `commits` están listados ahí; el
hash más reciente es `0d40082`.

### Cambios de contrato del backend que el frontend ahora consume

| Endpoint | Estado | Quién lo usa |
|---|---|---|
| `PUT /api/businesses/{id}/appointments/{id}` (P9) | Nuevo | `AppointmentWizard` en modo edit + drag-and-drop del calendario. |
| `GET /api/businesses/{id}/availability?excludeAppointmentId=` | Param nuevo | Wizard en modo edit (para que el slot original no salga ocupado por sí mismo). |
| `GET /api/clients?search=` | Param nuevo | `ClientPicker` (autocompletado server-side). |
| `GET /api/businesses/{id}/absences?from&to` | Nuevo | Calendario, para pintar las franjas rojas de ausencias. |
| `PUT /api/businesses/{id}/hours/{id}` multi-tramo | Cambio | `HoursTab` con turno partido. |
| `POST/PUT /api/businesses/{id}/booths` con `color` | Campo nuevo | `BoothsTab` con picker de color. |

### Componentes nuevos
- `components/calendar/drag.jsx` — hook `useDragAppointment` para el
  drag-and-drop sin librerías (umbral 5 px, ghost via createPortal,
  hit-test sobre `data-cal-cell`).
- `components/calendar/MiniCalendar.jsx` — navegación rápida por fecha.
- `components/calendar/cells.jsx::ApptHoverCard` — tarjeta flotante con
  el resumen de la cita al hacer hover.
- `components/clients/ClientPicker.jsx` — buscador server-side de
  clientes con alta al vuelo desde el wizard.
- `lib/employeeColor.js` — utilidad `dotClassFromColorAndId` que
  resuelve la paleta del empleado (color de membership o fallback por
  id).

### Calendario — reescritura defensiva
La rejilla del calendario gana cuatro capas de defensa que no estaban:

1. **`BlockOverlay`** sobre franjas/columnas dentro de un `schedule_block`,
   con click bloqueado en los slots.
2. **`AbsenceOverlay`** rojo sobre las horas de ausencia del empleado
   (clamp al alto del grid, no se sale).
3. **Wizard guidance**: si se intenta editar una cita que cae en un
   bloqueo o ausencia, toast guía + banner rosa en el detalle.
4. **Backend 409** sigue siendo la última red.

Vista Mes: heatmap por densidad de citas activas (umbrales 1/3/6),
separadores tipo Semana (border 2 px slate-300), botón "+" oculto si el
día está bloqueado.

`hourPx` adaptativo: si el horario configurado supera 14 h, 48 px/hora;
si supera 18 h, 40 px/hora. Cap defensivo `[0, 24]` en `dayStart` /
`dayEnd`. El modo compacto manual desaparece (commit `e1518ea`).

### Seguridad — revalidación de sesión por request
`TenantGuardFilter` consulta la membership viva en cada request y la
compara con los claims del JWT (rol + `is_active`); si cambió algo
desde el login, devuelve 401 limpio. Resuelve dos bugs de la sesión:
cambio de rol que no surtía efecto y sesión que persistía tras
desactivar empleado.

### Configuración / Empleados — fix de horarios
- Rango visual adaptativo en `HoursTab` y `ScheduleGrid` (los 8-22
  fijos se desbordaban con horarios extensos).
- `flatMap` + `isFirstOfDay` por item para arreglar el bug de React
  reconcile que descolocaba las etiquetas L/M/X… al borrar el primer
  tramo de un día con turno partido.
- `openRangesFor` (utilidad común del calendario) ahora devuelve N
  rangos abiertos por día (turno partido) y `HourSlots::isClosed`
  invierte la lógica para soportarlo.

### Postman
La colección `docs/optima-postman-collection-v4.json` se mantiene al día
en cada cambio del contrato HTTP. Cobertura actual: los 17 controllers
(verificado por agente externo, 99% → 100% tras añadir el grupo
`08b - business_absences`).
