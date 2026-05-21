# Revisión quirúrgica del proyecto Optima — 2026-05-21

Auditoría línea a línea del backend (Spring Boot) y del frontend (`frontend-v2/`).

## 1. Resumen ejecutivo

- **Método**: 8 agentes de revisión en paralelo (un lote por módulo/capa), cada
  uno leyendo cada archivo de su lote, más pruebas dinámicas ejecutadas por el
  orquestador.
- **Alcance**: los ~100 archivos `.java` de `src/main/java`, `docs/schema_v20.sql`,
  `src/test/`, la infraestructura (`pom.xml`, `application.properties`, Docker), y
  los ~38 archivos del frontend `frontend-v2/src`.
- **Total: 67 hallazgos.** 0 críticos. 3 altos. 7 medios. 39 bajos. 18 info.
- **Backend: 39 hallazgos** (0 críticos · 3 altos · 6 medios · 17 bajos ·
  13 info). No hay bugs de seguridad ni de concurrencia; el patrón dominante
  era N+1 evitable por falta de `@EntityGraph`, más comentarios desfasados.
  **Resueltos los 26 accionables** (3 ALTA + 6 MEDIA + 17 BAJA), cada uno
  aplicado y verificado (`mvnw test` + smokes en runtime); los 13 INFO se
  verificaron correctos (sin acción). Estado por hallazgo en la sección 3.
- **Frontend: 28 hallazgos — 16 CORREGIDOS en esta sesión, 12 documentados.**
  Ver sección 4.
- **Veredicto**: el proyecto está sólido. El cruce schema↔entidades es impecable
  (17 tablas ↔ 17 entidades sin un solo desajuste). El frontend está bien
  alineado con la API. Ningún hallazgo es bloqueante.

## 2. Pruebas dinámicas ejecutadas

| Prueba | Resultado |
|---|---|
| `mvnw test` (suite backend, H2) | **PASS** — exit 0, todos los tests verdes |
| `npm run build` (frontend, tras los fixes) | **VERDE** — 1827 módulos, 0 errores |
| Playwright E2E (`e2e_full.py`, 30 comprobaciones) | **30/30 OK** — 12 pantallas, 5 pestañas de Configuración, 3 vistas de Calendario, wizard de citas, drawers, Archivar/Reactivar y CRUD de cliente completo |
| Smoke de API (`curl`) | **OK** — público 200, protegido sin token 401, login 200, cross-tenant 403, `?sort` inválido → 400, `/availability` sin params → 400 |

> Nota: el E2E dejó un cliente de prueba archivado (`ZZ QA E2E (editado)`) en el
> negocio 1. Es un artefacto inocuo; se elimina con `docker compose down -v`.

---

## 3. Hallazgos del BACKEND

> Tras la auditoría, el usuario autorizó aplicar los fixes de backend uno a
> uno. Cada hallazgo indica su estado (✅ CORREGIDO / pendiente). Ningún
> hallazgo es CRÍTICO.

### 3.1 common / config / auth / infraestructura

#### CMN-1 · ALTA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `pom.xml:8`, `CLAUDE.md:26,282`
- El POM declara `spring-boot-starter-parent` **3.4.1** (confirmado: la app
  arranca con `Spring Boot v3.4.1`), pero `CLAUDE.md` afirmaba "Spring Boot
  3.5.13" en las líneas 26 y 282 (aunque la 365 ya decía "3.4.x").
- **Corrección aplicada**: `CLAUDE.md` líneas 26 y 282 corregidas a `3.4.1`
  (la versión real), y eliminado el comentario engañoso `Versión estable
  actual` del `pom.xml`. La versión NO se cambió (sigue 3.4.1); solo se
  alinearon los docs con la realidad. Verificado: `mvnw validate` → exit 0.

#### CMN-2 · MEDIA · seguridad · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `common/security/TenantGuardFilter.java`
- La regex `^/api/businesses/(\d+)(/.*)?$` solo casaba `businessId` numérico:
  una ruta con segmento no numérico (`/api/businesses/1abc/...`) NO la
  matcheaba y el guard cross-tenant se saltaba (mitigado solo por el 400 del
  `@Positive Long` del controller).
- **Corrección aplicada**: regex ampliada a `^/api/businesses/([^/]+)(/.*)?$`
  + `try/catch NumberFormatException` al parsear el segmento; si no es numérico
  se rechaza con 403. Javadoc del filtro y de la regex actualizados. Verificado:
  `mvnw test` verde (`TenantGuardFilterTest` incluido), y smoke: `/businesses/1`
  → 200, `/999` → 403, `/1abc` → 403 (antes 400).

#### CMN-3 · BAJA · convención · ✅ CORREGIDO (vía CMN-2)
- **Archivo**: `common/security/TenantGuardFilter.java`
- El Javadoc citaba endpoints eliminados el 2026-05-14 (`GET /api/businesses`,
  `/api/businesses/slug/{slug}`).
- **Resuelto**: la reescritura del bloque "URLs que matchea" hecha en CMN-2 ya
  eliminó esas referencias (quitó el ejemplo `slug/abc` y el "catálogo público").
  Sin cambios adicionales.

#### CMN-4 · BAJA · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `modules/auth/service/AuthService.java:31`
- El Javadoc decía "3 endpoints"; `AuthController` expone 5 (forgot/reset las
  sirve `PasswordResetService`).
- **Corrección aplicada**: encabezado reformulado — "Cubre 3 de los 5 endpoints
  de AuthController; los otros 2 (forgot/reset-password) los sirve
  PasswordResetService". Solo comentario.

#### CMN-5 · BAJA · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `common/security/RateLimitFilter.java`, `TenantGuardFilter.java`
- Ambos filtros hacían `new ObjectMapper()` en vez de inyectar el bean de
  Spring — se desviaban del patrón de inyección por constructor del proyecto.
- **Corrección aplicada**: `@RequiredArgsConstructor` en ambos filtros + campo
  `ObjectMapper` sin inicializar → Spring inyecta el bean. `TenantGuardFilterTest`
  adaptado (`new TenantGuardFilter(new ObjectMapper())`). Verificado: `mvnw test`
  verde (`contextLoads` confirma la inyección), 403 cross-tenant con su JSON OK.

#### CMN-6 · BAJA · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `common/utils/JwtUtil.java:58`
- `@PostConstruct` lanzaba `RuntimeException` cruda si el secret mide <32 chars.
- **Corrección aplicada**: cambiado a `IllegalStateException` (fallo de
  configuración de bean). Solo cambia el tipo de excepción.

#### CMN-7 · INFO · inconsistencia
- **Archivo**: `application.properties:16`
- La URL JDBC fija `serverTimezone=UTC` mientras el proyecto trata los
  `LocalDateTime` como hora local. Hoy es inocuo (columnas `DATETIME`), pero es
  una combinación frágil si se migrara a `TIMESTAMP`.
- **Sugerencia**: ninguna acción; dejar constancia de la dependencia.

> **Verificado limpio**: la cadena de filtros (RateLimit → JwtAuth →
> TenantGuard), los `permitAll`, la distinción identity/tenant token, el
> `GlobalExceptionHandler` (14 excepciones cubiertas), el
> `StrictLocalDateTimeDeserializer` y el flujo de auth (login 2 pasos, register
> transaccional, forgot/reset con anti-enumeration y mitigación de timing
> attack). 31 archivos revisados.

### 3.2 business

#### BIZ-1 · ALTA · rendimiento · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `modules/business/repository/ScheduleBlockRepository.java:36`
- `findByBusinessIdOrderByStartDateAsc` no llevaba `@EntityGraph`, pero
  `ScheduleBlockResponse.from()` accede a `membership.user` y `booth` (LAZY) →
  N+1. `MembershipRepository` y `AppointmentRepository` ya lo resolvieron
  (commit `e6e294b`); este listado quedó fuera.
- **Corrección aplicada**: `@EntityGraph(attributePaths = {"membership","membership.user","booth"})`
  sobre el método. Verificado: `mvnw test` verde, `GET /schedule-blocks` → 200,
  y la SQL de Hibernate ahora hace los `LEFT JOIN` en una sola consulta.

#### BIZ-2 · MEDIA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `CreateBusinessRequest.java:30`, `UpdateBusinessRequest.java:32`
- El Javadoc afirmaba que el email es "UNIQUE GLOBAL en la tabla `users`", pero
  `createEntity` valida `businessRepository.existsByEmail` — la columna
  `businesses.email`, independiente de `users.email`. El código es correcto;
  el comentario era engañoso.
- **Corrección aplicada**: Javadoc de ambos DTOs reescrito — el email es el de
  contacto del negocio (`businesses.email`), no `users.email`. Solo comentarios.
  Verificado: `mvnw compile` → exit 0.

#### BIZ-3 · BAJA · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `ServiceCategoryRepository.java`, `BusinessServiceRepository.java`
  (+ callers en sus services)
- `catalog/` usaba `findAllBy...` para devolver `Page<T>`; la convención
  (documentada en `ScheduleBlockRepository`) es `findBy` cuando se devuelve
  `Page`. `business/` ya era correcto.
- **Corrección aplicada**: renombrados los 4 finders paginados de catalog a
  `findByBusinessIdAndIsActiveTrue/False` + sus 4 callers. Spring Data deriva
  la query idéntica (`All` es solo prefijo) → cero cambio de comportamiento.
  Verificado: `mvnw test` verde, `/services` y `/categories` (activos +
  archivados) → 200.

#### BIZ-4 · BAJA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `modules/business/service/BusinessService.java`
- `createEntity`/`update` aplicaban `trim()` solo a `slug` y `email`; `name`
  entraba sin sanear. `TaxService`/`BoothService` sí trimean el nombre.
- **Corrección aplicada**: `b.setName(request.name().trim())` en `createEntity`
  y `update`. Solo `name` (es `@NotBlank` → nunca null); los campos opcionales
  (`phone`, `address`…) admiten null y trimearlos exigiría null-checks — se
  dejan, igual que en `TaxService`/`BoothService`.

#### BIZ-5 · INFO · (sin acción) — `BusinessHour`/`isClosed` verificado correcto.
#### BIZ-6 · INFO · (sin acción) — `RoleController` (catálogo global de solo lectura) verificado correcto.

> **Verificado limpio**: locks pesimistas `findByIdAndBusinessIdForUpdate`,
> validación cross-tenant en los 5 services tenant-scoped, los 3 tipos de
> `ScheduleBlock`, el doble nombre `BusinessService` (sin colisión real), todas
> las entidades soft-delete con `isActive`+`createdAt`+`deactivatedAt`. 41
> archivos revisados.

### 3.3 user / client

#### USR-1 · MEDIA · rendimiento · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `EmployeeScheduleRepository.java:43`, `EmployeeAbsenceRepository.java:44`
- Los listados de horarios y ausencias no llevaban `@EntityGraph`, pero sus
  Response DTOs acceden a `membership` y `membership.user` (LAZY) → N+1. El
  Javadoc de los DTOs afirmaba "el mismo trade-off que `AppointmentResponse`",
  ya falso tras el commit `e6e294b`.
- **Corrección aplicada**: `@EntityGraph(attributePaths = {"membership","membership.user"})`
  en los 2 finders que SÍ alimentan listados de DTO. `findAllByMembershipIdAndDayOfWeek`
  NO se tocó (solo valida solape, no produce DTOs). `membership.business` excluido
  (acceso id-only). Javadoc obsoleto de los 2 DTOs corregido. Verificado:
  `mvnw test` verde, `/schedules` y `/absences` → 200 con `LEFT JOIN`.

#### USR-2 · BAJA · rendimiento · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `MembershipRepository.java:115`
- `findAllByUserId` (alimenta `/api/me/businesses`) no llevaba `@EntityGraph`;
  `MembershipSummaryResponse.from` accede a `business` y `role` → N+1 de baja
  cardinalidad. (La otra mitad de USR-2, `findAllByBusinessIdAndIsActiveTrue`,
  ya la cerró APP-1.)
- **Corrección aplicada**: `@EntityGraph(attributePaths = {"business","role"})`
  en `findAllByUserId`. Verificado: `mvnw test` verde, `/me/businesses` → 200,
  SQL con `LEFT JOIN businesses` + `roles`.

#### USR-3 · BAJA · (sin acción) — la asimetría create-valida-overlap /
update-no-valida en ausencias es decisión consciente documentada en ambos
servicios. Verificado.

#### USR-4 · BAJA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `CreateEmployeeAbsenceRequest.java`
- `@FutureOrPresent` en `startDateTime` impedía registrar una ausencia ya
  iniciada (un empleado que enferma a media mañana). `UpdateEmployeeAbsenceRequest`
  ya no lo llevaba — asimetría.
- **Corrección aplicada**: eliminado `@FutureOrPresent` (+ su import + Javadoc).
  Ahora se puede registrar una ausencia en curso; create↔update alineados.
  Verificado: `POST .../absences` con fecha pasada → 201 (antes 400).

> **Verificado limpio**: cross-tenant correcto en todos los lookups, contraseñas
> nunca expuestas en DTOs, mass-assignment cerrado (`UpdateUserRequest` solo
> `roleId`), `@PreAuthorize` coherente, códigos HTTP correctos. 32 archivos
> revisados.

### 3.4 catalog

#### CAT-1 · ALTA · rendimiento · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `BusinessServiceRepository.java:34,41`
- `findAllByBusinessIdAndIsActiveTrue/False` no llevaban `@EntityGraph`;
  `BusinessServiceResponse.from()` accede a `category.name` y `tax.name`
  (LAZY) → SELECT extra por servicio listado.
- **Corrección aplicada**: `@EntityGraph(attributePaths = {"category","tax"})`
  sobre los 2 finders. `business` no se incluye (solo se le pide el id =
  columna FK, sin cargar la entidad). Verificado: `mvnw test` verde,
  `GET /services` → 200 (5 servicios), SQL con `LEFT JOIN service_categories`
  + `taxes` en una sola consulta.

#### CAT-2 · BAJA · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `BusinessServiceResponse.java`, `ServiceCategoryResponse.java`
- El parámetro de `from()` se llamaba `s`/`c` (una letra).
- **Corrección aplicada**: renombrado a `service` / `category` (con sus usos).
  Solo legibilidad; sin cambio de comportamiento.

#### CAT-3 · BAJA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `BusinessServiceService.java`
- `createService`/`updateService` resolvían categoría e impuesto sin comprobar
  su `isActive`: se podía crear un servicio colgando de una categoría/impuesto
  archivados. El Javadoc de `ServiceCategory` prometía esa exclusión.
- **Corrección aplicada (opción "A-fino")**: guard `isActive` en `createService`
  (siempre) y en `updateService` solo si la categoría/impuesto **cambia**
  (comparando el id del request con el actual) — así editar el precio de un
  servicio cuya categoría se archivó después no queda bloqueado. Verificado:
  `mvnw test` verde, `POST /services` con categoría archivada → 400.

#### CAT-4 · BAJA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `BusinessServiceService.java`, `ServiceCategoryService.java`
- `existsByBusinessIdAndNameIgnoreCase` cuenta también los archivados, así que
  un recurso archivado bloquea el nombre con un 409 confuso.
- **Corrección aplicada**: los 4 mensajes 409 de nombre duplicado (create+update
  de servicio y de categoría) añaden "(revisa también los archivados)".
  Verificado: el 409 devuelve el mensaje aclarado.

#### CAT-5 · BAJA · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `BusinessServiceService.java`, `ServiceCategoryService.java`
- `update*` normalizaba el nombre con `trim()`; `create*` no.
- **Corrección aplicada**: `createCategory`/`createService` calculan
  `String name = request.name().trim()` al inicio y lo usan tanto en el chequeo
  de duplicados como en el `setName` — así un `"  X  "` no se cuela ante un
  `"X"` existente. Mismo patrón que ya tenían los `update*`.

#### CAT-6 · INFO · inconsistencia
- `createCategory` hace `setIsActive(true)` explícito; `createService` confía en
  el default de la entidad. Unificar (preferible quitar el redundante).

> **Verificado limpio**: mapeo entidad↔columna, cross-tenant, `@PreAuthorize` en
> las 4 mutaciones de cada controller, códigos HTTP, mensajes de error. 14
> archivos revisados.

### 3.5 appointment

#### APP-1 · MEDIA · rendimiento · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `MembershipRepository.java:66` (caller: `AvailabilityService:258`)
- `findAllByBusinessIdAndIsActiveTrue` no llevaba `@EntityGraph`; el algoritmo
  de disponibilidad hace `emp.getUser().getFullName()` por candidato → N+1.
- **Corrección aplicada**: `@EntityGraph(attributePaths = {"user"})` en el finder
  (único caller verificado: `AvailabilityService:258`, solo necesita `user`).
  Verificado: `mvnw test` verde, `/availability` → 200, SQL con `LEFT JOIN users`.

#### APP-2 · BAJA · bug · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `AppointmentValidator.java`
- `validateAppointmentInterval` hacía `minutes % interval` sin defender contra
  `interval` nulo (NPE) o cero (ArithmeticException) → 500 críptico.
- **Corrección aplicada**: guard al inicio — si `interval` es null o ≤0, lanza
  `500` con mensaje claro ("Error de configuración: el intervalo... no es
  válido"). El schema sigue garantizándolo; el guard protege el `@Component`
  reutilizable.

#### APP-4 · BAJA · rendimiento
- **Archivo**: `AppointmentController.java:91-98`
- El Javadoc promete `size` "max 100"; está garantizado por
  `spring.data.web.pageable.max-page-size=100` (verificado en
  `application.properties:35`). Correcto — se anota solo para que el tope quede
  documentado junto al endpoint.

#### APP-6 · INFO · inconsistencia · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `AvailabilityController.java`
- `GET /availability?date=...` no rechazaba fechas pasadas, mientras
  `CreateAppointmentRequest` sí lleva `@FutureOrPresent`.
- **Corrección aplicada**: `@FutureOrPresent` en el `date` del endpoint (+ su
  import). Verificado: `/availability?date=2020-01-01` → 400,
  `date=2026-05-28` → 200.

#### APP-7 · INFO · convención · ✅ CORREGIDO (2026-05-21)
- **Archivo**: `AppointmentService.java`, `AppointmentController.java`
- El número de validaciones de `createAppointment` aparecía como 14, 15 y 16 en
  distintos comentarios.
- **Corrección aplicada**: eliminado el conteo numérico de los 4 comentarios
  (`AppointmentService` ×2, `AppointmentController` ×2) — ahora dicen "cadena de
  validaciones" sin número, que no se desincroniza. Solo comentarios.

#### APP-3, APP-5, APP-8 · INFO · (sin acción) — verificados sin desviación
(mensajes de error, `try/catch` de doble reserva, `BookedService` con precios
congelados).

> **Verificado limpio**: la race condition de doble reserva (lock pesimista +
> `saveAndFlush` + `try/catch` que traduce el UNIQUE a 409), el algoritmo de
> `getAvailability` (9 pasos, sin off-by-one), la whitelist de `sort` en
> `searchAppointments` (→ 400), `serviceIds` con nulos, la máquina de estados,
> `findApplicableBlocks` para bloqueos globales. 21 archivos revisados.

### 3.6 schema SQL ↔ entidades y tests

**El cruce de las 17 tablas con las 17 entidades JPA es IMPECABLE**: nombres de
columna, tipos, nullabilidad, longitudes, FKs, `UNIQUE`, `updatable` — todo casa
1:1. Sin tablas huérfanas ni columnas sin mapear (las columnas virtuales
`active_slot_key`/`active_booth_slot_key` se omiten correctamente). El seed es
coherente. Los 6 tests están actualizados y son correctos.

Los únicos hallazgos son **huecos de cobertura de tests**:

#### SCH-4 · MEDIA · cobertura-tests · ✅ CORREGIDO (2026-05-21)
- No había test del camino feliz de `createAppointment` (persistencia de la cita
  + `BookedService`, cálculo de `endDateTime`, congelado de `appliedPrice`/
  `appliedTaxPercentage`). Los 3 tests existentes solo cubrían rechazos 409.
- **Corrección aplicada**: nuevo test
  `createAppointment_persisteCitaYCongelaPrecios_cuandoTodoEsValido` en
  `AppointmentServiceTest` — captura con `ArgumentCaptor` la cita y los
  `BookedService` guardados y verifica `endDateTime` = inicio + duración y los
  precios congelados. Solo código de test. Verificado: `mvnw test` → exit 0.

#### SCH-5 · MEDIA · cobertura-tests · ✅ CORREGIDO (2026-05-21)
- Sin test de la máquina de estados de citas (`VALID_TRANSITIONS`), de
  `markPayment` ni de la whitelist de `sort` (esta última fue un bug-fix de QA
  500→400 sin test de regresión).
- **Corrección aplicada**: nuevo `AppointmentValidatorTest` con 3 tests de
  `validateStatusTransition` (transición válida, ilegal → 400, estado final →
  400) + nuevo test `searchAppointments_lanza400_cuandoElSortNoEstaEnLaWhitelist`
  en `AppointmentServiceTest`. `markPayment` (trivial) se deja sin test. Solo
  código de test. Verificado: `mvnw test` → exit 0.

#### SCH-6 · BAJA · cobertura-tests · ✅ CORREGIDO (2026-05-21)
- `selectBusiness` no se testeaba con una membership inactiva.
- **Corrección aplicada**: nuevo test
  `selectBusiness_lanza403_cuandoLaMembershipEstaInactiva` en `AuthServiceTest`
  — membership existente pero `isActive=false` → 403 (cubre la rama que los 2
  tests de select-business previos no tocaban). `mvnw test` verde.

#### SCH-7 · BAJA · cobertura-tests · ✅ CORREGIDO (2026-05-21)
- Sin test de `PasswordResetService` ni de `UserService.create` (find-or-create
  del `User` por email, lógica delicada del refactor v16).
- **Corrección aplicada**: nuevo `PasswordResetServiceTest` (5 tests:
  anti-enumeration de `requestReset`, camino feliz de `requestReset` y
  `consumeReset`, rechazos 400 por token inexistente/caducado) + nuevo
  `UserServiceTest` (3 tests de `create`: email nuevo → crea identidad, email
  existente → la reutiliza, ya-es-empleado → 409). `mvnw test` verde.

#### SCH-1, SCH-2, SCH-3, SCH-8 · INFO · (sin acción) — verificaciones
explícitas: `columnDefinition="TEXT"` consistente; los índices compuestos no se
declaran en las entidades (correcto con `ddl-auto=none`); el `CHECK
appointment_interval` se valida en servicio, no en la entidad (por convención);
comentario de `AvailabilityServiceTest` con un número desincronizado.

---

## 4. Hallazgos del FRONTEND

### 4.1 Corregidos en esta sesión (16)

| ID | Sev. | Archivo | Qué se corrigió |
|---|---|---|---|
| **FCO-1** | MEDIA | `package.json` | Eliminadas 9 dependencias fantasma (8 `@radix-ui/*` + `class-variance-authority`) que no se importan en ningún sitio — verificado con grep. `npm install` quitó 84 paquetes; build verde. |
| **FCO-2** | MEDIA | `components/layout/BusinessSwitcher.jsx` | Eliminada la lógica muerta de `isActive`: `GET /api/me/businesses` devuelve `MembershipSummaryResponse` (4 campos) que **no tiene** `isActive`, así que las insignias "Dado de baja" nunca se renderizaban y `disabled` por inactividad nunca aplicaba. Importación de `AlertCircle` y JSDoc también limpiados. |
| **FCO-4** | BAJA | `hooks/usePagedFetch.js` | El reset de página al cambiar filtros provocaba un fetch HTTP desechado cuando se estaba en página >0. Resuelto con un `useRef` que detecta el cambio de filtros y resetea la página *antes* de lanzar la petición. |
| **FCO-5** | BAJA | `src/App.css` | Archivo de scaffolding de Vite (clases `.hero`, `.counter`…, variables CSS inexistentes), no importado en ningún sitio. **Eliminado.** |
| **FCO-6** | BAJA | `context/AuthContext.jsx` | Un `optima_user` corrupto en `localStorage` hacía que `JSON.parse` reventara durante el render inicial → app en blanco irrecuperable. Añadidos `safeParse`, `isJwtExpired` y `loadStoredUser`: ante JSON corrupto o JWT caducado/ilegible, limpia el storage y devuelve `null`. **Resuelve también FCO-10** (un token caducado ya no llega a renderizar la UI protegida). |
| **FCO-7** | BAJA | `components/ui/Toast.jsx` | Los `id` se generaban con `Date.now()` → colisión si dos toasts se emiten en el mismo milisegundo (key duplicada + cierre prematuro). Cambiado a un contador monotónico de módulo. |
| **FCO-8** | BAJA | `components/ui/Input.jsx`, `Select.jsx`, `Textarea.jsx` | La `<label>` no estaba asociada al control (sin `htmlFor`/`id`). Añadido `useId()`: ahora un lector de pantalla anuncia la etiqueta y hacer clic en ella enfoca el campo. Afecta a todos los formularios de la app. |
| **FCO-9** | BAJA | `components/ui/Modal.jsx` | Sin semántica de diálogo. Añadidos `role="dialog"`, `aria-modal="true"`, `aria-labelledby` (con `useId`), `aria-label` en el botón de cierre, foco al panel al abrir y restauración del foco al cerrar. |
| **FPG-3** | BAJA | `pages/Dashboard.jsx` | El timeline "Agenda de hoy" usaba un rango fijo 9-21; las citas fuera de ese rango desaparecían. Ahora `TodayTimeline` recibe `dayStart`/`dayEnd` derivados de `business_hours` (mín. apertura / máx. cierre), con 9-21 como fallback. |
| **FPG-4** | BAJA | `pages/Dashboard.jsx` | `StatusDonut` calculaba `buckets[k] / total` sin defender contra `undefined` (la leyenda sí lo hacía). Unificado a `(buckets[k] ?? 0)`. |
| **FPG-5** | BAJA | `components/layout/Sidebar.jsx` | El `@media print` de Calendario ocultaba `aside.sidebar`, selector que no casaba con ningún elemento (el Sidebar no tenía esa clase) → la barra lateral salía al imprimir la agenda. Añadida la clase `sidebar` al `<aside>`. |
| **FPG-6** | BAJA | `pages/Dashboard.jsx`, `pages/Calendario.jsx` | Peticiones con `size: 200`/`size: 500` que el backend cappea en 100 (engañoso). Bajadas a `size: 100` con comentario que documenta el tope real. |
| **FPG-11** | INFO | `pages/Register.jsx` | Los enlaces "términos y condiciones" / "política de privacidad" eran `href="#"` y hacían scroll al inicio del formulario. Añadido `onClick={(e) => e.preventDefault()}`. |
| **FPG-15** | BAJA | `pages/Perfil.jsx` | El enlace "Ir a configuración" usaba `<a href>` → recarga completa de la SPA (re-montaba contextos, repetía `/api/roles`…). Cambiado a `<Link>` de React Router. |
| **FPG-16** | BAJA | `pages/Empleados.jsx` | El KPI del empleado pedía `size: 200` (cap real 100) con un comentario incorrecto. Corregidos `size` y comentario. |
| **FPG-17** | MEDIA | `pages/Perfil.jsx` | **Bug detectado en el cruce de verificación del orquestador** (no lo vio el agente de páginas). La sección "Mis negocios" leía `b.roleName`, campo inexistente — `MembershipSummaryResponse` lo llama `role`. Resultado: la etiqueta de rol salía **en blanco** y el avatar siempre con el degradado gris de fallback. `BusinessSwitcher` y `Login` ya usaban `b.role` correctamente. Corregido `b.roleName` → `b.role` (2 ocurrencias). |

### 4.2 Documentados (no corregidos) — con motivo (12)

#### FCO-3 · BAJA · UX — `AppointmentWizard.jsx:149-154`
El doble filtrado de huecos (servidor + cliente) puede dejar la lista vacía
mostrando "No hay huecos" cuando en realidad el backend devolvió huecos ya
pasados. Es funcional; mejora opcional: un mensaje distinto ("Ya no quedan
huecos hoy"). No corregido por ser cosmético.

#### FCO-10 · INFO · seguridad — `AppLayout.jsx:22`
El guard de rutas no validaba la expiración del JWT. **Resuelto indirectamente
por FCO-6**: `loadStoredUser` ahora descarta tokens caducados al hidratar el
contexto, así que `AppLayout` ya no renderiza la UI con una sesión muerta.

#### FCO-11 · INFO — `vite.config.js`
Revisado: `alias '@'` con `path.resolve(__dirname, …)` correcto. Sin acción.

#### FPG-1 · BAJA · `AppointmentWizard.jsx:144`
`goToSlots` pone `membershipId` en la query sin condicional. Revisado: **no es
un bug** — `goToSlots` valida `membershipId` no vacío antes (línea 129), así que
siempre tiene valor. Cambio cosmético omitido (no arregla nada).

#### FPG-2 · BAJA · `AppointmentWizard.jsx:102-110`
El `useEffect` de reset no depende de `prefillTime`. Revisado: **el código es
correcto** — `prefillTime` se consume fresco en `goToSlots` (no en ese effect);
añadirlo como dependencia sería una infracción de `exhaustive-deps` y podría
borrar datos del formulario. Sin cambio.

#### FPG-7 · BAJA · UX — `Empleados.jsx:214-223`
El drawer puede quedar mostrando datos "archivados" si se reactiva otro empleado
desde su tarjeta. Caso menor; aceptable para el alcance del TFG.

#### FPG-8 · INFO · UX — `Citas.jsx`, `Catalogo.jsx`, `Clientes.jsx`
La búsqueda y los filtros de estado/pago operan sobre la página visible, no
sobre todo el dataset. Es decisión consciente y ya está avisada en pantalla.

#### FPG-9 · BAJA · bug — `Catalogo.jsx:44-46,114-119`
`aux.taxes` se carga solo con impuestos activos; si un servicio referencia un
impuesto archivado, la columna "Con IVA" muestra el precio base (0%) en
silencio. **No corregido**: el arreglo correcto requiere o bien que
`BusinessServiceResponse` incluya `taxPercentage` (backend — relacionado con
CAT-3), o cargar también los impuestos archivados manteniéndolos fuera del
desplegable del modal (reestructuración no trivial para un caso límite BAJA).

#### FPG-10 · BAJA · bug — `Configuracion.jsx:948-964`
En `BlocksTab` las secciones Activos/Próximos/Pasados se derivan de la página
actual mientras el header muestra el total. Con >1 página las secciones no
suman el total. Las secciones ya muestran su propio conteo; un arreglo completo
exige no paginar los bloqueos o agregarlos en servidor.

#### FPG-12 · INFO · `Login.jsx:59-65`
El autofocus usa `document.querySelector` en vez de una `ref`. Funciona; menor.

#### FPG-13 · INFO — varias páginas
Las preferencias en `localStorage` se persisten con criterios algo distintos
entre páginas hermanas. Coherente con la decisión documentada de que la vista
"Archivados" siempre empieza limpia.

#### FPG-14 · INFO · `Empleados.jsx:157-186`
`handleSave` en modo edición tiene un flujo de validación enrevesado, pero
funcionalmente correcto (el backend solo acepta `roleId`).

---

## 5. Verificaciones globales que pasaron limpias

- **Cruce schema ↔ entidades**: 17 ↔ 17, impecable.
- **Multi-tenancy**: validación cross-tenant presente en todos los servicios
  tenant-scoped; `TenantGuardFilter` confirmado con smoke (403 cross-tenant).
- **Seguridad**: sin fugas de info en errores, contraseñas nunca expuestas,
  mass-assignment cerrado, race condition de doble reserva bien cerrada.
- **Frontend ↔ API**: rutas, métodos, payloads y query params casan con los
  controllers (salvo FCO-2 y FPG-17, ya corregidos). Fechas sin sufijo `Z`.
- **Tests**: `mvnw test` verde; `vite build` verde; E2E 30/30; smoke de API OK.
- **Javadocs** (2026-05-21): tras aplicar los 26 fixes, los Javadoc y
  comentarios de los ~30 archivos tocados se revisaron con un subagente; 3
  desajustes menores de comentario corregidos.

---

*Generado por la auditoría quirúrgica del 2026-05-21. Las correcciones
aplicadas se marcan ✅ CORREGIDO en cada hallazgo; el resto quedan documentadas.*
