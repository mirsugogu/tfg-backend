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
| P1-negocio  | Turno partido — horario del negocio | ✅ Hecho — commit `16676d7` |
| P2 | Copiar horario L→V para el empleado | ✅ Hecho — commit `9728e35` |
| P3 | Nombre de servicio único por categoría (no por negocio) | ✅ Decisión: se mantiene |
| P4 | Panel de stats en Catálogo ocupa mucho | ✅ Decisión: se mantiene |
| P5 | Crear cliente al vuelo desde la nueva cita | ✅ Hecho — commit `c512729` (+ fix `659ff37`) |
| P6 + P7 | Filtros del calendario que esconden citas | ✅ Hecho — commit `f873b9e` |
| P8 | "Color por cabina" poco visible | ✅ Hecho — commit `3c1e229` |
| P9 | Editar / reprogramar una cita | ✅ Hecho — commit `fbaed96` + drag `80b84b3` |
| P10 | Toggle densidad no afecta a la vista Mes | ✅ Cerrado — commit `e1518ea` (se retiró el toggle) |
| P11 | Color del selector "Agrupar" | ✅ Nada que hacer — confusión del tester |
| P12 | Las citas canceladas pierden su señal visual | ✅ Hecho — commit `c1a3966` |
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

### P12 — Señal visual de las citas canceladas — commit `c1a3966`
`styleFor` (`utils.js`) ignoraba `statusName` en los modos "Color por →
Empleado/Cabina": una cita `CANCELLED`/`NO_SHOW` tomaba el color del recurso
y se confundía con una activa. Fix en 3 puntos:
- `utils.js` `styleFor`: los estados terminales se pintan siempre apagados,
  antes de mirar `colorBy` — corrige las 5 vistas desde un solo punto.
- `cells.jsx` `PositionedEvent`: `opacity-60` si terminal (bloque sin texto).
- `cells.jsx` `EventChip` (grid + list): `line-through opacity-60` si terminal.
Build de producción verde.

### P3 — Unicidad del nombre de servicio — se mantiene
Decisión (2026-05-22): la unicidad del nombre sigue siendo **por negocio**, no
por categoría. No es un bug — el 409 es comportamiento correcto. Cambiarlo
obligaría a tocar la capa de BD (el constraint `uq_service_business_name
UNIQUE (id_business, name)` de `schema_v20.sql`, añadido en la auditoría del
2026-05-20) con una migración manual en todos los entornos, y rompería la
simetría del patrón de unicidad que comparten servicios, categorías, cabinas e
impuestos. El riesgo/beneficio no compensa para el alcance del TFG.

### P8 — Selector "Color por" sacado a la barra visible — commit `3c1e229`
El selector "Color por" estaba dentro del popover de Filtros, mezclado con los
filtros de contenido (Empleado/Cabina/Estado) pese a ser una preferencia de
visualización, como "Agrupar" o "Densidad". Movido a la barra visible, con
etiqueta "Color", junto a "Agrupar" y visible en todas las vistas.
- `Calendario.jsx` `FiltersBar`: bloque retirado del popover y recreado en la
  barra con el patrón visual de "Agrupar".
Build de producción verde.

### P2 — Botón "Copiar L→V" en el horario del empleado — commit `9728e35`
El horario del empleado no tenía forma de replicar el lunes al resto de la
semana (el del negocio sí). Nuevo botón "Copiar L→V" en la sección "Horario
semanal" del drawer de empleado, con modal de confirmación.
- `Empleados.jsx` `EmployeeDrawer`: handler `handleCopyMonToWeek` — por cada
  día martes-viernes borra los tramos existentes y recrea los del lunes
  (DELETE antes que POST: el backend valida solapes al crear, 409). Copia los
  turnos partidos completos. Modal `scheduleModal === 'copyweek'`.
Verificado E2E con Playwright: turno partido copiado, sobrescritura correcta,
sábado intacto, guarda sin lunes. Build verde.

### P5 — Buscador de clientes + crear al vuelo en el wizard — `c512729` (+ fix `659ff37`)
El selector de cliente del wizard era un `<select>` que precargaba solo los
primeros 100 clientes; con más, el resto quedaba inseleccionable. Se sustituye
por un buscador server-side con autocompletado, e incluye crear un cliente sin
salir del asistente (P5 propiamente dicho).
- **Backend:** `GET /clients?search=` — nuevo `@Query searchActiveByBusiness`
  en `ClientRepository` (busca en nombre, email y teléfono); parámetro `search`
  opcional y retrocompatible en `ClientService` y `ClientController`.
- **Frontend:** componente nuevo `ClientPicker.jsx` (input con debounce →
  `/clients?search`, desplegable de resultados, mini-formulario para crear
  cliente al vuelo). Integrado en `AppointmentWizard`, que deja de precargar
  los 100 clientes.
Verificado E2E con Playwright: búsqueda, selección y creación al vuelo. Build
del frontend y arranque del backend verdes.

---

### P1-negocio — Horario partido del negocio — `16676d7`
Cierra la asimetría con P1-empleado. Aplicado con simetría al patrón de
`EmployeeScheduleService` (overlap `A<D AND C<B`).
- **Schema:** quitado `uq_business_hours_day` en `docs/schema_v20.sql`. La BD
  se recarga cargando el `.sql` entero (`mysql < docs/schema_v20.sql`), que
  hace `DROP DATABASE IF EXISTS` + `CREATE DATABASE` antes de las tablas y
  del seed; no se aplican `ALTER` incrementales.
- **Repository:** sustituidos `findByBusinessIdAndDayOfWeek` y
  `existsByBusinessIdAndDayOfWeek` por
  `findAllByBusinessIdAndDayOfWeekOrderByStartTimeAsc`.
- **`BusinessHourService.create`:** loop de solape sobre los tramos abiertos
  del día. Cerrados (`isClosed=true`) y nuevos cerrados se ignoran del check
  (un "marcador cerrado" no solapa). `update` sin revalidación, simetría con
  `EmployeeScheduleService.update`.
- **`AppointmentValidator.validateBusinessHours`:** la cita es válida si
  cabe íntegra dentro de **algún** tramo abierto del día.
- **`AvailabilityService`:** itera todos los tramos abiertos del día y, por
  cada (tramo-empleado × tramo-negocio), genera slots. El descanso 14-16 del
  turno partido nunca produce huecos.
- **Frontend `HoursTab`:** una fila por tramo (`filter` + `sort`, fila vacía
  "Sin configurar" si no hay tramos), "Copiar L→V" hace DELETE+POST de todos
  los tramos del lunes, "Fin de semana cerrado" hace DELETE de todo +
  POST `isClosed=true`. Mismo patrón que `Empleados.jsx`.
- **Postman:** caso "Lunes duplicado" cambia de "dos POST iguales → UNIQUE"
  a "tramo solapado → 409 por solape"; añadido caso "Lunes 19-22 turno
  partido → 201".
Tests 30/30 verdes, build frontend verde.

---

## ⬜ Pendiente

Ordenado por relación esfuerzo / valor.

### P10 — El toggle densidad no afecta a la vista Mes  ·  opcional
`density` cambia `HOUR_PX` 64↔40 px (`utils.js:16`) solo en Día/Semana. La vista
Mes usa celdas `min-h-[120px]` fijas (`Calendario.jsx:486`). Si el tester lo
probó en Mes, no vio nada. Decidir: extender a Mes, o dejarlo.

### P10 — Toggle densidad sin efecto en Mes — cerrado en `e1518ea`
El toggle de densidad solo afectaba a Día/Semana porque el Mes usa altura
fija. Decisión (2026-05-23): retirar el toggle y reemplazar el control de
densidad por dos mejoras visuales propias de cada vista:
- Día/Semana: `hourPx` adaptativo según el rango horario configurado
  (`a649e0e`) — si el horario es muy extenso, las celdas se compactan
  para que el calendario quepa en pantalla.
- Mes: heatmap por densidad de citas activas (`3ffa09c`) — las celdas con
  más citas se saturan en azul; misma intención que un toggle de
  densidad, pero contextual al contenido.

### P9 — Editar / reprogramar una cita — `fbaed96` + drag `80b84b3`
Nuevo `PUT /api/businesses/{businessId}/appointments/{id}` que reaplica
toda la cadena de validación de `createAppointment` excluyendo la propia
cita del check de solape. Una cita en COMPLETED no se reagenda (400); una
CANCELLED / NO_SHOW sí, y al guardar vuelve a PENDING (ciclo de vida nuevo).
- **Backend:**
  - `AppointmentRepository`: `+findByIdAndBusinessIdForUpdate` (lock
    pesimista) y `+existsOverlapping*Excluding` para que el solape ignore
    la propia cita en edit.
  - `BookedServiceRepository`: `+deleteAllByAppointmentId` para re-congelar
    precios al editar (decisión consciente: los precios se recongelan al
    valor actual del catálogo, no se conservan los antiguos).
  - `AppointmentValidator`: `validateNoOverlap` y `validateNoBoothOverlap`
    aceptan `excludeAppointmentId` opcional.
  - `AppointmentService`: nuevo `updateAppointment` con 16 pasos de
    validación, lock pesimista sobre la cita, reset CANCELLED/NO_SHOW →
    PENDING y borrado/recreación de `BookedService`.
  - `AppointmentController`: nuevo `PUT /{id}`.
  - `UpdateAppointmentRequest`: DTO sin `clientId` (la cita pertenece al
    cliente original) ni `@FutureOrPresent` (permite editar notas/servicios
    de citas pasadas sin reagendar).
  - `AvailabilityController` + `AvailabilityService`: `+excludeAppointmentId`
    opcional para que el wizard en modo edit no muestre el slot original
    como ocupado por sí mismo.
- **Frontend:**
  - `AppointmentDetailModal`: botón "Editar cita" condicional (solo si no
    está en COMPLETED).
  - `AppointmentWizard`: nueva prop `appointmentToEdit`; precarga el form
    desde la cita, bloquea el cliente con panel informativo (no toca el
    componente `ClientPicker`), título dinámico, slot original
    pre-seleccionado, `PUT` en lugar de `POST`.
  - `Calendario.jsx` y `Citas.jsx`: cableado del flujo detail → wizard
    edit.
- **Tests:** 33/33 verdes (+2 tests del update: COMPLETED → 400 y
  CANCELLED → PENDING).
- **Postman:** 8 casos nuevos del PUT (happy path con re-congelación 2→1
  y 1→2 servicios, validaciones, 404, 400 estado terminal).
- **Drag-and-drop** (`80b84b3`): suma una segunda forma de invocar el PUT
  desde el calendario. Hook `useDragAppointment` (sin librerías, umbral
  de 5 px, ghost via createPortal, hit-test con `document.elementFromPoint`
  sobre `data-cal-cell`/`data-day`/`data-resource-*`/`data-hour-px`/
  `data-day-start`/`data-interval`). Funciona en las cuatro vistas con
  rejilla (Día, Semana, y sus variantes por recurso); las citas en
  CANCELLED/NO_SHOW siguen siendo clickables pero no draggables.
  `ConfirmDropModal` muestra las diferencias en lenguaje natural antes
  de ejecutar el PUT. El snap usa `appointmentInterval` del negocio.

---

## ✅ Trabajo posterior al backlog (2026-05-22 → 2026-05-23)

Sesiones de pruebas con el tester que destaparon más detalles. Todos
cerrados; cada commit es atómico para poder revertirse sin romper nada.

### Críticos detectados en pruebas
- **Cambio de rol no surtía efecto hasta re-login** — `a842948`.
  `TenantGuardFilter.revalidateSession` consulta la membership viva en
  cada request y compara con los claims (rol + `is_active`); si cambió
  algo, 401 limpio para forzar re-login. Cubre también "la sesión
  persiste tras desactivar empleado".
- **Inputs numéricos aceptaban letras de notación científica** — `7869d13`.
  Los `type="number"` admitían `e`, `E`, `+`, `-`. Bloqueo en `onKeyDown`
  de los wrappers `Input.jsx`/`Textarea.jsx`.

### Bloqueos de agenda visibles en el calendario
- **Pintado en las 5 vistas** — `12a5b51` + `f01b582` + `601fa9a`.
  `BlockOverlay` semitransparente sobre las franjas/columnas
  bloqueadas; `HourSlots` lee `isBlocked` y desactiva el click. Las
  citas existentes en días bloqueados siguen clickables.
- **Aviso al editar cita en zona bloqueada** — `b9d4246`.
  Toast guía + banner rosa en el detalle. Defensa en 4 capas: overlay
  → click bloqueado → wizard guidance → backend 409.

### Ausencias visibles en el calendario
- **Franjas rojas por empleado** — `8be1e21` + `a6bf7bb` (fix
  `useEffect` perdido) + `d64c045` (clamp al alto del grid) + `3962c67`
  (misma lógica defensiva que los bloqueos).
- Nuevo `BusinessAbsencesController` (`GET /absences?from&to`, agregado
  por negocio) consumido por el calendario. Cubierto en Postman con 8
  casos (happy, rango vacío, validación de `from`/`to`, cortocircuito
  de `TenantGuardFilter`, 401 sin token).

### Color y consistencia visual
- **Color asignable por empleado** — `4c54f0a` y propagación en toda la
  UI `0cccd66` (avatar Empleados, EventChip de Citas y Calendario,
  hover, modal de detalle).
- **Color configurable por cabina** — `18ceda1` (`color` en `Booth` +
  DTOs + service) + `39a9bff` (Postman). Cierra la asimetría con
  empleados: ya no se asigna por `boothId % 8`.
- **Hover rico al pasar sobre una cita** — `96b4ae7` + traducción al
  español del estado `85756d0` (`useCatalog().statusLabel`).
- **Minicalendario** para saltar a una fecha — `c076560`.
- **Heatmap del Mes por densidad** — `3ffa09c` + separadores tipo
  Semana `c10ba17` (la malla más legible).
- **Modo compacto retirado** — `e1518ea`. Reemplazado por `hourPx`
  adaptativo (`a649e0e`) que reduce la altura por hora cuando el rango
  visible es muy extenso (≥14 h → 48 px; ≥18 h → 40 px).
- **Impresión más estética** — `3d1109c` (`print:` + `@page`,
  `print-color-adjust: exact`).
- **Citas — selector Próximas / Pasadas + chip "Pasada sin cerrar"** —
  `aaf318e`.

### Avisos al desactivar
- **Empleado con citas próximas** — `843df0f` + `f8b3a88` (cancelar la
  consulta si el modal se cierra rápido). Banner rojo con el número de
  citas activas futuras (`AbortController` para no fugar promesas).
- **Categoría con servicios activos** — `c2c9020` (backend 409).
- **Servicios archivados se pueden editar sin reactivar** — `8f7ed19`
  (quitado el check `!isActive` del `updateService`).
- **Reactivar servicio exige categoría e impuesto activos** — `b6948c6`.

### Mejoras de horarios (post P1)
- **HoursTab + ScheduleGrid con rango adaptativo** — `efcc53e` +
  `2b53091`. La barra se calculaba sobre 8-22 fijo y se desbordaba con
  horarios extensos (`width > 100%`); ahora el rango es el min/max del
  horario realmente configurado, con cap `[0, 24]`.
- **Etiquetas L/M/X… ya no se descolocan al borrar el primer tramo de
  un día con turno partido** — `0ab2775` (Empleados) + `0d40082`
  (Configuración). Bug de React reconcile: el patrón anidado
  `[1..7].map(d => rows.map(...))` no tenía key estable a nivel de día
  y la etiqueta dependía del `idx` local del sub-map. `flatMap` +
  `isFirstOfDay` por item + key globalmente única arregla el reciclaje.
- **`openRangesFor` multi-tramo** — `80b84b3`. La función usaba `.find()`
  y devolvía un único rango; con turno partido el segundo tramo se
  pintaba como cerrado en la rejilla del calendario. `.filter()` + N
  rangos + invertida la lógica de `isClosed` en `HourSlots`.

---

## Notas

- VIS-4 del Informe de Calidad anterior (terminología Archivar/Desactivar) se
  decidió **dejar como está** — la distinción es defendible.
- Citas.jsx **sí** persiste sus filtros `from`/`to`/`employee` en `localStorage`,
  pero son visibles en pantalla, así que no es la misma trampa que P6/P7. Sin
  cambiar de momento.
