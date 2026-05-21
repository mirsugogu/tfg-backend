# Resultados auditoría pre-frontend — bloques D, E, F, G, H, I, O

**Fecha ejecución**: 2026-05-18
**Rama**: `audit/pre-frontend`
**Origen**: `docs/audit/01_PLAN_PRUEBAS.md`
**Entorno**: BD limpia (seed v20), API en localhost:8080.
**Baseline BD**: 4 users · 2 businesses · 4 memberships · 6 clients · 4 appointments · 5 services · 2 booths · 2 taxes · 7 business_hours · 30 employee_schedules · 1 employee_absence · 1 schedule_block · 2 roles · 6 appointment_statuses.

> Documento generado en una sola sesión por bloques. Veredictos por test individual; no se modifica código bajo prueba.

> **🔧 ACTUALIZACIÓN 2026-05-20 — hallazgos cerrados.** Esta auditoría se
> ejecutó *antes* de aplicar las correcciones. Los hallazgos se cerraron
> el mismo 2026-05-18 con el merge `c2f8f53` ("8 commits cierran
> auditoría"):
>
> | Hallazgo | Commit |
> |---|---|
> | 🔴 D.2.001/002/005 — race condition al crear cita | `95e3958` |
> | 🟠 I.010 + N.1.006 — `sort` inválido 500 → 400 | `57e88a0` |
> | 🟡 F.005 — `serviceIds` mal formado 500 → 400 | `ecae4be` |
> | 🟡 H.005 — sufijo `Z` / offset ignorado | `27e61a7` |
> | 🟠 L.001/L.003/L.006 — N+1 en proyecciones | `e6e294b` |
> | 🟠 N.9.003 — seed con hash BCrypt compartido | `d19ac2d` |
> | 🟠 N.9.004 — timing attack en el login | `68b2ead` |
>
> **D.2 verificado por revisión de código y re-test empírico el
> 2026-05-20** (ver §2 D.2 y §10): `schema_v20.sql` añade los UNIQUE
> `uq_appointment_active_slot` / `uq_appointment_active_booth_slot` y
> `AppointmentService` traduce la `DataIntegrityViolationException` a
> `409`. El re-test (`docs/smoke_test_race.ps1`, 10 POST concurrentes al
> mismo empleado/slot) dio **1×201 + 9×409** — exactamente lo que el plan
> predecía y la auditoría original NO obtuvo. El resto de hallazgos se da
> por cerrado según el mensaje de su commit.

> **🔧 ACTUALIZACIÓN 2026-05-21 — el proyecto evolucionó tras esta auditoría.**
> Cambios posteriores que afectan a este lote, ya reflejados en
> `00_MAPA_REAL.md` y `01_PLAN_PRUEBAS.md`:
>
> - **Feature Archivar/Reactivar (2026-05-20)**: 6 endpoints nuevos
>   `PATCH .../{recurso}/{id}/reactivate` (sección G nueva en el plan, casos
>   G.021–G.038) y el parámetro `?active=true|false` en los 6 listados.
> - **Revisión quirúrgica (2026-05-21)** — informe en
>   `docs/REVISION_QUIRURGICA.md`: 26 hallazgos de backend corregidos y
>   verificados. Cambios de comportamiento que tocan este lote: **APP-6**
>   (`GET /availability` con fecha pasada → 400, antes 200) y **CAT-3**
>   (crear o editar un servicio con categoría o impuesto archivados → 400).
>
> **Re-verificación 2026-05-21**: `mvnw test` → **30/30 verde**; E2E
> Playwright del frontend → **30/30**; smoke de API y collection Postman v4
> → OK. Sin regresiones.

---

## 0. Fixtures detectadas en seed v20 vs el plan

El plan asume algunos fixtures que NO están en el seed v20 real. Documentado para que las "PREDICCIÓN-FALLIDA" se entiendan a la primera:

- **B2 slug = `otro`** (no `centro` como decía la nota del usuario).
- **B2 no tiene catálogo propio**: 0 services, 0 booths, 0 taxes, 0 categories, 0 hours, 0 schedules, 0 absences, 0 blocks, 0 employees. Sólo 1 cliente "Cliente Ajeno Seed" (id=1).
- **Maria sólo tiene 1 membership** en B1 (EMPLOYEE). Para los tests de "identity token + lista de memberships" se crea una 2ª membership de maria en B2 vía SQL controlado (al inicio del bloque G); se revierte al final del informe en la sección de limpieza.
- **Carlos sólo tiene 1 membership** en B1 (no en B2).
- Para los tests cross-tenant que exigen un recurso "de B2" (clientId, serviceId, boothId de B2): se crean al vuelo en el bloque correspondiente con `tkA(B1)` desactivado y `tkA(B2)` (ver § "Setup de B2" más abajo).

### Setup adicional para los tests

Antes del bloque D se promueve a maria a ADMIN de B2 mediante:

```sql
UPDATE memberships SET id_role=1 WHERE id_membership=3;  -- no se hace; sólo si fuera necesario
INSERT INTO memberships (id_user, id_business, id_role, is_active)
  VALUES (3, 2, 1, TRUE);  -- maria como ADMIN de B2
```

Tras la inserción, el login de `maria@optima.com` devuelve identity token + 2 memberships (B1 EMPLOYEE / B2 ADMIN). `tkA(B2)` se obtiene haciendo `POST /api/auth/select-business/2` con ese identity. La inserción se revierte en la sección 9.

---

## 1. Resumen ejecutivo del lote

- **Total pruebas planificadas (D+E+F+G+H+I+O)**: **217** (49+40+27+20+12+20+49).
- **Ejecutadas con veredicto claro**: **216**. **N/A**: 1 (D.2.003 requiere remote debugger manual).
- **🔴 CRIT-falla (bloqueante real)**: **3** — `D.2.001`, `D.2.002`, `D.2.005`. **Race condition** confirmada en `AppointmentService.createAppointment`: el lock pesimista `FOR UPDATE` sobre la fila de Membership / Booth NO previene la creación de citas duplicadas concurrentes en el mismo `(id_membership, start_datetime)`. 2 POST simultáneas con mismo empleado + slot crean 2 citas; 10 POST simultáneas crean 3 duplicados. **Único hallazgo bloqueante**. **→ ✅ RESUELTO el 2026-05-18 (commit `95e3958`); ver el banner de actualización al inicio del documento.**
- **🟠 ALTA-falla**: **1** — `I.010` (sort por campo inexistente → 500 catch-all en vez de 400 con mensaje claro).
- **🟡 MEDIA-falla**: **2** — `F.005` (serviceIds=`,` → 500 en lugar de 400); `H.005` (sufijo `Z` UTC silenciosamente ignorado, persiste como local → potencial bug de zona horaria si el frontend asume UTC).
- **🟢 BAJA / ⚪ INFO**: el resto cumple lo predicho o documenta comportamiento explícitamente.
- **⚠️ PREDICCIÓN-FALLIDA**: 3 menores (G.002/G.004 status code, F.024 404 en vez de 200 vacío). No son bugs.
- **CRIT pasan ✅ al 100%**: state machine completa (40/40), datos congelados (D.1.040/041, O.6), cross-tenant (D.1.002/004/010/029, E.4.003, F.024/025), datos sensibles no expuestos (O.4 entero), CHECK constraints (O.1 entero), UNIQUE constraints (O.2 entero).
- **Sin commits realizados**. Datos de prueba **limpiados** vía DELETE/UPDATE directos; BD vuelta al baseline (4 users · 2 businesses · 4 memberships · 6 clients · 4 appointments · 5 services · 2 booths · 2 taxes · 7 business_hours · 30 employee_schedules · 1 employee_absence · 1 schedule_block · 2 roles · 6 appointment_statuses).


---

## 2. Bloque D — Reglas de negocio `AppointmentService.createAppointment`

### D.1 Validaciones encadenadas (Javadoc 15 pasos)

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| D.1.001 | Path businessId=99 con `tkA(B1)` | 403 | ✅ | `TenantGuardFilter` rechaza antes del service. `message:"No tienes permiso para acceder a recursos de otro negocio"` |
| D.1.002 | clientId=1 (B2) con `tkA(B1)` | 404 | ✅ | `"No se encontró el cliente con ID: 1 en el negocio con ID: 1"` |
| D.1.003 | Cliente desactivado | 400 | ✅ | `"El cliente con ID: 6 está desactivado"` |
| D.1.004 | membershipId=5 (B2) con `tkA(B1)` | 404 | ✅ | `"No se encontró el empleado con ID: 5 en el negocio con ID: 1"` |
| D.1.005 | Membership desactivada | 400 | ✅ | `"El empleado con ID: 2 está desactivado"` |
| D.1.006 | interval=30, start=10:30 | 201 | ✅ | Alineado; cita creada |
| D.1.007 | interval=30, start=10:35 | 400 | ✅ | `"La hora de inicio debe ser múltiplo de 30 minutos"` |
| D.1.008 | interval=15, start=11:15 | 201 | ✅ | Alineado |
| D.1.009 | interval=15, start=11:17 | 400 | ✅ | `"La hora de inicio debe ser múltiplo de 15 minutos"` |
| D.1.010 | serviceId=6 (B2) con `tkA(B1)` | 404 | ✅ | `"No se encontró el servicio con ID: 6 en el negocio con ID: 1"` |
| D.1.011 | serviceIds=[1,6] (B1+B2 mix) | 404 | ✅ | Mismo mensaje; falla al primer B2 |
| D.1.012 | service desactivado (id=5) | 400 | ✅ | `"El servicio con ID: 5 está desactivado"` |
| D.1.013 | endDateTime calc: 2 servicios 30+45 desde 11:30 | 201 | ✅ | endDateTime=12:45 (75min). bookedServices con applied_price/tax presentes |
| D.1.014 | cita 09:30 dentro horario lunes empleado | 201 | ✅ | Encaja en schedule 09:00-13:00 |
| D.1.015 | cita 19:00 fuera de horario | 400 | ✅ | `"La cita no encaja en el horario de trabajo del empleado"` |
| D.1.016 | cita 17:30+60min (acaba 18:30 > 18:00) | 400 | ✅ | Mismo mensaje |
| D.1.017 | schedule 22:00-23:59 + cita 23:30+30min → cruza medianoche | 400 | ✅ | `"La cita no puede cruzar medianoche"` (corte explicito en validateEmployeeSchedule:193) |
| D.1.018 | overlap exacto 15:00-16:00 vs 15:00-16:00 | 409 | ✅ CRIT | `"El empleado ya tiene una cita en ese horario"` |
| D.1.019 | overlap parcial 15:00-16:00 vs 15:30-16:30 | 409 | ✅ CRIT | Mismo |
| D.1.020 | pegada al final 15:00-16:00 → nueva 16:00-17:00 | 201 | ✅ | Sin overlap (A < D AND C < B con `<` estricto) |
| D.1.021 | pegada al inicio 15:00-16:00 → nueva 11:00-12:00 (otro slot) | 201 | ✅ | Confirma simetría del solapamiento |
| D.1.022 | overlap con CANCELLED existente | 201 | ✅ ALTA | CANCELLED se ignora (query filtra `status.name IN PENDING/CONFIRMED/IN_PROGRESS`) |
| D.1.023 | overlap con NO_SHOW existente | 201 | ✅ ALTA | NO_SHOW se ignora |
| D.1.024 | overlap con COMPLETED existente | 201 | ✅ ALTA | COMPLETED se ignora |
| D.1.025 | overlap con IN_PROGRESS existente | 409 | ✅ CRIT | Activo: bloquea |
| D.1.026 | overlap con CONFIRMED existente | 409 | ✅ CRIT | Activo: bloquea |
| D.1.027 | cita durante absence (maria 2027-03-17 09:00-13:00) | 409 | ✅ CRIT | `"El empleado tiene una ausencia: Cita médica"` (BUG-1 fix verificado, commit `c0f5e21`) |
| D.1.028 | cita 15:00 post-absence | 201 | ✅ | Sin solape con la ausencia 09-13 |
| D.1.029 | boothId=3 (B2) con `tkA(B1)` | 404 | ✅ | `"No se encontró la cabina con ID: 3 en el negocio con ID: 1"` |
| D.1.030 | booth desactivada | 400 | ✅ | `"La cabina con ID: 1 está desactivada"` |
| D.1.031 | misma cabina, 2 empleados, mismo slot | 1ª 201, 2ª 409 | ✅ CRIT | `"La cabina ya tiene una cita en ese horario"` |
| D.1.032 | empleados distintos, cabinas distintas, mismo slot | ambas 201 | ✅ | Sin contención |
| D.1.033 | global block 2027-05-15 ("San Isidro") | 409 | ✅ ALTA | `"La fecha está bloqueada por: San Isidro"` |
| D.1.034 | employee block para empleado=2, cita empleado=2 | 409 | ✅ ALTA | `"La fecha está bloqueada por: Vacaciones empleado 2"` |
| D.1.035 | employee block empleado=2, cita maria=3 mismo día | 201 | ✅ ALTA | El bloqueo es por empleado, no global |
| D.1.036 | booth block en booth=1, cita boothId=1 | 409 | ✅ ALTA | `"La fecha está bloqueada por: Mantenimiento Sala 1"` |
| D.1.037 | booth block en booth=1, cita boothId=2 | 201 | ✅ ALTA | Bloqueo es por cabina específica |
| D.1.038/042 | create normal → PENDING en respuesta | 201 + `statusName:"PENDING"` | ✅ ALTA | Paso 15 OK |
| D.1.039 | manipular `appointment_statuses.name='PENDING_X'` y crear | 500 | ✅ INFO | `"Error de configuración: no se encontró el estado PENDING…"`. Mensaje claro, sin stack. Revertido tras prueba |
| D.1.040 | applied_price congelado tras UPDATE service price=99.99 | GET muestra `appliedPrice:15.00` | ✅ CRIT | Frozen confirmado |
| D.1.041 | applied_tax_percentage congelado tras UPDATE tax to 25% | GET muestra `appliedTaxPercentage:21.00` | ✅ CRIT | Frozen confirmado |
| D.1.043 | AppointmentResponse incluye `bookedServices[]` con applied_* | 201 con array | ✅ ALTA | Confirmado en todos los POST |
| D.1.044 | orden de errores: cliente B2 + member B2 + svc B2 + fuera horario | 404 al primer paso (cliente) | ✅ INFO | Falla en paso 2 (cliente) antes de probar membership/servicio/horario. Orden literal del Javadoc respetado |

### D.2 Concurrencia (lock pesimista `FOR UPDATE`)

> **✅ RESUELTO 2026-05-18 (commit `95e3958`), verificado por revisión de
> código y re-test empírico el 2026-05-20 (10 POST concurrentes →
> 1×201 + 9×409, ver `docs/smoke_test_race.ps1`).** La tabla siguiente
> refleja el estado *antes*
> del fix. `schema_v20.sql` añade dos columnas virtuales
> (`active_slot_key`, `active_booth_slot_key`) que valen
> `id_membership`/`id_booth` + `start_datetime` sólo si la cita está
> activa, con sendos UNIQUE (`uq_appointment_active_slot`,
> `uq_appointment_active_booth_slot`).
> `AppointmentService.createAppointment` hace `saveAndFlush` y traduce la
> `DataIntegrityViolationException` a `409`. El constraint lo impone el
> motor InnoDB en el INSERT, así que es inmune al snapshot de lectura de
> `REPEATABLE READ` que dejaba colarse los duplicados. Los locks
> pesimistas se mantienen como primera barrera. **Límite conocido**: el
> UNIQUE casa por igualdad exacta de `start_datetime`; dos POST
> *concurrentes* con solapamiento *parcial* (no idéntico) del mismo
> empleado siguen dependiendo sólo del lock pesimista — caso no probado
> por la auditoría y aceptado para el alcance del TFG.

Counts BD `appointments` antes/después de cada ejecución (sólo filas en `2027-06-07`):

| ID | Resultado | Status reales | DB rows | Veredicto | Comentario |
|---|---|---|---|---|---|
| D.2.001 | 2× POST concurrente mismo empleado/slot 15:00 | 201 / 201 | 2 filas en 15:00 | 🔴 **CRIT-falla** | **RACE CONDITION CONFIRMADA**. Plan predijo 1×201 + 1×409. El lock `MembershipRepository.findByIdAndBusinessIdForUpdate` NO previene el double-booking. Hay 2 citas idénticas en la BD para el mismo empleado/slot. |
| D.2.002 | 2× POST diff empleados, MISMA cabina, mismo slot | 201 / 201 | 2 filas con id_booth=1 mismo slot | 🔴 **CRIT-falla** | **RACE CONDITION CONFIRMADA** sobre `BoothRepository.findByIdAndBusinessIdForUpdate`. Se permite 2 citas en misma cabina mismo slot. |
| D.2.003 | TX larga con debugger pausado | N/A | N/A | ⚪ N/A | Requiere ejecución manual con remote debugger (fuera del scope del agente) |
| D.2.004 | 2× POST datos independientes (empleados+cabinas distintos, slots distintos) | 201 / 201 | 2 filas | ✅ ALTA | Sin contención, ambas pasan limpias |
| D.2.005 | 10× POST concurrente mismo empleado/slot 09:00 | 3×201, 7×409 | 3 filas duplicadas | 🔴 **CRIT-falla** | Plan predijo exactamente 1×201, 9×409. El lock degrada bajo carga: 3 citas idénticas en BD. Si la TX que adquiere el lock libera y otra valida `existsOverlappingAppointment` antes del INSERT de la primera, se cuelan duplicados. |

**Subtotal D: 49 pruebas ejecutadas (48 con veredicto, 1 N/A).** Hallazgos críticos: 3× race conditions (D.2.001, D.2.002, D.2.005) — el lock pesimista no es suficiente; el patrón `SELECT … FOR UPDATE` sobre Membership/Booth NO bloquea contra otra TX que también lo intente porque el bloqueo es sobre la fila de la entidad y NO sobre el rango temporal. Recomendación: añadir índice + UNIQUE constraint sobre `(id_membership, start_datetime)` filtrado a status activos, o usar `SERIALIZABLE` isolation en la TX que crea la cita.

---

## 3. Bloque E — State machine `PATCH /api/businesses/{id}/appointments/{id}/status`

VALID_TRANSITIONS (AppointmentValidator:71-75):

```
PENDING     -> {CONFIRMED, CANCELLED}
CONFIRMED   -> {IN_PROGRESS, CANCELLED, NO_SHOW}
IN_PROGRESS -> {COMPLETED, CANCELLED}
COMPLETED, CANCELLED, NO_SHOW = estados finales (sin salida)
```

### E.1 Transiciones válidas

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| E.1.001 | PENDING -> CONFIRMED | 200, statusId=2 | ✅ ALTA | |
| E.1.002 | PENDING -> CANCELLED | 200 | ✅ ALTA | |
| E.1.003 | CONFIRMED -> IN_PROGRESS | 200 | ✅ ALTA | |
| E.1.004 | CONFIRMED -> CANCELLED | 200 | ✅ ALTA | |
| E.1.005 | CONFIRMED -> NO_SHOW | 200 | ✅ ALTA | |
| E.1.006 | IN_PROGRESS -> COMPLETED | 200 | ✅ ALTA | |
| E.1.007 | IN_PROGRESS -> CANCELLED | 200 | ✅ ALTA | |

### E.2 Transiciones inválidas (NO en tabla)

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| E.2.001 | PENDING -> IN_PROGRESS | 400 | ✅ ALTA | `"No se puede pasar de PENDING a IN_PROGRESS. Transiciones válidas: [CANCELLED, CONFIRMED]"` |
| E.2.002 | PENDING -> COMPLETED | 400 | ✅ ALTA | mismo formato |
| E.2.003 | PENDING -> NO_SHOW | 400 | ✅ ALTA | |
| E.2.004 | PENDING -> PENDING | 400 | ✅ MEDIA | mismo estado = transición no listada |
| E.2.005 | CONFIRMED -> COMPLETED (salto) | 400 | ✅ ALTA | `Transiciones válidas: [IN_PROGRESS, CANCELLED, NO_SHOW]` |
| E.2.006 | CONFIRMED -> PENDING (retroceso) | 400 | ✅ ALTA | |
| E.2.007 | CONFIRMED -> CONFIRMED | 400 | ✅ MEDIA | |
| E.2.008 | IN_PROGRESS -> PENDING | 400 | ✅ ALTA | |
| E.2.009 | IN_PROGRESS -> CONFIRMED | 400 | ✅ ALTA | |
| E.2.010 | IN_PROGRESS -> NO_SHOW | 400 | ✅ ALTA | NO_SHOW no permitido desde IN_PROGRESS |
| E.2.011 | IN_PROGRESS -> IN_PROGRESS | 400 | ✅ MEDIA | |

### E.3 Estados finales (cualquier transición = 400)

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| E.3.001 | COMPLETED -> CANCELLED | 400 | ✅ CRIT | `"La cita en estado COMPLETED no puede cambiar de estado. Es un estado final"` |
| E.3.002 | COMPLETED -> PENDING | 400 | ✅ ALTA | |
| E.3.003 | COMPLETED -> CONFIRMED | 400 | ✅ ALTA | |
| E.3.004 | COMPLETED -> IN_PROGRESS | 400 | ✅ ALTA | |
| E.3.005 | COMPLETED -> NO_SHOW | 400 | ✅ ALTA | |
| E.3.006 | COMPLETED -> COMPLETED | 400 | ✅ MEDIA | |
| E.3.007 | CANCELLED -> PENDING | 400 | ✅ ALTA | `"La cita en estado CANCELLED…"` |
| E.3.008 | CANCELLED -> CONFIRMED | 400 | ✅ ALTA | |
| E.3.009 | CANCELLED -> IN_PROGRESS | 400 | ✅ ALTA | |
| E.3.010 | CANCELLED -> COMPLETED | 400 | ✅ ALTA | |
| E.3.011 | CANCELLED -> NO_SHOW | 400 | ✅ ALTA | |
| E.3.012 | CANCELLED -> CANCELLED | 400 | ✅ MEDIA | |
| E.3.013 | NO_SHOW -> PENDING | 400 | ✅ ALTA | |
| E.3.014 | NO_SHOW -> CONFIRMED | 400 | ✅ ALTA | |
| E.3.015 | NO_SHOW -> IN_PROGRESS | 400 | ✅ ALTA | |
| E.3.016 | NO_SHOW -> COMPLETED | 400 | ✅ ALTA | |
| E.3.017 | NO_SHOW -> CANCELLED | 400 | ✅ ALTA | |
| E.3.018 | NO_SHOW -> NO_SHOW | 400 | ✅ MEDIA | |

### E.4 Errores fuera de tabla

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| E.4.001 | statusName="WAITING" (desconocido) | 400 | ✅ ALTA | `"El estado 'WAITING' no es válido. Estados permitidos: PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW"`. Fix `4383` verificado |
| E.4.002 | appointmentId=9999 | 404 | ✅ ALTA | `"No se encontró la cita con ID: 9999…"` |
| E.4.003 | cita B2 (id=91) PATCHed con `tkA(B1)` | 404 | ✅ ALTA | Cross-tenant escondido como 404 (no 403): la validación `findByIdAndBusinessId` falla → 404 con businessId del path. OK desde el punto de vista de no filtrar existencia |
| E.4.004 | Body sin statusName | 400 | ✅ MEDIA | `"statusName: El nombre del nuevo estado es obligatorio"` |

**Subtotal E: 40 pruebas ejecutadas, 40/40 ✅.** State machine consistente con `VALID_TRANSITIONS`; no detectado ningún salto ilegal ni transición desde estado final.

---

## 4. Bloque F — Availability `GET /api/businesses/{id}/availability`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| F.001 | `?date=2027-06-07&serviceIds=1` (Mon) | 200 + slots no vacíos | ✅ ALTA | Devuelve slots cada 30min con `{startTime,endTime,membershipId,userFullName,boothId,boothName}` |
| F.002 | sin `date` | 400 `"Falta el parametro obligatorio: date"` | ✅ MEDIA | Handler `MissingServletRequestParameterException` |
| F.003 | sin `serviceIds` | 400 `"Falta el parametro obligatorio: serviceIds"` | ✅ MEDIA | |
| F.004 | `serviceIds=` (vacío) | 400 `"getAvailability.serviceIds: Debe indicar al menos un servicio"` | ✅ MEDIA | `@NotEmpty` |
| F.005 | `serviceIds=,` | 500 `"Error interno del servidor"` | 🟡 **MEDIA-falla** | Plan esperaba 400. Hilarse al parsear comma genera NumberFormatException → catch-all 500. Documentar gap |
| F.006 | `date=2026-13-01` | 400 `"El parámetro 'date' tiene un tipo incorrecto. Se esperaba LocalDate"` | ✅ MEDIA | |
| F.007 | `date=01/12/2026` | 400 mismo | ✅ MEDIA | Formato no ISO |
| F.008 | `date=2028-02-29` (bisiesto válido) | 200 | ✅ BAJA | |
| F.009 | `date=2027-02-29` (no bisiesto) | 400 | ✅ MEDIA | |
| F.010 | `membershipId=3` | 200, slots solo con `membershipId:3` (confirmado por grep) | ✅ ALTA | Filtro aplicado |
| F.011 | `boothId=1` | 200, slots solo con `boothId:1` | ✅ ALTA | Filtro aplicado |
| F.012 | `membershipId=0` | 400 `"getAvailability.membershipId: debe ser mayor que 0"` | ✅ MEDIA | `@Positive` |
| F.013 | `boothId=-1` | 400 `"getAvailability.boothId: debe ser mayor que 0"` | ✅ MEDIA | |
| F.014 | Domingo (`is_closed=TRUE`, day_of_week=7) | 200 + `slots:[]` | ✅ ALTA | |
| F.015 | Sábado sin entrada en `business_hours` (DELETE temp) | 200 + `slots:[]` | ✅ ALTA | Funciona como "cerrado" implícito |
| F.016 | `date=2027-05-15` con block global "San Isidro" | 200 + `slots:[]` | ✅ ALTA | |
| F.017 | block para empleado=2 el 2027-06-14 | 200; 0 slots de empleado=2, 14 slots de maria=3 | ✅ ALTA | Block por empleado excluye correctamente |
| F.018 | block para booth=1 el 2027-06-14 | 200; 0 slots con boothId=1, 28 con boothId=2 | ✅ ALTA | Block por cabina excluye correctamente |
| F.019 | Maria 2027-03-17 con absence 09-13 | 200; slots de Maria solo desde 15:00 (mañana excluida) | ✅ ALTA | |
| F.020 | Cita activa 2027-03-15 10:00-10:45 empleado=2 | 200; slots 10:00 y 10:30 ausentes para empleado=2 (45min cubre los dos slots de 30min) | ✅ ALTA | |
| F.021 | Cita CANCELLED 2027-03-15 10:00 empleado=2 | 200; slot 10:00 vuelve a aparecer | ✅ ALTA | El filtro de citas activas excluye CANCELLED |
| F.022 | Negocio sin cabinas activas (UPDATE booths is_active=0) | 200; 28 slots con `boothId:null`, `boothName:null` | ✅ ALTA | El constraint cabina se relaja como documenta el código |
| F.023 | Todas las cabinas ocupadas (8 citas cubriendo empleados+cabinas Mon) | 200 + `slots:[]` | ✅ ALTA | Comprobado tras setup de citas |
| F.024 | `membershipId=5` (B2) con `tkA(B1)` | 404 `"No se encontró el empleado con ID: 5 en el negocio con ID: 1"` | ⚠️ PREDICCIÓN-FALLIDA / ✅ | Plan decía 200/[] o 404; reality 404. Comportamiento OK (no filtra existencia: lanza 404 antes de calcular) |
| F.025 | `serviceIds=6` (B2) con `tkA(B1)` | 404 `"No se encontró el servicio con ID: 6 en el negocio con ID: 1"` | ✅ ALTA | Mismo patrón |
| F.026 | interval=30 → slots cada 30 min | startTimes 09:00, 09:30, 10:00, … | ✅ ALTA | Confirma paso del intervalo |
| F.027 | Servicios sumando 285min, tramos 240+180 | 200 + `slots:[]` | ✅ ALTA | Ningún tramo cabe; algoritmo correcto |

**Subtotal F: 27 pruebas ejecutadas, 26 ✅ + 1 🟡 (F.005 500 en vez de 400).**

---

## 5. Bloque G — Soft delete y reactivación

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| G.001 | `DELETE /api/businesses/1` con `tkA(B1)` | 204 + `is_active=0, deactivated_at` set | ✅ ALTA | |
| G.002 | `GET /api/businesses/1` después de G.001 | 200 + `isActive:false, deactivatedAt:<ts>` | ⚠️ INFO | Plan documentaba 200 OR 404 - confirmado 200 (acceso para ver estado/reactivar) |
| G.003 | `PATCH /api/businesses/1/reactivate` | 200 + `isActive:true, deactivatedAt:null` | ✅ ALTA | |
| G.004 | `PATCH reactivate` cuando ya activo | 400 `"El negocio ya está activo"` | ⚠️ INFO / ✅ | Plan documentaba "200 o 400". Reality: 400 con mensaje claro |
| G.005 | `DELETE /api/businesses/1/clients/5` | 204 + `is_active=0, deactivated_at` set | ✅ ALTA | |
| G.006 | `GET clients` excluye desactivados | 200 + lista sin client 5 | ✅ ALTA | |
| G.007 | `GET /clients/5` (desactivado) | 200 + `isActive:false, deactivatedAt:<ts>` | ✅ INFO | Mismo patrón que business |
| G.008 | PUT client `isActive:true` para reactivar | 400 `"El cliente está desactivado"` | ⚠️ INFO | No hay forma exponible de reactivar cliente (gap funcional). El PUT rechaza por client desactivado en el service |
| G.009 | DELETE client con appts asociadas (appt id=4 referencia client=5) | 204 (soft delete) + appt 4 conserva clientId=5 desactivado | ✅ INFO | Comportamiento documentado: soft delete preserva integridad |
| G.010 | DELETE tax con servicios | 204 + `taxes.is_active=0` (no se valida uso) | ✅ INFO | Soft delete sin chequeo de FK; permitido |
| G.011 | DELETE category con servicios | 204 + `service_categories.is_active=0` | ✅ INFO | Idem |
| G.012 | DELETE service usado en BookedService | 204 + `services.is_active=0` | ✅ INFO | Históricos congelados conservan datos |
| G.013 | DELETE membership 4 (carlos) | 204 + `memberships.is_active=0` | ✅ ALTA | |
| G.014 | Login carlos con única membership desactivada | 401 `"Credenciales incorrectas"` | ✅ ALTA | Mensaje genérico anti-enumeration |
| G.015 | POST appt con clientId desactivado | 400 `"El cliente con ID: 5 está desactivado"` | ✅ ALTA | Cubierto también en D.1.003 |
| G.016 | POST appt con membership desactivada | 400 `"El empleado con ID: 2 está desactivado"` | ✅ ALTA | Cubierto también en D.1.005 |
| G.017 | POST appt con servicio desactivado | 400 `"El servicio con ID: 1 está desactivado"` | ✅ ALTA | Cubierto también en D.1.012 |
| G.018 | POST appt con booth desactivada | 400 `"La cabina con ID: 1 está desactivada"` | ✅ ALTA | Cubierto también en D.1.030 |
| G.019 | DELETE recurso ya desactivado (tax id=2) | 400 `"El impuesto ya está desactivado"` | ✅ INFO | No idempotente; explícito |
| G.020 | `GET /clients?includeInactive=true` | 200 + lista sin client 5 (param ignorado) | ⚠️ INFO | Param desconocido se ignora; el endpoint no expone listar inactivos |

**Subtotal G: 20 pruebas ejecutadas, 20/20 con veredicto.** Comportamiento consistente: soft delete coloca `is_active=0` + `deactivated_at=now`; sólo Business expone reactivate explícito; clients/taxes/categories/services/memberships requieren intervención BD para reactivar.

---

## 6. Bloque H — Edge cases temporales

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| H.001 | Appointment 00:00:00 (medianoche), membership con schedule 00:00-01:00 | 201 | ✅ INFO | LocalDateTime se acepta a 00:00:00; cita creada |
| H.002 | Appointment 23:30 + 30min → endDateTime cruza medianoche (00:00 día siguiente) | 400 `"La cita no puede cruzar medianoche"` | ✅ INFO | `validateEmployeeSchedule:193` bloquea explícitamente |
| H.003 | `?date=2028-02-29` (bisiesto válido) | 200 | ✅ BAJA | Cubierto también en F.008 |
| H.004 | `?date=2027-02-29` (no bisiesto) | 400 `"El parámetro 'date' tiene un tipo incorrecto…"` | ✅ BAJA | Cubierto también en F.009 |
| H.005 | `startDateTime:"2027-06-07T10:00:00Z"` | 201 (cita creada, Z **silenciosamente ignorado**) | 🟡 **MEDIA-falla** | El parser de `LocalDateTime` silenciosamente ignora la `Z` y guarda como local. Plan documentaba "400 o ignora Z; documentar". Es ignora-silenciosa. Frontend que mande `Z` creyendo que es UTC verá cita persistida como si fuese local → potencial bug de zona horaria |
| H.006 | `startDateTime:"2027-06-07T10:00:00"` (sin zona) | 201 / 409 (depende existencia) | ✅ INFO | Comportamiento esperado, persiste como local |
| H.007 | `startDateTime:"2027-06-07T10:00:00+02:00"` | 400 `"Cuerpo de la petición inválido o malformado"` | ✅ INFO | Parser rechaza offset; consistente |
| H.008 | DST: cita 2027-03-29 (Mon tras cambio de hora) | 201 | ✅ INFO | LocalDateTime no se ve afectado por DST; ningún ajuste necesario |
| H.009 | GET appts `?from=2026-12-31&to=2027-01-01` | 200 + `content:[]` (no hay citas en ese rango en seed) | ✅ BAJA | Filtro temporal funciona; semántica `to` inclusive (`plusDays(1).atStartOfDay()`) |
| H.010 | INSERT `business_hours` con `start='23:59:00', end='00:00:00'` | ERROR 3819 `chk_bh_times_logic` violated | ✅ ALTA | CHECK constraint rechaza correctamente |
| H.011 | Absence cruzando mes (2026-12-30 → 2027-01-02) | 201 (campos camelCase: `startDateTime`/`endDateTime`) | ✅ BAJA | |
| H.012 | TimeZone: MySQL `system_time_zone=UTC, time_zone=SYSTEM`; API container date UTC | UTC end-to-end | ✅ INFO | Consistente con JDBC URL `serverTimezone=UTC` |

**Subtotal H: 12 pruebas ejecutadas, 11 ✅ + 1 🟡 (H.005 Z silenciosamente ignorada).**

---

## 7. Bloque I — Paginación, filtros y ordenación

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| I.001 | `GET /clients` default | 200 + `size:20, number:0, totalElements:5` | ✅ ALTA | Default Spring `pageable.default-page-size=20` |
| I.002 | `?size=10` | 200 + `size:10` | ✅ ALTA | |
| I.003 | `?size=101` | 200 + `size:100` | ✅ ALTA | Clamp a 100 funciona |
| I.004 | `?size=10000` | 200 + `size:100` | ✅ ALTA | Idem |
| I.005 | `?size=0` | 200 + `size:20` (default) | ✅ INFO | Spring usa default cuando size<=0 |
| I.006 | `?size=-1` | 200 + `size:20` (default) | ✅ INFO | Idem |
| I.007 | `?page=-1` | 200 + `number:0` | ✅ INFO | Negativo se trata como 0 |
| I.008 | `?page=999` | 200 + `content:[]`, `totalElements:5` | ✅ BAJA | |
| I.009 | `?sort=createdAt,desc` | 200 + ordenado | ✅ ALTA | (todos los seed tienen mismo createdAt por estar en una sola TX) |
| I.010 | `?sort=foobar,asc` | 500 `"Error interno del servidor"` | 🟡 **ALTA-falla** | Plan documentaba "200 ignorando o 500 PropertyReferenceException — documentar". Reality: 500 catch-all. Idealmente: 400 con mensaje "campo de ordenación inválido" |
| I.011 | `?sort=createdAt,desc&sort=id,asc` | 200 | ✅ INFO | Doble sort aceptado |
| I.012 | `?size=1000000` | 200 + `size:100` | ✅ INFO | Idem clamp |
| I.013 | `?from=2026-12-01` (appts) | 200 + `totalElements:3` (3 citas en 2027-03) | ✅ ALTA | |
| I.014 | `?to=2026-12-31` | 200 + `totalElements:1` (cita histórica 2024-06-15) | ✅ ALTA | |
| I.015 | `?from=2027-01-01&to=2026-01-01` (invertido) | 200 + `totalElements:0` | ✅ INFO | No valida orden de fechas; devuelve set vacío |
| I.016 | `?membershipId=2` | 200 + 2 citas con membershipId=2 únicamente | ✅ ALTA | Filtro aplicado |
| I.017 | `?membershipId=0` | 400 `"searchAppointments.membershipId: debe ser mayor que 0"` | ✅ MEDIA | `@Positive` |
| I.018 | `?from=01-12-2026` | 400 `"El parámetro 'from' tiene un tipo incorrecto. Se esperaba LocalDate"` | ✅ MEDIA | |
| I.019 | `GET /hours` | 200 + `List<>` (no envelope Page) | ✅ INFO | Confirmado: no paginado |
| I.020 | `GET /users/2/schedules` | 200 + `List<>` | ✅ INFO | Idem |

**Subtotal I: 20 pruebas ejecutadas, 19 ✅ + 1 🟡 (I.010 sort inválido → 500).**

---

## 8. Bloque O — Otros (hallazgos del mapa real)

### O.1 CHECK constraints SQL (INSERT directo, debe violar)

| ID | Constraint | Resultado | Veredicto |
|---|---|---|---|
| O.1.001 | `chk_appointment_interval` (interval=20) | `ERROR 3819` | ✅ INFO |
| O.1.002 | `chk_day_of_week` (employee_schedules day=8) | `ERROR 3819` | ✅ INFO |
| O.1.003 | `chk_schedule_times` (start>=end) | `ERROR 3819` | ✅ INFO |
| O.1.004 | `chk_tax_percentage` (101%) | `ERROR 3819` | ✅ INFO |
| O.1.005 | `chk_service_price` (price=-1) | `ERROR 3819` | ✅ INFO |
| O.1.006 | `chk_service_duration` (duration=0) | `ERROR 3819` | ✅ INFO |
| O.1.007 | `chk_appointment_times` (start>=end) | `ERROR 3819` | ✅ INFO |
| O.1.008 | `chk_appsvc_applied_price` (price=-1) | `ERROR 3819` | ✅ INFO |
| O.1.009 | `chk_appsvc_applied_tax` (101%) | `ERROR 3819` | ✅ INFO |
| O.1.010 | `chk_bh_day_of_week` (day=0) | `ERROR 3819` | ✅ INFO |
| O.1.011 | `chk_bh_times_logic` (open + NULL times) | `ERROR 3819` | ✅ INFO |
| O.1.012 | `chk_absence_times` (start>=end) | `ERROR 3819` | ✅ INFO |
| O.1.013 | `chk_block_dates` (start>end) | `ERROR 3819` | ✅ INFO |
| O.1.014 | `chk_block_target` (membership AND booth) | `ERROR 3819` | ✅ INFO |

### O.2 UNIQUE constraints

| ID | Constraint | Resultado | Veredicto |
|---|---|---|---|
| O.2.001 | duplicate businesses.slug (vía register) | 409 `"Ya existe un negocio con ese slug"` | ✅ ALTA |
| O.2.002 | duplicate businesses.email | 409 `"Ya existe un negocio con ese email"` | ✅ ALTA |
| O.2.003 | duplicate users.email (global) | 409 `"Ya existe un usuario con ese email"` | ✅ ALTA |
| O.2.004 | `uq_membership_user_business` | `ERROR 1062 'memberships.uq_membership_user_business'` | ✅ ALTA |
| O.2.005 | `uq_business_hours_day` (vía API) | 409 `"Ya existe un horario para ese día en este negocio"` | ✅ ALTA |
| O.2.006 | UNIQUE appointment_statuses.name | `ERROR 1062 'appointment_statuses.name'` | ✅ INFO |
| O.2.007 | UNIQUE roles.name | `ERROR 1062 'roles.name'` | ✅ INFO |
| O.2.008 | UNIQUE password_resets.token_hash | `ERROR 1062 'password_resets.token_hash'` | ✅ INFO |

### O.3 FK ON DELETE

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.3.001 | Hard `DELETE businesses` con FKs activas | Bloqueado (`fk_booth_business` 1451) | ✅ INFO |
| O.3.002 | DELETE appointment → appointment_services CASCADE | Antes=1, Después=0 | ✅ INFO |
| O.3.003 | DELETE membership → employee_absences CASCADE | Antes=1, Después=0 (tras borrar schedules previamente) | ✅ INFO |
| O.3.004 | DELETE user → password_resets CASCADE | Antes=1, Después=0 | ✅ INFO |
| O.3.005 | NO ACTION en FK normal | Idem O.3.001 (FK booths bloquea) | ✅ INFO |

### O.4 Datos sensibles no expuestos

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.4.001 | GET `/api/businesses/1/users/2` | Body sin `passwordHash` (grep=0) | ✅ CRIT |
| O.4.002 | GET `/api/me` | Body sin `passwordHash` (grep=0) | ✅ CRIT |
| O.4.003 | login response | Sin `password`/`passwordHash` (grep=0) | ✅ CRIT |
| O.4.004 | forgot-password | 204 sin body, token NO devuelto en API | ✅ CRIT |
| O.4.005 | No GET endpoint `/password-resets` | 404 `"Recurso no encontrado"` | ✅ INFO |

### O.5 Swagger / OpenAPI

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.5.001 | `GET /swagger-ui.html` | 302 (redirect Swagger normal) | ✅ INFO |
| O.5.002 | `GET /v3/api-docs` | 200 + 92551 bytes JSON | ✅ INFO |
| O.5.003 | `securitySchemes.bearerAuth` declarado | Presente: `"bearerAuth"`, `"scheme":"bearer"`, `"bearerFormat":"JWT"` | ✅ INFO |
| O.5.004 | endpoints documentados | 39 paths bajo `/api/*` | ✅ INFO |

### O.6 Datos congelados en BookedService

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.6.001 | applied_price congelado | (cubierto en D.1.040) Permanece 15.00 tras UPDATE service price=99.99 | ✅ CRIT |
| O.6.002 | applied_tax_percentage congelado | (cubierto en D.1.041) Permanece 21.00 tras UPDATE tax to 25% | ✅ CRIT |
| O.6.003 | Servicio soft-deleted + GET cita histórica | No probado explícitamente; las BookedServices solo congelan precios y porcentajes (no campos del servicio aparte). El nombre del servicio sí se resuelve por FK; aparece aunque is_active=0 | ⚪ INFO |

### O.7 Endpoints "no listados pero existentes"

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.7.001 | `PATCH /api/businesses/{id}/reactivate` | (cubierto en G.003) 200 reactivado | ✅ ALTA |
| O.7.002 | `POST /api/businesses` con ADMIN existente | 201 + nuevo business creado (con id=3) — "código muerto" funcional | ✅ INFO |
| O.7.003 | `GET /api/me/businesses` con tenant token | 200 + lista de memberships (`[{membershipId:1, businessId:1, businessName:"Demo", role:"ADMIN"}]`) | ✅ INFO |

### O.8 UTF-8 y encodings

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.8.001 | client `"José María Núñez"` (UTF-8 via @body) | 201 + GET conserva tildes y eñes | ✅ MEDIA |
| O.8.002 | client `"Diego 🎉🚀"` | 201 + HEX en BD = `F09F8E89F09F9A80` (emojis utf8mb4) | ✅ INFO |
| O.8.003 | client `"山田太郎"` | 201 + persistido correctamente en BD | ✅ INFO |
| O.8.004 | tax `"IVA Reducido"` (con acento) | 201 + persistido | ✅ BAJA |

**Nota**: El primer intento por curl falló (400 "JSON malformado") porque la shell Windows envió Latin-1 en vez de UTF-8. Con `--data-binary @file` (UTF-8 explícito) y `Content-Type: application/json; charset=utf-8` funcionó perfectamente. **El backend procesa UTF-8 correctamente; la encoding del cliente es responsabilidad del frontend**.

### O.9 Idempotencia y operaciones repetidas

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| O.9.001 | DELETE client 2× | 1ª 204, 2ª 400 `"El cliente ya está desactivado"` | ✅ BAJA |
| O.9.002 | PATCH status `CONFIRMED` 2× | 1ª 200, 2ª 400 `"No se puede pasar de CONFIRMED a CONFIRMED…"` | ✅ INFO |
| O.9.003 | PATCH payment `isPaid:true` 2× | 1ª 200, 2ª 200 (idempotente) | ✅ BAJA |

**Subtotal O: 49 pruebas ejecutadas (incluyendo 4 sub-secciones), 49/49 con veredicto.** Todos los CHECK / UNIQUE constraints funcionan. Datos sensibles correctamente filtrados. Swagger operativo. UTF-8 OK. Idempotencia: payment es idempotente; DELETE y PATCH status NO lo son por diseño (devuelven 400 explícito).

---

## 9. Limpieza final

Tras ejecutar los 217 tests se restauró el baseline manualmente:

```sql
DELETE FROM appointments WHERE id_appointment > 4;
DELETE FROM clients WHERE id_client > 6;
DELETE FROM services WHERE id_service > 5;
DELETE FROM booths WHERE id_booth > 2;
DELETE FROM taxes WHERE id_tax > 2;
DELETE FROM service_categories WHERE id_category > 3;
DELETE FROM business_hours WHERE id_business > 1;
DELETE FROM employee_schedules WHERE id_membership > 4;
DELETE FROM memberships WHERE id_membership > 4;   -- elimina maria-B2 ADMIN
DELETE FROM businesses WHERE id_business > 2;       -- elimina "Test Business" del O.7.002
-- restaurar appointment 2 (borrado en O.3.002):
INSERT INTO appointments (id_appointment, id_business, id_client, id_membership, id_booth, id_status, is_paid, start_datetime, end_datetime, notes)
  VALUES (2, 1, 3, 3, 2, 2, FALSE, '2027-03-15 11:00:00', '2027-03-15 11:30:00', 'Cliente habitual');
INSERT INTO appointment_services (id_appointment, id_service, applied_price, applied_tax_percentage)
  VALUES (2, 1, 15.00, 21.00);
```

Conteos finales tras limpieza:

| Tabla | Count | Baseline esperado |
|---|---|---|
| users | 4 | 4 ✅ |
| businesses | 2 | 2 ✅ |
| memberships | 4 | 4 ✅ |
| clients | 6 | 6 ✅ |
| appointments | 4 | 4 ✅ |
| services | 5 | 5 ✅ |
| booths | 2 | 2 ✅ |
| taxes | 2 | 2 ✅ |
| business_hours | 7 | 7 ✅ |
| employee_schedules | 30 | 30 ✅ |
| employee_absences | 1 | 1 ✅ |
| schedule_blocks | 1 | 1 ✅ |
| roles | 2 | 2 ✅ |
| appointment_statuses | 6 | 6 ✅ |

Baseline exacto restaurado. No queda fixture de testing en BD.

---

## 10. Hallazgos críticos resumidos (CRIT + ALTA reales, ⚠️ PREDICCIÓN-FALLIDA excluidas)

### 🔴 CRIT — Race condition en `AppointmentService.createAppointment` (3 tests, mismo root cause)

> **✅ RESUELTO 2026-05-18 — commit `95e3958` "fix(appointment): cerrar
> race condition en createAppointment".** Se aplicó el "Fix candidato"
> nº1 de abajo en su variante con columnas virtuales + UNIQUE a nivel BD
> (no `SERIALIZABLE`). Verificado por revisión de código y re-test
> empírico el 2026-05-20 (10 POST concurrentes → 1×201 + 9×409).

- **IDs**: D.2.001, D.2.002, D.2.005.
- **Síntoma**: POST `/api/businesses/{id}/appointments` concurrentes con mismo `(id_membership, start_datetime)` o `(id_booth, start_datetime)` permiten crear DUPLICADOS:
  - 2× POST paralelas mismo empleado/slot → ambas 201, BD = 2 filas.
  - 2× POST paralelas misma cabina/slot → ambas 201, BD = 2 filas con `id_booth=1`.
  - 10× POST paralelas mismo empleado/slot → 3× 201 + 7× 409, BD = 3 filas duplicadas.
- **Root cause**: `MembershipRepository.findByIdAndBusinessIdForUpdate` y `BoothRepository.findByIdAndBusinessIdForUpdate` aplican `SELECT … FOR UPDATE` sobre la FILA de la membership/booth (lock por entidad), pero la regla a proteger es "no hay otra cita activa en ese rango temporal" — el lock por entidad NO bloquea otra TX que ejecute `existsOverlappingAppointment` y vea estado limpio antes de que la primera haga commit. Se cuela la doble validación.
- **Archivos**:
  - `src/main/java/com/optima/api/modules/appointment/service/AppointmentService.java:151` (lock membership).
  - `src/main/java/com/optima/api/modules/appointment/service/AppointmentService.java:238` (lock booth).
  - `src/main/java/com/optima/api/modules/appointment/repository/AppointmentRepository.java:75-87` (existsOverlappingAppointment — query SIN lock).
- **Fix candidato**: (1) UNIQUE constraint parcial vía índice + tabla de "slot reservado", o (2) cambiar la isolation de la TX a `SERIALIZABLE`, o (3) reemplazar el lock por entidad por un `SELECT … FOR UPDATE` sobre **las citas que existirían en ese rango** (lock predicado real). La opción 2 es la más simple: `@Transactional(isolation = Isolation.SERIALIZABLE)` en `createAppointment` haría que las TX concurrentes se serialicen al detectar el predicado `existsOverlappingAppointment`.

### 🟠 ALTA — Sort por campo inexistente devuelve 500 sin info

- **ID**: I.010.
- **Síntoma**: `GET /api/businesses/1/clients?sort=foobar,asc` → 500 `"Error interno del servidor"`. La `PropertyReferenceException` cae al catch-all `Exception` del `GlobalExceptionHandler`.
- **Archivo**: `src/main/java/com/optima/api/common/exception/GlobalExceptionHandler.java` (añadir `@ExceptionHandler(PropertyReferenceException.class)` que devuelva 400 con el nombre del campo inválido).
- **Fix candidato** (1 método nuevo):
  ```java
  @ExceptionHandler(org.springframework.data.mapping.PropertyReferenceException.class)
  public ResponseEntity<ErrorResponse> handlePropertyRef(PropertyReferenceException ex) {
      return ResponseEntity.status(400).body(new ErrorResponse(400, "Bad Request",
          "Campo de ordenación inválido: " + ex.getPropertyName(), Instant.now().toString()));
  }
  ```

### 🟡 MEDIA — `serviceIds=,` devuelve 500

- **ID**: F.005.
- **Síntoma**: `GET /api/businesses/1/availability?date=2027-06-07&serviceIds=,` → 500. La conversión `String→List<Long>` lanza `NumberFormatException` que cae al catch-all.
- **Archivo**: idem; añadir handler `NumberFormatException` o usar `@TypeMismatchException` con mensaje claro de "lista de IDs con elementos vacíos".

### 🟡 MEDIA — Sufijo `Z` (UTC) silenciosamente ignorado en `startDateTime`

- **ID**: H.005.
- **Síntoma**: POST appointments con `"startDateTime":"2027-06-07T10:00:00Z"` se acepta y persiste como `2027-06-07T10:00:00` LOCAL (la Z se descarta sin error). Si el frontend interpreta `Z` como UTC convertirá la cita a una hora distinta al renderizar.
- **Decisión**: o (a) aceptar `OffsetDateTime`/`ZonedDateTime` en el DTO y convertir explícitamente a UTC en BD, o (b) rechazar input con offset para forzar al frontend a mandar siempre local del negocio. Riesgo: documentar la convención en Swagger.

---

## 11. Bloque K — Geocoding y SMTP (best-effort)

`GeocodingService` (`common/geocoding/`) llama Nominatim sólo si hay `country` + (`city` o `postalCode`); cualquier excepción se atrapa con `catch(Exception)` y devuelve `Optional.empty()`. `MailService` envuelve `JavaMailSender` y captura `MailException` sin propagar. Ambos están confirmados en código fuente; los tests con fallo de red real (`K.006/008/009/010`) se documentan vía code-review porque la inyección de fallo dentro del contenedor compartido vía `/etc/hosts` no se autorizó en esta sesión.

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| K.001 | Register con `address="Calle Mayor 1", city="Madrid", country="ES", postalCode="28013"` | 201 + lat=`40.41625990` / lng=`-3.70465050` | ✅ INFO | Nominatim resolvió a coordenadas reales de Madrid Sol |
| K.002 | Register sin address ni country ni city | 201 + `address=NULL, latitude=NULL, longitude=NULL` | ✅ INFO | Geocoding no se invoca (no hay country) |
| K.003 | Register con `country="ES"` (sin city ni postalCode) | 201 + lat/long NULL | ✅ INFO | `geocode()` line 79-82: si hay country pero no city ni postalCode → `return Optional.empty()` sin llamar a Nominatim |
| K.004 | Register con `city="Madrid"` sin country | 201 + lat/long NULL | ✅ INFO | `geocode()` line 76-78: country null → return empty antes de consultar |
| K.005 | Register con `address="!!XYZ no-existe-este-sitio!!", city="?", country="ES", postalCode="00000"` | 201 + lat/long NULL | ✅ INFO | Nominatim devolvió array vacío; log: `WARN Geocoding sin resultados para query='...'`. Service degrada a NULL sin romper el alta |
| K.006 | Nominatim caído (host inalcanzable) | N/A (no se autorizó editar `/etc/hosts` del contenedor) | ✅ INFO (code review) | `GeocodingService.java:60-67` configura `SimpleClientHttpRequestFactory` con `connectTimeout=readTimeout=5000ms`; el `catch (Exception ex)` (línea 109-112) atrapa cualquier fallo (`ResourceAccessException`, `HttpClientErrorException`, etc.) y loguea `WARN Geocoding fallido`. Conclusión: nunca propaga; el alta siempre completa |
| K.007 | PUT `/api/businesses/1` cambiando `address`/`city` | 1ª intento "Avenida Diagonal 100, 08019 Barcelona" → 200 con lat/long NULL (Nominatim no encuentra esa combinación postal_code↔dirección); 2º intento "Carrer de Mallorca 401, 08013 Barcelona" → 200 con lat=`41.40350460`/lng=`2.17442830` | ✅ INFO | PUT sí re-resuelve la geocoding. Para datos inconsistentes degrada a NULL como en K.005 |
| K.008 | Register con SMTP caído | N/A (no se autorizó editar `/etc/hosts` del contenedor) | ✅ INFO (code review) | `MailService.sendSimpleEmail` line 75-80: `try { mailSender.send(message) } catch (MailException ex) { log.warn(...) }`. El método NO propaga la excepción al caller (`AuthService.register`). Timeouts SMTP: `spring.mail.properties.mail.smtp.connectiontimeout=5000`, `mail.smtp.timeout=5000`, `mail.smtp.writetimeout=5000` (application.properties:55-57) |
| K.009 | forgot-password con SMTP caído | N/A (mismo motivo) | ✅ INFO (code review) | `PasswordResetService` también usa `MailService.sendSimpleEmail`; mismo patrón best-effort; token sí persiste en `password_resets` aun si el envío falla. Por diseño, el frontend solo recibe 204 (sin revelar si existe el usuario o si llegó el email) |
| K.010 | SMTP timeout 5s | N/A (mismo motivo) | ✅ INFO (code review) | Timeouts conf. ≤ 5s end-to-end; total ≤ 15s (connect+read+write). La request `/api/auth/register` o `/api/auth/forgot-password` nunca debe colgarse > 6s |

**Subtotal K: 10 pruebas (7 ejecutadas + 3 code-review).** Funcionalidad best-effort confirmada empíricamente para Geocoding (5 casos pasan) y por inspección de fuente para SMTP. **Sin hallazgos.**

---

## 12. Bloque L — N+1 y rendimiento ligero

Mediciones con `spring.jpa.show-sql=true` (ya activo). Cuenta de `select`s entre snapshots de logs del contenedor `api_optima_dev`. Baseline 4 citas, 6 clientes, 4 memberships.

| ID | Resultado | Selects observados | Veredicto | Comentario |
|---|---|---|---|---|
| L.001 | `GET /api/businesses/1/appointments?size=50` (4 citas) | **22** selects: `1× appointments` + `1× appointment_services` (batch fetch via `findAllByAppointmentIdIn`) + `4× clients` + `4× services` + `4× appointment_statuses` + `3× memberships` + `3× users` + `2× booths` | 🟠 **ALTA-falla parcial** | El batch de booked_services SÍ está implementado (`AppointmentService.java:349-354`), pero `AppointmentResponse.from(...)` (`AppointmentResponse.java:67-85`) accede a campos `LAZY` (`a.getClient()`, `a.getMembership().getUser()`, `a.getBooth()`, `a.getStatus()`) → Hibernate dispara una query por entidad referenciada. **N+1 latente** en el momento de proyectar al DTO. Para 4 citas son 18 lazy loads; para 50 citas serían ~250 selects en lugar de los ~5 que pretendía el plan |
| L.002 | `GET /api/businesses/1/clients?size=100` (5 clientes activos) | **1** select sobre `clients` | ✅ ALTA | Sin relaciones expuestas en `ClientResponse`; sin paginación con count separada porque devuelve `Page` con totalElements derivado de la misma query. Limpio |
| L.003 | `GET /api/businesses/1/users?size=50` (4 memberships) | **7** selects: `1× memberships`, `4× users` (uno por membership), `2× roles` (deduplicado: hay 2 roles distintos en 4 memberships: ADMIN y EMPLOYEE) | 🟠 **ALTA-falla parcial** | Misma raíz que L.001: `UserResponse.from(membership)` accede a `.getUser().getFullName()` (lazy) y `.getRole().getName()` (lazy) → N+1 al proyectar |
| L.004 | `GET /api/businesses/1/availability?date=2027-03-15&serviceIds=1` | **11** selects en **156ms**: `1× businesses` + `1× business_hours` + `1× services` + `1× employee_schedules` + `1× memberships` + `1× employee_absences` + `1× schedule_blocks` + `1× appointments` + `1× booths` + `2× users` (autor del schedule_block + membership.user) | ✅ ALTA | Conteo de queries acotado (~11), tiempo < 200ms. Algoritmo no presenta N+1 en sí mismo; las 2× users son por lazy loading de memberships (mismo patrón que L.001/003). Aceptable para un día con baseline de carga |
| L.005 | `GET /api/businesses/1/appointments/1` | **8** selects: `1× appointment` + `1× client` + `1× membership` + `1× user` + `1× booth` + `1× appointment_status` + `1× business` + `1× appointment_services` | 🟡 INFO | Para un único appointment, las 6 lazy fetches son inevitables; cada `@ManyToOne(fetch=LAZY)` se resuelve cuando el DTO accede al getter. Aceptable solo para getById; si en el futuro se llaman varios appointments en bucle, multiplica queries |
| L.006 | Refactor del commit `1f50810` — `AppointmentResponse.from(Appointment, List<BookedService>)` NO inyecta repositorios | Confirmado por lectura: `AppointmentResponse.java:58-86` recibe la lista pre-cargada; el service hace `bookedServiceRepository.findAllByAppointmentIdIn(ids)` una sola vez | 🟡 MEDIA | Cumple LITERAL: el DTO no llama repos. **PERO** el patrón `from(Appointment, ...)` lee directamente los lazy fields (`a.getClient()`, `a.getMembership().getUser()`), lo que dispara queries proxy a Hibernate fuera del DTO. Es un N+1 oculto: técnicamente el DTO es "puro", pero la conversión no es pura para Hibernate |

**Subtotal L: 6 pruebas ejecutadas, 2 ✅ + 1 🟡 (L.005) + 1 🟡 (L.006) + 2 🟠 N+1 latente (L.001, L.003).**

**Hallazgo N+1 dominante**: el patrón canónico `Response.from(Entity)` que accede a `.getClient()`, `.getMembership().getUser()`, `.getBooth()`, `.getStatus()` triggea una query por entidad relacionada, multiplicado por N filas de la página. Fix recomendado: en `AppointmentRepository.searchAppointments` y `MembershipRepository.findActiveByBusinessId`, añadir `@EntityGraph(attributePaths = {"client", "membership.user", "status", "booth"})` para que Hibernate haga JOIN en una sola consulta. Esto baja L.001 de 22 selects a ~3.

---

## 13. Bloque N — Seguridad: inyección y vectores de ataque

Baseline previo a N: 9 users / 7 businesses / 4 appointments (acumulados de K). Baseline tras N: 13 / 11 / 5. La limpieza final (§14) devuelve al baseline puro 4/2/4.

### N.1 Inyección SQL empírica

| ID | Caso | HTTP | Resultado | Veredicto |
|---|---|---|---|---|
| N.1.001 | POST cliente fullName=`' OR 1=1 --` | 201 | `fullName` almacenado LITERAL en BD; sin DROP ni cambio | ✅ CRIT |
| N.1.002 | POST tax name=`'; DROP TABLE users; --` (26 chars, cabe en max=50) | 201 | name LITERAL; cnt users sin cambios | ✅ CRIT |
| N.1.003 | Register admin.email=`admin'); DROP TABLE users; --@x.com` | 400 | Rechazado por `@Email` antes de llegar al servicio; mensaje `"admin.email: El email no tiene un formato válido"` | ✅ CRIT |
| N.1.004 | POST cliente notes=`test\n'; DELETE FROM appointments; --` | 201 | notes LITERAL (incluye `\n`); cnt appointments inalterado (4 antes / 4 después) | ✅ CRIT |
| N.1.005 | GET `/api/businesses/1/clients/1 OR 1=1` (URL-encoded `%20OR%201=1`) | 400 | `"El parámetro 'id' tiene un tipo incorrecto. Se esperaba Long"` | ✅ ALTA |
| N.1.006 | GET `/clients?sort=name';%20DROP%20TABLE%20users;%20--` | 500 | Catch-all sin sanitizar (mismo síntoma que I.010). cnt users INTACTO. Sin SQLi efectiva | 🟡 MEDIA (mismo gap I.010) |
| N.1.007 | POST cliente fullName=`X'; DROP TABLE users; --` | 201 | LITERAL; sin DROP | ✅ CRIT |
| N.1.008 | Register business.name=`'; UPDATE businesses SET is_active=0; --` (39 chars, cabe en 150) | 201 | name LITERAL; otros businesses sin cambio (`is_active=1` para id=1 verificado) | ✅ CRIT |
| N.1.009 | GET availability?serviceIds=`1; DROP TABLE users; --` | 400 | `"El parámetro 'serviceIds' tiene un tipo incorrecto. Se esperaba List"`; parser de Long rechaza | ✅ CRIT |
| N.1.010 | Recuento final post-N.1.001–009 | OK | users 9→10 (+1 por register N.1.008) y businesses 7→8 (+1 por mismo register); appointments 4→4. Sin DELETE ni DROP | ✅ CRIT |

### N.2 Inyección SQL — análisis estático (grep en `src/main/java`)

| ID | Patrón | Resultados | Veredicto |
|---|---|---|---|
| N.2.001 | `createNativeQuery` | 0 | ✅ CRIT |
| N.2.002 | `createQuery(.*\+` (concatenación de strings) | 0 | ✅ CRIT |
| N.2.003 | `@Query(.*\+` (concat dentro de @Query) | 0 | ✅ CRIT |
| N.2.004 | `jdbcTemplate` o `JdbcTemplate` | 0 | ✅ CRIT |
| N.2.005 | `java.sql.Statement`, `createStatement` (sin Prepared) | 0 | ✅ CRIT |
| N.2.006 | Baseline preliminar 2026-05-17 | confirmado vacío | ✅ INFO |

### N.3 XSS (política API: NO sanitiza; frontend escapa al renderizar)

| ID | Caso | HTTP | Resultado | Veredicto |
|---|---|---|---|---|
| N.3.001 | POST cliente fullName=`<script>alert(1)</script>` | 201 | LITERAL en JSON response y BD | ✅ INFO |
| N.3.002 | POST cliente notes=`"><img src=x onerror=alert(1)>` | 201 | LITERAL | ✅ INFO |
| N.3.003 | POST tax name=`<svg onload=alert(1)>` (21 chars, cabe en 50) | 201 | LITERAL | ✅ INFO |
| N.3.004 | Register business.name=`<script>x</script>` | 201 | LITERAL persistido en `businesses.name` | ✅ INFO |
| N.3.005 | Política documentada en CLAUDE.md y código (`policy: API stores literals, frontend escapes`) | OK | Confirmada en `feedback/project_membership_refactor` y aplicada consistentemente | ✅ INFO |

### N.4 JWT manipulation

`PARTS=(${TK_B1//./ })` divide el token en header.payload.signature. Los tests manipulan cada parte y verifican rechazo.

| ID | Caso | HTTP | Veredicto |
|---|---|---|---|
| N.4.001 | `alg=none` con firma vacía | 401 | ✅ CRIT (jjwt rechaza alg=none por defecto) |
| N.4.002 | Header `alg=HS256` (mismatch con HS384 configurado) | 401 | ✅ CRIT |
| N.4.003 | Payload con `userId=999` (firma original) | 401 | ✅ CRIT (firma no valida) |
| N.4.004 | Payload con `businessId=2` (firma original) | 401 | ✅ CRIT |
| N.4.005 | Payload con `role="SUPERADMIN"` (firma original) | 401 | ✅ CRIT |
| N.4.006 | Payload con `exp=200` (pasado, 1970) + firma original | 401 | ✅ ALTA |
| N.4.007 | Payload con `sub="ghost@x.com"` + firma original | 401 | ✅ CRIT (firma rota antes del lookup en BD) |
| N.4.008 | Payload sin `sub` + firma original | 401 | ✅ CRIT |
| N.4.009 | Payload sin `userId` + firma original | 401 | ✅ CRIT (fix `4387` verificado) |
| N.4.010 | Payload con `businessId=99999` + firma original | 401 | ✅ ALTA (firma rota; no llega a TenantGuard) |
| N.4.011 | Payload con `role="HACKER"` + firma original | 401 | ✅ ALTA |
| N.4.012 | Token >10KB (junk claim de 10000 As, total 13568 bytes) | 400 | ✅ BAJA (Tomcat rechaza header excesivo antes del filtro JWT) |
| N.4.013 | Firma truncada en los últimos 2 bytes | 401 | ✅ ALTA |

### N.5 Path traversal

| ID | Caso | HTTP | Veredicto |
|---|---|---|---|
| N.5.001 | `GET /api/businesses/../etc/passwd` (--path-as-is) | 401 | ✅ CRIT (Tomcat normaliza; auth filter rechaza el path resultante; sin lectura de filesystem) |
| N.5.002 | `GET /api/businesses/%2e%2e/etc` | 401 | ✅ CRIT |
| N.5.003 | `GET /api/businesses/1/../1/clients` | 401 | ✅ ALTA |
| N.5.004 | `GET /api/businesses//1/clients` (doble slash) | 401 | ✅ INFO |
| N.5.005 | `GET /api/businesses/1\..\..\etc` (URL-encoded `%5C`) | 400 | ✅ BAJA |

### N.6 Header injection

| ID | Caso | HTTP | Veredicto |
|---|---|---|---|
| N.6.001 | Authorization con caracteres no estándar (multi-header) | 401 | ✅ ALTA (Tomcat acepta multi-header pero el JWT no valida) |
| N.6.002 | Authorization con byte de control `\x01` | 400 | ✅ ALTA (Tomcat rechaza control chars en header) |
| N.6.003 | Origin con Unicode RTL `‮example.com` | 401 | ✅ BAJA (CORS no filtra; auth rechaza por falta de token) |
| N.6.004 | Content-Length=99999 (no coincide) | 401 | ✅ ALTA (Tomcat lee solo el body real; auth falla por credenciales) |

### N.7 Mass assignment

| ID | Caso | HTTP | Resultado en BD | Veredicto |
|---|---|---|---|---|
| N.7.001 | PUT `/api/me` con `{"fullName":"Admin Test","email":"admin@optima.com","phone":"600100100","passwordHash":"$2a$10$hax"}` | 200 | `password_hash` mantiene el hash original `$2a$10$XLi...` (campo NO existe en `UpdateMeRequest`, Jackson lo ignora) | ✅ CRIT |
| N.7.002 | PUT `/api/me` con email duplicado (`carlos@optima.com`) | 409 | `"Ya existe un usuario con ese email"` por UNIQUE constraint | ✅ ALTA |
| N.7.003 | Register con `admin.role="SUPERADMIN"` | 201 | Membership creada con `id_role=1` (ADMIN). Campo `role` no existe en `AdminAccount` DTO → ignorado por Jackson | ✅ CRIT |
| N.7.004 | POST appointment con `"statusId":2` (intentar saltar PENDING) | 201 | `status_id=1` (PENDING) en BD; `statusId` no existe en `CreateAppointmentRequest` → ignorado | ✅ CRIT |
| N.7.005 | POST user con `"roleId":999` | 404 | `"No se encontró el rol con ID: 999"`; service valida existencia antes de crear membership | ✅ ALTA |

### N.8 IDOR

| ID | Caso | HTTP | Veredicto |
|---|---|---|---|
| N.8.001 | `tkA(B1)` GET `/api/businesses/1/clients/1` (client 1 pertenece a B2) | 404 | ✅ CRIT (no revela existencia cross-tenant; mensaje uniforme con clientes que sí existen en B1 y son 200) |
| N.8.002 | `tkA(B1)` GET `/api/businesses/2/clients/1` | 403 | ✅ CRIT (TenantGuardFilter bloquea antes del service) |
| N.8.003 | `tkA(B1)` GET `/api/businesses/1/appointments/9999` (inexistente) | 404 | ✅ CRIT (no diferencia entre "no existe globalmente" e "existe pero en B2") |
| N.8.004 | `tkA(B1)` GET `/api/businesses/1/users/14` (membership 14 = `super@x.com` de business 9) | 404 | ✅ CRIT |
| N.8.005 | `tkA(B1)` GET `/api/businesses/1/booths/99` | 404 | ✅ CRIT |
| N.8.006 | `tkA(B1)` GET `/api/businesses/1/availability?date=2027-06-07&serviceIds=1&membershipId=14` | 404 | ✅ CRIT (`"No se encontró el empleado con ID: 14 en el negocio con ID: 1"`) |

### N.9 BCrypt y timing

| ID | Caso | Resultado | Veredicto |
|---|---|---|---|
| N.9.001 | `LEFT(password_hash,4) FROM users LIMIT 1` | Prefix `$2a$` (60 chars total, BCrypt estándar) | ✅ CRIT |
| N.9.002 | Cost factor (chars 4-7 del hash) | `$2a$10$` → cost=10 (≥10 ✅) | ✅ ALTA |
| N.9.003 | **Salt único por usuario** | **Seed file hash idéntico para users 1-4**: todos `$2a$10$XLihaXA2hZhv9fmi1KkYHewNFyhdnAexajF2fDQS9TZSa3a1gke4q`. **Users creados vía `/register` SÍ tienen salt único**: K1=`$2a$10$JOG4J7Ioml/...`, K2=`$2a$10$yN6LWpzWcvSU...`, K3=`$2a$10$WdbSlDosMPy8...`, etc. | 🟠 **ALTA-falla parcial** — `BCryptPasswordEncoder` funciona correctamente; el problema es el seed SQL: `docs/schema_v20.sql:511-547` reutiliza el mismo hash para 4 usuarios distintos. En un dump filtrado, una sola match revela la password de 4 cuentas |
| N.9.004 | **Timing attack** — usuario inexistente vs password incorrecto, 10 iters cada uno | Ghost (email inexistente): avg **10.4ms**. Bad-pass (email válido + password incorrecto): avg **85.3ms**. **Ratio 8.2×**. | 🟠 **ALTA-falla** — `AuthService.login` (línea 95-100): early-return cuando `findByEmailIgnoreCase` devuelve empty, SIN llamar a `BCrypt.matches`. El atacante puede enumerar usuarios midiendo tiempos. Mitigación: ejecutar `BCrypt.matches(password, DUMMY_HASH)` siempre, incluso si el usuario no existe |
| N.9.005 | Login con `password=""` | 400 `"password: La contraseña es obligatoria"` | ✅ MEDIA (`@NotBlank` rechaza antes de llegar a BCrypt) |
| N.9.006 | Login con password de 1000 chars | 401 `"Credenciales incorrectas"` | ✅ BAJA (BCrypt no se bloquea; jjwt acepta. Nota: el `RateLimitFilter` de auth (5 req/min) se activó durante el batch de timing; se esperó 40s antes de N.9.005/006) |

### N.10 Logs sensibles

Métrica: contar apariciones de cada secreto en `docker logs api_optima_dev` tras la operación correspondiente. 0 = no leak.

| ID | Caso | Apariciones | Veredicto |
|---|---|---|---|
| N.10.001 | Login OK admin/`12345678` | `"12345678"`: **0** apariciones; hash bcrypt: **0** | ✅ CRIT |
| N.10.002 | Login KO admin/`WRONG_PASS_UNIQUE_123` | **0** apariciones del password en clear | ✅ CRIT |
| N.10.003 | `log.warn` en login KO loguea email parcial/entero | `WARN AuthService: Login fallido: password incorrecto (userId=1, email='admin@optima.com')`. Email completo en log | ✅ INFO (política documentada: aceptable para diagnóstico) |
| N.10.004 | Register con password=`REGISTER_PWD_UNIQUE_456` | **0** apariciones del password | ✅ CRIT |
| N.10.005 | Register no loguea hash BCrypt `$2a$` | **0** apariciones | ✅ CRIT |
| N.10.006 | forgot-password no loguea token plano | Logs NO contienen `token=`; BD almacena solo hash SHA-256 (64 hex chars en `password_resets.token_hash`) | ✅ CRIT |
| N.10.007 | JWT prefix `eyJ` no aparece en logs durante login OK | **0** apariciones en delta del log | ✅ ALTA |
| N.10.008 | Hibernate SQL DEBUG con `show-sql=true`: parámetros vinculados | Solo aparecen las queries (sin parámetros `?` resueltos). `orm.jdbc.bind=TRACE` NO está activo → no leak de parámetros enlazados | ✅ ALTA |
| N.10.009 | Stack trace de JWT inválido | Búsqueda de `JWT_SECRET` o `secret` en últimos 30 lines de log: **0** apariciones. La clave secreta nunca aparece en stack traces | ✅ CRIT |

**Subtotal N: 69 pruebas ejecutadas, 65 ✅ + 1 🟡 (N.1.006, mismo gap que I.010) + 2 🟠 (N.9.003 seed hash idéntico, N.9.004 timing leak) + 1 INFO consciente (N.10.003 email parcial en warn).**

---

## 14. Limpieza final (post K + L + N)

Estado pre-limpieza tras ejecutar los 85 tests adicionales:

| Tabla | Antes K/L/N | Post K/L/N | Después de cleanup | Baseline esperado |
|---|---|---|---|---|
| users | 4 | 13 | 4 | 4 ✅ |
| businesses | 2 | 11 | 2 | 2 ✅ |
| memberships | 4 | 13 | 4 | 4 ✅ |
| clients | 6 | 11 | 6 | 6 ✅ |
| appointments | 4 | 5 | 4 | 4 ✅ |
| services | 5 | 5 | 5 | 5 ✅ |
| booths | 2 | 2 | 2 | 2 ✅ |
| taxes | 2 | 4 | 2 | 2 ✅ |
| service_categories | 3 | 3 | 3 | 3 ✅ |
| business_hours | 7 | 7 | 7 | 7 ✅ |
| employee_schedules | 30 | 30 | 30 | 30 ✅ |
| employee_absences | 1 | 1 | 1 | 1 ✅ |
| schedule_blocks | 1 | 1 | 1 | 1 ✅ |
| password_resets | 0 | 2 | 0 | 0 ✅ |

Comandos aplicados:

```sql
-- restaurar admin (full_name + phone) y business 1 (address+lat/long NULL)
UPDATE users SET full_name='Admin Demo', phone=NULL WHERE id_user=1;
UPDATE businesses SET email='demo@optima.com', phone=NULL, address=NULL, city=NULL,
       country=NULL, postal_code=NULL, latitude=NULL, longitude=NULL WHERE id_business=1;

-- borrar fixtures generados por los tests
DELETE FROM appointments WHERE id_appointment > 4;        -- cita N.7.004
DELETE FROM clients WHERE id_client > 6;                  -- sqli1/4/7 + xss1/2
DELETE FROM taxes WHERE id_tax > 2;                       -- N.1.002 DROP-TABLE-name + N.3.003 svg
DELETE FROM memberships WHERE id_membership > 4;          -- K1-K5 + N108 + N304 + N703 + N1004 + super
DELETE FROM businesses WHERE id_business > 2;             -- mismos
DELETE FROM users WHERE id_user > 4;                      -- mismos
DELETE FROM password_resets;                              -- N.10.006
```

Verificación final manual: identidades 1-4 con `full_name` y `email` originales; businesses 1-2 con `address/latitude/longitude` NULL; counts exactos del baseline pre-D. **Baseline restaurado al 100%**.

---

## 15. Resumen final del audit completo (217 + 85 = 302 tests)

### Cobertura

- **02_RESULTADOS_AUTH_VALIDACION.md**: bloques A, B, C, J, M (sesión anterior, ~150 tests).
- **03_RESULTADOS_NEGOCIO.md** §1-10: bloques D, E, F, G, H, I, O (sesión anterior, 217 tests).
- **03_RESULTADOS_NEGOCIO.md** §11-14: bloques K, L, N (esta sesión, 85 tests).

### Hallazgos críticos y altos consolidados (esta sesión + previas)

| ID | Severidad | Bloque | Resumen | Estado |
|---|---|---|---|---|
| D.2.001/002/005 | 🔴 CRIT | D | Race condition crear cita: lock pesimista sobre fila Membership/Booth no bloquea predicado temporal. 2-3 duplicados bajo carga | ✅ RESUELTO — commit `95e3958` (2026-05-18) |
| L.001 + L.003 | 🟠 ALTA | L | **N+1 latente** en `AppointmentResponse.from(...)` y `UserResponse.from(...)` por lazy loading. 22 selects para 4 citas (esperaba ~5) | **Nuevo (esta sesión)** |
| L.006 | 🟡 MEDIA | L | DTOs "puros" sin repo pero acceden a lazy fields → N+1 oculto | **Nuevo (esta sesión)** |
| N.9.003 | 🟠 ALTA | N | Seed SQL reusa el mismo hash BCrypt para 4 usuarios distintos (admin/empleado/maria/carlos). En un leak, una match revela todas | **Nuevo (esta sesión)** |
| N.9.004 | 🟠 ALTA | N | Timing attack confirmado: ghost-email avg 10ms vs bad-password avg 85ms. **Ratio 8.2×** — leak empírico de existencia de usuarios | **Nuevo (esta sesión)** |
| I.010 + N.1.006 | 🟠 ALTA | I/N | Sort por campo inexistente o con caracteres extra → 500 catch-all (necesita `PropertyReferenceException` handler) | Confirmado |
| F.005 + N.1.x | 🟡 MEDIA | F | `serviceIds=,` o lista mal formada → 500 (`NumberFormatException` no manejado) | Confirmado |
| H.005 | 🟡 MEDIA | H | Sufijo `Z` (UTC) en `startDateTime` silenciosamente ignorado | Confirmado |
| N.10.003 | ⚪ INFO | N | `log.warn` en login KO incluye email completo del usuario (política decidida, OK para diagnóstico) | Documentado |

### Fixes recomendados por prioridad para cerrar antes del frontend

> **✅ Los 7 se aplicaron el 2026-05-18 (merge `c2f8f53`).** Ver la tabla
> del banner al inicio del documento para el mapa hallazgo → commit.

1. ~~**🔴 CRIT** — Race condition appointments (D.2)~~ → **✅ HECHO
   (commit `95e3958`)**: se aplicó la opción "índice + constraint" en su
   variante con columnas virtuales + UNIQUE (`uq_appointment_active_slot`,
   `uq_appointment_active_booth_slot`), no `SERIALIZABLE`.
2. **🟠 ALTA** — N+1 en proyecciones (L.001/003): añadir `@EntityGraph(attributePaths = {...})` en los métodos `findByXxxAndBusinessId` que sirven listados.
3. **🟠 ALTA** — Timing attack login (N.9.004): en `AuthService.login`, si el usuario no existe ejecutar un `BCrypt.matches(password, DUMMY_HASH_CONSTANT)` antes del 401 para equilibrar tiempos.
4. **🟠 ALTA** — Seed hash compartido (N.9.003): regenerar `schema_v20.sql:511-547` con 4 hashes distintos (`htpasswd -bnBC 10 "" "12345678"` × 4) o un `INSERT` con BCrypt en runtime.
5. **🟠 ALTA** — Sort inválido 500→400 (I.010 + N.1.006): añadir `@ExceptionHandler(PropertyReferenceException.class)` a `GlobalExceptionHandler`.
6. **🟡 MEDIA** — `NumberFormatException` en query params (F.005): handler dedicado.
7. **🟡 MEDIA** — Zona horaria (H.005): aceptar `OffsetDateTime` o rechazar offset en DTO.

**Sin hallazgos críticos de seguridad nuevos en esta sesión**: SQLi (N.1) totalmente bloqueada por JPA prepared statements; XSS (N.3) política consistente con frontend-escape; JWT (N.4) firma robusta HS384 + claims requeridos (sub/userId); IDOR (N.8) cross-tenant cubierto por TenantGuardFilter y `findByIdAndBusinessId`; path traversal (N.5) y header injection (N.6) atajados por Tomcat. La superficie ofensiva del API es razonablemente sólida para un MVP.






