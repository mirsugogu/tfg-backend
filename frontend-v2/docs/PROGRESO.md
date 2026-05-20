# Frontend-v2 — Optima — 14 pasos hechos + pulido visual EN CURSO

> Frontend del TFG **Optima** (SaaS multi-tenant de gestión de citas).
> Plan de 14 pasos terminado; ahora en **fase de pulido visual**.
> **Última actualización: 2026-05-20.**

---

## 1. Qué es

Frontend construido desde cero en `C:\Users\diego\Documents\tfg-backend\frontend-v2`
contra el backend Spring Boot del mismo repo. La carpeta `frontend/` original
quedó descartada.

**Stack:** React 19 + Vite 8 + Tailwind 4 + axios + React Router 7.

---

## 2. Cómo arrancar

**Backend** (Spring Boot + MySQL en Docker), desde la raíz del repo:
```
docker compose up -d        # ~50 s hasta que responde en :8080
```

**Frontend:**
```
npm --prefix frontend-v2 run dev     # dev server en http://localhost:5173
npm --prefix frontend-v2 run build   # build de producción
```

**Login demo:** `admin@optima.com` / `12345678` (ADMIN, negocio id=1).

---

## 3. Plan de 14 pasos — todos completados

| Paso | Pantalla / tarea | Estado |
|---|---|---|
| 0 | Setup (Vite + Tailwind + .env) | ✅ |
| 1 | Lectura backend + frontend base | ✅ |
| 2 | Cliente HTTP robusto (`api.js`) | ✅ |
| 3 | Login multi-membership | ✅ |
| 4 | Forgot / Reset password | ✅ |
| 5 | Dashboard | ✅ |
| 6 | Clientes | ✅ |
| 7 | Empleados (+ horarios + ausencias) | ✅ |
| 8 | Catálogo (categorías + servicios) | ✅ |
| 9 | Citas (asistente 3 pasos) | ✅ |
| 10 | Configuración (5 pestañas) | ✅ |
| 11 | Perfil `/me` | ✅ |
| 12 | Catálogos dinámicos (CatalogContext) | ✅ |
| 13 | Calendario (Mes / Semana / Día) | ✅ |
| 14 | Pulido visual final (primera pasada) | ✅ |

---

## 4. Fase de pulido visual (post-14 pasos) — EN CURSO

Tras terminar los 14 pasos, el usuario detectó que la UI "se veía fatal". Se
abrió una fase de pulido visual:

**Hecho:**
- **Causa raíz del problema visual**: `src/index.css` tenía un reset CSS sin
  `@layer` (`*, *::before, *::after { margin:0; padding:0 }`) que en Tailwind 4
  pisaba TODO el spacing de las utilidades. Eliminado — arregló el aspecto de
  toda la app de golpe.
- **Login** rediseñado: tarjeta blanca, panel de marca azul, responsive,
  componente `LogoMark`.
- **Páginas de auth** rediseñadas igual: `Register`, `ForgotPassword`,
  `ResetPassword`.
- **4 features nuevas** (cierran huecos de cobertura de endpoints):
  - Registro de negocio público — ruta `/register` (`POST /api/auth/register`).
  - Cambiar de negocio — `BusinessSwitcher` en el sidebar
    (`GET /api/me/businesses` + `select-business`).
  - Zona de peligro — desactivar el negocio.
  - Arreglo del conteo del Dashboard.
- **Mapa de ubicación** en el registro: `LocationMap` (Leaflet + OpenStreetMap)
  con geocodificación Nominatim de la dirección escrita.

**PENDIENTE:**
- **Sidebar colapsable / responsive móvil — IMPORTANTE.** El sidebar debe
  poder ocultarse. Ahora en viewport móvil solo se ve el sidebar y tapa el
  contenido de la página. Hacerlo plegable (botón hamburguesa + overlay).
- **Pulido visual del resto de pantallas**: Dashboard, Clientes, Empleados,
  Catálogo, Citas, Calendario, Configuración, Perfil. Funcionan y se
  verificaron por build, pero falta llevarlas al nivel visual de Login.

---

## 5. Mapa de la aplicación

**Rutas** (`src/App.jsx`):
- `/login`, `/register`, `/forgot-password`, `/reset-password` — públicas.
- Bajo `AppLayout` (con Sidebar): `/dashboard`, `/clientes`, `/empleados`,
  `/catalogo`, `/citas`, `/calendario`, `/configuracion`, `/perfil`.

**Contextos** (`src/context/`):
- `AuthContext` — login 2 pasos, `register()`, `switchBusiness()`, token,
  `applyProfile()`.
- `CatalogContext` — carga `/api/roles` y `/api/appointment-statuses` al
  arrancar; expone `roles`, `statuses`, `roleLabel`, `statusLabel`.

**Piezas reutilizables**:
- `hooks/usePagedFetch.js` — consumo de endpoints `Page<T>`.
- `lib/api.js` — axios + interceptores (401/403/429) + `getErrorMessage`.
- `lib/format.js` — helpers de fecha/precio.
- `components/ui/` — Button, Input, Select, Textarea, Modal, Badge,
  Pagination, Toast, EmptyState, Card, `LogoMark`, `LocationMap`.
- `components/layout/BusinessSwitcher.jsx` — selector de negocio en el sidebar.
- `components/appointments/AppointmentWizard.jsx` — asistente "Nueva cita"
  en 3 pasos (lo usan Citas y Calendario).
- `components/appointments/AppointmentDetailModal.jsx` — detalle de cita +
  cambio de estado + pago (lo usan Citas y Calendario).

---

## 6. Reglas del proyecto

- El **código** del backend NO se toca (solo lectura). Editar archivos `.md`
  de documentación sí está permitido.
- Todo el trabajo de frontend en `frontend-v2/`.
- Idioma: español, nivel estudiante DAM.
- Se avanza pantalla por pantalla, confirmando con el usuario antes de seguir.

---

## 7. Puntos calientes del backend (a tener en cuenta en el frontend)

- Login multi-membership: `tokenType` "tenant" (1 membership) o "identity"
  (>1 → `POST /api/auth/select-business/{id}`).
- `TokenResponse` = `{ token, tokenType, businesses }`.
- `ErrorResponse` = `{ status, error, message, path?, timestamp }`.
- Paginación mixta: unos GET devuelven `Page<T>`, otros `List<T>`.
- DELETE soft idempotente: 2ª vez → 400 "ya desactivado".
- Rate limits → 429 con `Retry-After`.
- Reset de contraseña: el email del backend trae un enlace
  `/reset-password?token=...`; `ResetPassword` lo lee de la URL y además
  permite pegar el código a mano.
- Cuidado con: `serviceIds` como params repetidos (no CSV); `startDateTime`
  sin sufijo `Z`; `sort` solo por campo real.
- Race condition al crear cita: **ya cerrada en el backend** (UNIQUE a nivel
  BD, verificada empíricamente el 2026-05-20). El frontend igualmente consulta
  `/availability` antes y bloquea el botón durante la petición — ahora es
  solo UX, no una mitigación.

---

## 8. Decisiones de diseño tomadas

- Citas: en el asistente el empleado es obligatorio; las notas se piden en
  el paso 3 (confirmación).
- Citas/Calendario comparten `AppointmentWizard` y `AppointmentDetailModal`
  (sin duplicar código). El cambio de estado se hace en línea en el detalle.
- El catálogo de estados se reordena al ciclo de vida de la cita (el
  backend los devuelve alfabéticos).
- Configuración: el `slug` del negocio es de solo lectura (inmutable).
- Estados vacíos con CTA (`EmptyState`) solo en "no hay nada todavía", no
  en "la búsqueda no encontró resultados".
- El registro geocodifica la dirección con Nominatim y la muestra en un
  mapa solo como vista previa; el backend la vuelve a geocodificar.

---

## 9. Cómo verificar

Cada paso del plan se validó con `vite build` (0 errores) + smoke de API con
PowerShell. El pulido visual se verifica con capturas headless (Playwright) y
repaso en el navegador (`:5173`) a distintos anchos de pantalla — incluido
**ancho móvil**, donde ahora se reproduce el bug del sidebar pendiente.
