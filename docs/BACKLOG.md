# Backlog — Feedback del tester (Optima Frontend)

**Origen:** documento *"Problemas encontrados en Optima Frontend.docx"* — feedback
de un tester sobre el frontend.
**Verificado contra el código:** 2026-05-22 (3 subagentes: Calendario, Horarios,
Catálogo/Citas).

Es un backlog **independiente** del *"Informe de Calidad"* anterior
(LIM-1 / LIM-2 / AUS-4), que ya quedó cerrado en el commit `38a7088`.

---

## Estado de los 13 puntos

| # | Tema | Estado |
|---|------|--------|
| P1-empleado | Turno partido — horario del empleado | ✅ Hecho — commit `45e7f18` |
| P1-negocio  | Turno partido — horario del negocio | ⬜ Pendiente |
| P2 | Copiar horario L→V para el empleado | ⬜ Pendiente |
| P3 | Nombre de servicio único por categoría (no por negocio) | ⬜ Pendiente — decisión |
| P4 | Panel de stats en Catálogo ocupa mucho | ✅ Decisión: se mantiene |
| P5 | Crear cliente al vuelo desde la nueva cita | ⬜ Pendiente |
| P6 + P7 | Filtros del calendario que esconden citas | ✅ Hecho — commit `f873b9e` |
| P8 | "Color por cabina" poco visible | ⬜ Pendiente |
| P9 | Editar / reprogramar una cita | ⬜ Pendiente — grande |
| P10 | Toggle densidad no afecta a la vista Mes | ⬜ Opcional |
| P11 | Color del selector "Agrupar" | ✅ Nada que hacer — confusión del tester |
| P12 | Las citas canceladas pierden su señal visual | ✅ Hecho — sin commit |
| P13 | Panel de stats en el Calendario satura | ✅ Decisión: se mantiene |

---

## ✅ Hecho

### P6 + P7 — Filtros del calendario que esconden citas — `f873b9e`
Los filtros de contenido (`statusFilter`, `boothFilter`, `employeeFilter`) se
persistían en `localStorage` y sobrevivían al cierre de sesión; un filtro
olvidado hacía "desaparecer" citas reales. Ahora arrancan vacíos.
`Calendario.jsx:57-62`.

### P1-empleado — Turnos partidos en el horario del empleado — `45e7f18`
`ScheduleGrid` usaba `.find()` y solo pintaba el primer tramo del día. Ahora
usa `.filter()` y renderiza una fila por tramo. `Empleados.jsx` (ScheduleGrid +
modal de eliminar).

### P11 — Color del selector "Agrupar" — sin acción
No es un bug: es el indicador estándar de pestaña activa de un segmented
control. El tester probablemente lo confunde con el selector "Color por".

### P13 — Panel de stats del Calendario — se mantiene
Decisión (2026-05-22): el panel se queda. A diferencia del Dashboard, sus 4
KPIs (`Citas`, `€ previstos`, `Ocupación %`, `En curso`) son **contextuales al
rango navegado** (la semana o el mes que se está viendo), así que aportan
información que el Dashboard no da. Defendible en la memoria del TFG.

### P4 — Strip de stats del Catálogo — se mantiene
Decisión (2026-05-22): el strip se queda, por coherencia con P13 y mismo
criterio. `Precio medio`, `Duración media` y `Más caro` son métricas que el
Dashboard no ofrece.

### P12 — Señal visual de las citas canceladas — sin commit
`styleFor` (`utils.js`) ignoraba `statusName` en los modos "Color por →
Empleado/Cabina": una cita `CANCELLED`/`NO_SHOW` tomaba el color del recurso
y se confundía con una activa. Fix en 3 puntos:
- `utils.js` `styleFor`: los estados terminales se pintan siempre apagados,
  antes de mirar `colorBy` — corrige las 5 vistas desde un solo punto.
- `cells.jsx` `PositionedEvent`: `opacity-60` si terminal (bloque sin texto).
- `cells.jsx` `EventChip` (grid + list): `line-through opacity-60` si terminal.
Build de producción verde. Pendiente: commit.

---

## ⬜ Pendiente

Ordenado por relación esfuerzo / valor.

### P3 — Nombre de servicio único por categoría  ·  ~40 min backend  ·  decisión
Hoy la unicidad del nombre es **por negocio**: no deja repetir "Cuerpo completo"
en dos categorías distintas.
- Finder: `BusinessServiceRepository.java:29` (`existsByBusinessIdAndNameIgnoreCase`).
- Se invoca en `BusinessServiceService.java:78` (crear) y `:172` (editar).
- Para permitirlo: añadir `existsByBusinessIdAndCategoryIdAndNameIgnoreCase` y
  usarlo en ambas llamadas (el editar debe re-chequear si cambia de categoría).
- **Decisión de negocio:** ¿unicidad por categoría o mantener por negocio?
- Actualizar tests + colección Postman (mensaje 409).

### P8 — "Color por cabina" poco visible  ·  ~30-60 min  ·  frontend
La función existe (selector "Color por" con modo Cabina), pero está enterrada
en el popover de Filtros.
- Selector: `Calendario.jsx:759-773`. Default `'status'` (`Calendario.jsx:54`).
- Mejora: sacar el selector "Color por" a la barra de herramientas visible.

### P2 — Copiar horario L→V para el empleado  ·  ~1-2 h  ·  frontend
El horario del **negocio** ya tiene botón "Copiar L→V"
(`Configuracion.jsx:563-586`, botón `:622`). El del **empleado** no tiene nada.
- Replicar ese patrón en la sección "Horario semanal" del drawer de empleado
  (`Empleados.jsx:867-888`): botón que parta de los tramos del lunes y lance
  los POST a `${empUrl}/schedules` para martes-viernes.
- Si hay turno partido, copiar todos los tramos del lunes, no solo uno.

### P5 — Crear cliente al vuelo desde la nueva cita  ·  ~2-3 h  ·  frontend
El paso 1 del wizard tiene un `<Select>` de solo elección.
- Selector: `AppointmentWizard.jsx:226-233`. Carga `aux.clients`: `:94`, `:99-104`.
- Añadir un botón "+ Nuevo cliente" → mini-form (3 campos) → `POST
  /api/businesses/{bId}/clients` → añadir a `aux.clients` y autoseleccionar.
- El endpoint POST de clientes ya existe. Revisar el `CreateClientRequest` por
  si valida más campos.

### P1-negocio — Horario partido del negocio  ·  ~5-7 h  ·  backend + frontend
Un negocio que cierra a mediodía no puede meter 10-14 / 16-20.
- **Backend:** la tabla `business_hours` tiene `UNIQUE (id_business,
  day_of_week)` (`schema_v20.sql`). `BusinessHourService.create` rechaza con
  `existsByBusinessIdAndDayOfWeek`.
  - Quitar el UNIQUE; sustituir el check por validación de solape (misma
    fórmula `A<D AND C<B` que ya usa `EmployeeScheduleService`).
  - Revisar que `AvailabilityService` tolera varios tramos por día.
- **Frontend:** `HoursTab` en `Configuracion.jsx` (form `emptyHour:25`, itera
  `[1..7]` con `.find` en `:638`) → mismo cambio multi-barra que P1-empleado.

### P10 — El toggle densidad no afecta a la vista Mes  ·  opcional
`density` cambia `HOUR_PX` 64↔40 px (`utils.js:16`) solo en Día/Semana. La vista
Mes usa celdas `min-h-[120px]` fijas (`Calendario.jsx:486`). Si el tester lo
probó en Mes, no vio nada. Decidir: extender a Mes, o dejarlo.

### P9 — Editar / reprogramar una cita  ·  ~1-2 días  ·  full-stack  ·  GRANDE
Hoy una cita creada solo cambia de estado y de pago. No se puede cambiar día,
hora, empleado ni servicios. El tester tiene toda la razón.
- **Backend:** solo `PATCH .../{id}/status` y `PATCH .../{id}/payment`
  (`AppointmentController.java:122`, `:138`). No hay PUT ni DELETE (lo dice el
  Javadoc, `:46-48`).
  - Nuevo endpoint + método que reaplique toda la cadena de validación de
    `createAppointment` (horario, solape, cabina, bloqueos…).
  - Cuidado con el anti-doble-reserva a nivel BD (`active_slot_key` /
    `active_booth_slot_key`) y con regenerar `appointment_services` (precios e
    IVA congelados) si cambian los servicios.
- **Frontend:** modo edición en `AppointmentDetailModal.jsx` o reutilizar
  `AppointmentWizard` en modo "editar".
- Actualizar tests + colección Postman.

---

## Notas

- VIS-4 del Informe de Calidad anterior (terminología Archivar/Desactivar) se
  decidió **dejar como está** — la distinción es defendible.
- Citas.jsx **sí** persiste sus filtros `from`/`to`/`employee` en `localStorage`,
  pero son visibles en pantalla, así que no es la misma trampa que P6/P7. Sin
  cambiar de momento.
