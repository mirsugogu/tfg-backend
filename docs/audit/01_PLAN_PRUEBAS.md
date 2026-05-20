# Plan exhaustivo de pruebas pre-frontend — Optima

**Fecha**: 2026-05-17
**Rama**: `audit/pre-frontend`
**Origen**: derivado 1:1 de `docs/audit/00_MAPA_REAL.md` (sin inventar endpoints ni validaciones que no existen en el código).
**Objetivo**: validar exhaustivamente el backend antes de integrar el frontend, cubriendo seguridad, validación, reglas de negocio, rendimiento y contrato HTTP.

---

## 0. Convenciones del plan

### 0.1 Formato de tabla

| Columna | Significado |
|---|---|
| **ID** | `<Bloque>.<Subbloque>.<Nº>` (ej. `A.2.005`). Único en el plan. |
| **Bloque** | Letra del bloque (A–O). |
| **Endpoint/Caso** | Método + path o descripción del caso. |
| **Input** | Body, headers, query, path, estado de BD relevante. |
| **Esperado** | Status HTTP, body, side effects en BD/logs/email/queries SQL. |
| **Sev** | Severidad si la prueba falla: `CRIT` / `ALTA` / `MEDIA` / `BAJA` / `INFO`. |

### 0.2 Severidades

- **CRIT** — bug de seguridad explotable o corrupción de datos (SQLi, JWT roto, cross-tenant data leak, lock pesimista que no bloquea concurrencia).
- **ALTA** — fallo funcional con impacto en datos del tenant (state machine que permite saltos, validación cross-tenant que no salta, soft delete que no oculta el recurso).
- **MEDIA** — fallo funcional sin impacto en datos (validación que devuelve 500 en vez de 400, paginación que no respeta límite máximo, mensaje en idioma incorrecto).
- **BAJA** — divergencia cosmética (formato exacto del `timestamp`, header `Allow` ausente, mensaje sin acentos).
- **INFO** — comprobación informativa (qué hace si Nominatim cae, número exacto de queries SQL, contenido de logs en DEBUG). No "falla" salvo que revele un riesgo concreto.

### 0.3 Convenciones de fixtures usadas en los inputs

| Fixture | Significado |
|---|---|
| `B1` / `B2` | businessId 1 (Peluquería Demo, seed) / businessId 2 (Barbería Centro). |
| `admin@b1` / `empleado@b1` | usuario ADMIN/EMPLOYEE con membership activa en B1. |
| `maria@b1+b2` | usuario con membership activa en B1 y B2 (caso multi-membership del seed). |
| `tkA(B1)` | tenant token de admin@b1 con claim businessId=1. |
| `tkE(B1)` | tenant token de empleado@b1 con claim businessId=1, role=EMPLOYEE. |
| `tkI(maria)` | identity token de maria@b1+b2 (sin claim businessId). |
| `tk*` | cualquier JWT bien formado y firmado por nuestra clave. |
| `c1`, `s1`, `m1`, …| IDs concretos de cliente, servicio, membership en B1 del seed. |
| `c1@b2` | recurso con id `c1` que en realidad pertenece a B2 (para forzar cross-tenant). |
| `now+1d` | timestamp ISO en el futuro (ej. mañana 14:00:00). |

### 0.4 Cómo se prepara el entorno

1. `docker compose down -v` + `docker compose up -d` para sembrar `schema_v20.sql` limpio.
2. Variables: `JWT_SECRET` y `MAIL_PASSWORD` cargados de `.env`.
3. Pre-pruebas: capturar baseline `SELECT COUNT(*) FROM users; appointments; businesses;` (para verificar que las inyecciones SQL no alteran tablas).
4. Postman collection v4 + curl para los casos de concurrencia y rate limits.
5. Para el bloque L (queries SQL): activar temporalmente `logging.level.org.hibernate.SQL=DEBUG` y `org.hibernate.orm.jdbc.bind=TRACE`.
6. Para el bloque N.10 (logs sensibles): activar `logging.level.root=DEBUG`.

---

## A. Autenticación

Cubre `LoginRequest`, `RegisterRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `select-business`, y los 4 rate limits del `RateLimitFilter`.

### A.1 Login `POST /api/auth/token`

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.1.001 | A | POST /api/auth/token — 1 membership activa | `{"email":"admin@b1","password":"12345678"}` | 200, `tokenType="tenant"`, `token` JWT con claim `businessId=1` y `role="ADMIN"`, `businesses=null` | CRIT |
| A.1.002 | A | POST /api/auth/token — N memberships activas | `{"email":"maria@b1+b2","password":"12345678"}` | 200, `tokenType="identity"`, `token` JWT sin `businessId`, `businesses` con 2 entradas (id, name, roleName) | CRIT |
| A.1.003 | A | POST /api/auth/token — 0 memberships activas (todas inactivas) | usuario con todas las memberships con `is_active=false` | 401 message `"Credenciales incorrectas"` (no debe revelar la situación) | ALTA |
| A.1.004 | A | Email inexistente | `{"email":"noexiste@x.com","password":"X12345678"}` | 401 `"Credenciales incorrectas"` (mismo mensaje que A.1.005) | ALTA |
| A.1.005 | A | Password incorrecto | `{"email":"admin@b1","password":"WRONG_PASS"}` | 401 mismo mensaje exacto que A.1.004 | ALTA |
| A.1.006 | A | Usuario desactivado (`users.is_active=false`) | `{"email":"admin@b1","password":"12345678"}` previo `UPDATE users SET is_active=0` | 401 `"Credenciales incorrectas"` mismo mensaje | ALTA |
| A.1.007 | A | email NULL | `{"email":null,"password":"12345678"}` | 400 Bean Validation `@NotBlank` | MEDIA |
| A.1.008 | A | email cadena vacía | `{"email":"","password":"12345678"}` | 400 `@NotBlank` | MEDIA |
| A.1.009 | A | email solo espacios | `{"email":"   ","password":"12345678"}` | 400 `@NotBlank` (rechaza blanco) | MEDIA |
| A.1.010 | A | password NULL | `{"email":"admin@b1","password":null}` | 400 `@NotBlank` | MEDIA |
| A.1.011 | A | Body vacío | `{}` | 400 (ambos `@NotBlank`) | MEDIA |
| A.1.012 | A | JSON malformado | `{"email": "admin@b1"` (sin cierre) | 400 `HttpMessageNotReadableException` → `ErrorResponse` | MEDIA |
| A.1.013 | A | email con espacios alrededor | `{"email":" admin@b1 ","password":"12345678"}` | 200 (`AuthService.login` hace `.trim()`) | MEDIA |
| A.1.014 | A | email case-insensitive | `{"email":"ADMIN@B1","password":"12345678"}` | 200 (`findByEmailIgnoreCase`) | MEDIA |
| A.1.015 | A | Content-Type no JSON | `Content-Type: application/xml`, body XML | 415 `HttpMediaTypeNotSupportedException` → `ErrorResponse` | MEDIA |
| A.1.016 | A | Sin Content-Type | body JSON pero sin header `Content-Type` | 415 o 400 | BAJA |
| A.1.017 | A | Método incorrecto | `GET /api/auth/token` | 405 `HttpRequestMethodNotSupportedException` + header `Allow: POST` | BAJA |
| A.1.018 | A | Respuesta — campos del TokenResponse | login OK 1 membership | response contiene `token`, `tokenType`, `expiresAt`, `userId`, `email`, `businesses` (null o lista) — sin `password`, sin `passwordHash` | CRIT |

### A.2 Register `POST /api/auth/register`

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.2.001 | A | Register OK | `{"business":{"name":"NuevoNeg","slug":"nuevo","email":"new@x.com","appointmentInterval":30},"admin":{"fullName":"Nuevo Admin","email":"admin@new.com","password":"12345678"}}` | 201 + tenant token (1 membership); fila en `businesses`, `users`, `memberships` (rol ADMIN); welcome email best-effort | ALTA |
| A.2.002 | A | slug duplicado | business.slug = "peluqueria-demo" (existe en seed) | 409 `"Ya existe un negocio con ese slug"` | ALTA |
| A.2.003 | A | email global ya existe (`users.email` UNIQUE) | admin.email = "admin@b1" | 409 (no debe permitir registrar otro negocio con ese admin como nuevo usuario; el flujo debe documentar si reusa identidad o falla) | ALTA |
| A.2.004 | A | business.email duplicado en `businesses` | business.email = "info@peluqueria.com" | 409 (UNIQUE col-level businesses.email) | ALTA |
| A.2.005 | A | admin.password muy corto | `admin.password = "1234567"` (7 chars) | 400 `@Size(min=8)` | MEDIA |
| A.2.006 | A | admin.password 8 chars exactos | `admin.password = "12345678"` | 201 | BAJA |
| A.2.007 | A | admin.password 101 chars | password de 101 chars | 400 `@Size(max=100)` | MEDIA |
| A.2.008 | A | admin.email mal formado | admin.email = "notanemail" | 400 `@Email` | MEDIA |
| A.2.009 | A | admin.email 151 chars | email de 151 chars (parte local muy larga) | 400 `@Size(max=150)` | MEDIA |
| A.2.010 | A | appointmentInterval no permitido | `business.appointmentInterval=20` | 400 (validación service: solo 15/30/45/60) | MEDIA |
| A.2.011 | A | appointmentInterval ausente | sin `appointmentInterval` | 201, defaults a 30 en el service | BAJA |
| A.2.012 | A | appointmentInterval=15/30/45/60 | cada valor por separado | 201 los cuatro | MEDIA |
| A.2.013 | A | business.email mal formado | business.email = "no-email" | 400 `@Email` | MEDIA |
| A.2.014 | A | business={} (anidado vacío) | `{"business":{}, "admin":{...}}` | 400 (cascade `@Valid`, varios `@NotBlank` fallan) | MEDIA |
| A.2.015 | A | admin={} | `{"business":{...}, "admin":{}}` | 400 | MEDIA |
| A.2.016 | A | business=null | `{"business":null,"admin":{...}}` | 400 `@NotNull` | MEDIA |
| A.2.017 | A | admin=null | `{"business":{...},"admin":null}` | 400 `@NotNull` | MEDIA |
| A.2.018 | A | business.name 151 chars | `business.name = "a"*151` | 400 `@Size(max=150)` | MEDIA |
| A.2.019 | A | business.slug con caracteres prohibidos | `business.slug = "Hola Mundo!"` (espacios + signos) | 400 si hay regex de slug, 201 si no hay; documentar comportamiento | INFO |
| A.2.020 | A | business.phone 21 chars | phone de 21 chars | 400 `@Size(max=20)` | BAJA |
| A.2.021 | A | Respuesta Register — token devuelto es `tenant`, no `identity` | register OK | tokenType="tenant" (1 sola membership recién creada) | MEDIA |
| A.2.022 | A | Side effect: email de bienvenida | register OK con MAIL_USERNAME configurado | log.info "welcome email sent" o "welcome email skipped" (best-effort) | INFO |

### A.3 Forgot password `POST /api/auth/forgot-password`

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.3.001 | A | Email existente | `{"email":"admin@b1"}` | 204 (sin body); fila nueva en `password_resets` (token_hash, expires_at = now+1h); email enviado best-effort | ALTA |
| A.3.002 | A | Email inexistente (anti-enumeration) | `{"email":"noexiste@x.com"}` | 204 (mismo status y SIN BODY que A.3.001 — no debe permitir enumerar usuarios) | ALTA |
| A.3.003 | A | Email NULL | `{"email":null}` | 400 `@NotBlank` | MEDIA |
| A.3.004 | A | Email mal formado | `{"email":"notanemail"}` | 400 `@Email` | MEDIA |
| A.3.005 | A | Email vacío | `{"email":""}` | 400 `@NotBlank` | MEDIA |
| A.3.006 | A | Email 151 chars | email muy largo | 400 `@Size(max=150)` | BAJA |
| A.3.007 | A | Side effect: `password_resets.token_hash` es SHA-256, no el token plano | inspección BD post-A.3.001 | `LENGTH(token_hash) = 64` hex; token plano NO almacenado | CRIT |
| A.3.008 | A | Side effect: `expires_at` ≈ now+1h | inspección BD | diferencia entre `expires_at` y `created_at` cercana a 3600s | BAJA |
| A.3.009 | A | Email del side effect contiene el token plano (no el hash) | revisar mailbox | el correo lleva el token usable | INFO |

### A.4 Reset password `POST /api/auth/reset-password`

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.4.001 | A | Reset OK | `{"token":"<token-recibido-en-A.3.001>","newPassword":"NewPass99"}` | 204; `users.password_hash` cambia; `password_resets.used_at` queda set (o se borra según implementación) | ALTA |
| A.4.002 | A | Token inexistente | `{"token":"abcdef…0000","newPassword":"NewPass99"}` | 400 mensaje genérico (no debe revelar si el token nunca existió vs expiró vs usado) | ALTA |
| A.4.003 | A | Token expirado (>1h) | usar token con `expires_at < now` (forzar via SQL `UPDATE` previo) | 400 mismo mensaje genérico | ALTA |
| A.4.004 | A | Token ya usado | reutilizar el token de A.4.001 | 400 mismo mensaje genérico | ALTA |
| A.4.005 | A | newPassword muy corto | `newPassword = "1234567"` | 400 `@Size(min=8)` | MEDIA |
| A.4.006 | A | newPassword null | `newPassword = null` | 400 `@NotBlank` | MEDIA |
| A.4.007 | A | newPassword 101 chars | `newPassword = "a"*101` | 400 `@Size(max=100)` | BAJA |
| A.4.008 | A | token null | `token = null` | 400 `@NotBlank` | MEDIA |
| A.4.009 | A | token 256 chars | token de 256 chars | 400 `@Size(max=255)` | BAJA |
| A.4.010 | A | Después de reset, login con password vieja | login con la password anterior | 401 (el reset invalidó la password vieja) | ALTA |
| A.4.011 | A | Después de reset, login con password nueva | login con la nueva | 200 | ALTA |

### A.5 Select business `POST /api/auth/select-business/{businessId}`

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.5.001 | A | identity → tenant OK | `tkI(maria)` + path `/2` (membership activa en B2) | 200 + tenant token con `businessId=2` y `role=…` | CRIT |
| A.5.002 | A | identity → membership que no tiene | `tkI(maria)` + path `/99` | 403 | CRIT |
| A.5.003 | A | identity → membership existente pero `is_active=false` | `tkI(maria)` + path `/<negocio-con-membership-inactiva>` | 403 | ALTA |
| A.5.004 | A | tenant → cambia de negocio | `tkA(B1)` + path `/2` (maria también es ADMIN en B2 → válido si usuario tiene B2) | 200 + tenant token con `businessId=2` (rebinding) | ALTA |
| A.5.005 | A | sin token | header sin `Authorization` | 401 (endpoint requiere JWT — no es permitAll) | CRIT |
| A.5.006 | A | businessId=0 | path `/0` | 400 `@Positive` | MEDIA |
| A.5.007 | A | businessId=-1 | path `/-1` | 400 `@Positive` | MEDIA |
| A.5.008 | A | businessId no numérico | path `/abc` | 400 `MethodArgumentTypeMismatchException` → handler `ConstraintViolationException`/MethodArgument | MEDIA |
| A.5.009 | A | businessId que existe pero el usuario no es membership | usuario sin membership en B7, path `/7` | 403 (no 404 — no leak si el negocio existe) | ALTA |

### A.6 Rate limits

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| A.6.001 | A | Rate limit login: 21ª request | 21 POST /api/auth/token consecutivos desde misma IP en <60s | requests 1–20: 401/200; request 21: 429 con header `Retry-After: <segundos>` y `ErrorResponse(429, "Too Many Requests", "Demasiadas peticiones…", ts)` | ALTA |
| A.6.002 | A | Rate limit register: 21ª request | 21 POST /api/auth/register en <1h misma IP | request 21: 429 | ALTA |
| A.6.003 | A | Rate limit forgot: 11ª request | 11 POST /api/auth/forgot-password en <1h misma IP | request 11: 429 | ALTA |
| A.6.004 | A | Rate limit reset: 21ª request | 21 POST /api/auth/reset-password en <1h misma IP | request 21: 429 | ALTA |
| A.6.005 | A | Rate limit por IP (no global) | request 21 desde IP `127.0.0.1` después de A.6.001, request 1 desde otra IP/origen | misma IP: 429; otra IP: 401/200 (bucket independiente) | ALTA |
| A.6.006 | A | Rate limit no se aplica fuera de auth | 21 POST /api/businesses/1/clients en <1min | sin 429 (RateLimitFilter sólo cubre 4 paths POST de auth) | INFO |
| A.6.007 | A | Rate limit ignora método ≠ POST | 21 GET /api/auth/token | 405 (no es POST → cae a `HttpRequestMethodNotSupportedException`) | INFO |
| A.6.008 | A | Format del error 429 | después de A.6.001 | body = ErrorResponse JSON con `status=429`, `error="429 TOO_MANY_REQUESTS"` (o `"Too Many Requests"`), `message` en español, `timestamp` ISO | MEDIA |
| A.6.009 | A | Retry-After ≥ 1 | header de la respuesta 429 | `Retry-After ≥ 1` (no 0 ni negativo) | BAJA |

**Subtotal A: 78 pruebas.**

---

## B. Autorización

Cubre la cadena `RateLimitFilter → JwtAuthenticationFilter → TenantGuardFilter → @PreAuthorize`.

### B.1 Sin token / token inválido

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| B.1.001 | B | GET /api/me sin Authorization | sin header | 401 + ErrorResponse JSON | CRIT |
| B.1.002 | B | Authorization sin "Bearer " | `Authorization: AbCdEf…` | 401 | ALTA |
| B.1.003 | B | Bearer vacío | `Authorization: Bearer ` | 401 | ALTA |
| B.1.004 | B | Bearer con basura | `Authorization: Bearer not-a-jwt` | 401 | ALTA |
| B.1.005 | B | Bearer con JWT bien formado pero firmado con otra clave | token firmado con `secret = "otra-clave-de-32-chars-arghhh!"` | 401 (firma inválida) | CRIT |
| B.1.006 | B | Bearer con JWT con 1 char de firma cambiado | token válido, último char de la firma alterado | 401 | CRIT |
| B.1.007 | B | Bearer con JWT expirado | `exp` en el pasado | 401 | ALTA |
| B.1.008 | B | Bearer con JWT sin claim `sub` | JWT manual sin `sub` | 401 o 403 (sin email no se puede resolver el principal) | CRIT |
| B.1.009 | B | Bearer con JWT cuyo `sub` no existe en BD | sub="ghost@x.com" | 401 (`UserRepository.findByEmailIgnoreCase` devuelve empty) | CRIT |
| B.1.010 | B | Bearer con padding base64 corrupto | `Bearer aaaa.bbbb.ccccc` con `===` mal puestos | 401 | ALTA |

### B.2 TenantGuard (identity vs tenant + cross-tenant)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| B.2.001 | B | identity intentando recurso tenant-scoped | `tkI(maria)` + GET /api/businesses/1/clients | 403 (TenantGuardFilter rechaza por `principal.businessId() == null`) | CRIT |
| B.2.002 | B | tenant cross-tenant por path | `tkA(B1)` + GET /api/businesses/2 | 403 (pathBusinessId=2 != principal.businessId=1) | CRIT |
| B.2.003 | B | tenant cross-tenant en subrecurso | `tkA(B1)` + GET /api/businesses/2/clients/1 | 403 | CRIT |
| B.2.004 | B | tenant accediendo a su propio negocio | `tkA(B1)` + GET /api/businesses/1 | 200 | ALTA |
| B.2.005 | B | identity accediendo a /api/me | `tkI(maria)` + GET /api/me | 200 (no es path /api/businesses/X/) | ALTA |
| B.2.006 | B | identity accediendo a /api/me/businesses | `tkI(maria)` + GET /api/me/businesses | 200 + lista de memberships activas | ALTA |
| B.2.007 | B | identity en select-business | `tkI(maria)` + POST /api/auth/select-business/2 | 200 + tenant token (TenantGuardFilter no aplica al path `/api/auth/...`) | ALTA |
| B.2.008 | B | tenant en select-business (cambio de negocio) | `tkA(B1)` + POST /api/auth/select-business/2 | 200 + tenant token nuevo | ALTA |
| B.2.009 | B | tenant en /api/businesses (POST root) | `tkA(B1)` + POST /api/businesses con CreateBusinessRequest | 201 (TenantGuard no se aplica — el path es `/api/businesses`, no `/api/businesses/{id}/...`) **pero** `@PreAuthorize("hasRole('ADMIN')")` filtra | INFO |
| B.2.010 | B | tenant accediendo a /api/businesses/3 (negocio inexistente) | `tkA(B1)` + GET /api/businesses/3 | 403 (TenantGuard rechaza por path != claim antes de llegar al service) | ALTA |

### B.3 `@PreAuthorize("hasRole('ADMIN')")` vs EMPLOYEE

Endpoints con `ADMIN`:

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| B.3.001 | B | EMPLOYEE no puede crear business raíz | `tkE(B1)` + POST /api/businesses | 403 | ALTA |
| B.3.002 | B | EMPLOYEE no puede PUT business | `tkE(B1)` + PUT /api/businesses/1 | 403 | ALTA |
| B.3.003 | B | EMPLOYEE no puede DELETE business | `tkE(B1)` + DELETE /api/businesses/1 | 403 | ALTA |
| B.3.004 | B | EMPLOYEE no puede reactivate business | `tkE(B1)` + PATCH /api/businesses/1/reactivate | 403 | ALTA |
| B.3.005 | B | EMPLOYEE no puede crear taxes | `tkE(B1)` + POST /api/businesses/1/taxes | 403 | ALTA |
| B.3.006 | B | EMPLOYEE no puede PUT/DELETE taxes | `tkE(B1)` + PUT/DELETE /…/taxes/{id} | 403 ambos | ALTA |
| B.3.007 | B | EMPLOYEE no puede crear booth | `tkE(B1)` + POST /…/booths | 403 | ALTA |
| B.3.008 | B | EMPLOYEE no puede crear hours | `tkE(B1)` + POST /…/hours | 403 | ALTA |
| B.3.009 | B | EMPLOYEE no puede crear category | `tkE(B1)` + POST /…/categories | 403 | ALTA |
| B.3.010 | B | EMPLOYEE no puede crear service | `tkE(B1)` + POST /…/services | 403 | ALTA |
| B.3.011 | B | EMPLOYEE no puede crear user (membership) | `tkE(B1)` + POST /…/users | 403 | ALTA |
| B.3.012 | B | EMPLOYEE no puede crear schedule | `tkE(B1)` + POST /…/users/{id}/schedules | 403 | ALTA |
| B.3.013 | B | EMPLOYEE no puede crear absence | `tkE(B1)` + POST /…/users/{id}/absences | 403 | ALTA |
| B.3.014 | B | EMPLOYEE no puede crear schedule-block | `tkE(B1)` + POST /…/schedule-blocks | 403 | ALTA |

### B.4 Endpoints sin `@PreAuthorize` (ADMIN y EMPLOYEE permitidos)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| B.4.001 | B | EMPLOYEE puede crear cliente | `tkE(B1)` + POST /…/clients | 201 (ClientController no tiene `@PreAuthorize`) | ALTA |
| B.4.002 | B | EMPLOYEE puede PUT cliente | `tkE(B1)` + PUT /…/clients/{id} | 200 | ALTA |
| B.4.003 | B | EMPLOYEE puede DELETE cliente | `tkE(B1)` + DELETE /…/clients/{id} | 204 | ALTA |
| B.4.004 | B | EMPLOYEE puede crear appointment | `tkE(B1)` + POST /…/appointments | 201 | ALTA |
| B.4.005 | B | EMPLOYEE puede PATCH appointment status | `tkE(B1)` + PATCH /…/appointments/{id}/status | 200 | ALTA |
| B.4.006 | B | EMPLOYEE puede PATCH appointment payment | `tkE(B1)` + PATCH /…/appointments/{id}/payment | 200 | ALTA |
| B.4.007 | B | EMPLOYEE puede GET availability | `tkE(B1)` + GET /…/availability | 200 | ALTA |
| B.4.008 | B | EMPLOYEE puede GET listados de configuración | `tkE(B1)` + GET /…/taxes, /…/categories, /…/services, /…/booths, /…/hours, /…/users, /…/schedule-blocks | 200 todos (lectura tenant-scoped abierta a ambos roles) | ALTA |

### B.5 permitAll (catálogos globales y auth)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| B.5.001 | B | GET /api/roles sin token | sin Authorization | 200 + lista (permitAll) | INFO |
| B.5.002 | B | GET /api/appointment-statuses sin token | sin Authorization | 200 + lista | INFO |
| B.5.003 | B | GET /api/appointment-statuses/{id} sin token | id=1 | 200 + objeto | INFO |
| B.5.004 | B | GET /swagger-ui.html sin token | — | 200 (permitAll) | INFO |
| B.5.005 | B | GET /v3/api-docs sin token | — | 200 + JSON OpenAPI | INFO |

**Subtotal B: 47 pruebas.**

---

## C. Validación de Request DTOs

Esta sección cubre los 28 Request DTOs del mapa. Como `LoginRequest`, `RegisterRequest`, `ForgotPasswordRequest` y `ResetPasswordRequest` ya tienen sus casos exhaustivos en el bloque A, aquí cubrimos los 24 restantes.

### C.1 CreateBusinessRequest / UpdateBusinessRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.1.001 | C | POST /api/businesses — name null | `{"name":null,…}` | 400 `@NotBlank` | MEDIA |
| C.1.002 | C | name vacío | `name=""` | 400 | MEDIA |
| C.1.003 | C | name 150 chars (límite) | name = "a"*150 | 201 | BAJA |
| C.1.004 | C | name 151 chars | name = "a"*151 | 400 `@Size(max=150)` | MEDIA |
| C.1.005 | C | slug null | `slug=null` | 400 | MEDIA |
| C.1.006 | C | slug duplicado | slug ya existe | 409 | ALTA |
| C.1.007 | C | email null | `email=null` | 400 | MEDIA |
| C.1.008 | C | email mal formado | `"notanemail"` | 400 `@Email` | MEDIA |
| C.1.009 | C | phone 20 chars | phone="1"*20 | 201 | BAJA |
| C.1.010 | C | phone 21 chars | phone="1"*21 | 400 `@Size(max=20)` | MEDIA |
| C.1.011 | C | address 256 chars | address="x"*256 | 400 `@Size(max=255)` | MEDIA |
| C.1.012 | C | appointmentInterval=20 | service rechaza no 15/30/45/60 | 400 | MEDIA |
| C.1.013 | C | appointmentInterval=0 | service rechaza | 400 | MEDIA |
| C.1.014 | C | appointmentInterval=null | omitido | 201 con default=30 | BAJA |
| C.1.015 | C | Campo extra en body | `{…, "foo":"bar"}` | 201 (Jackson ignora desconocidos en records) | INFO |
| C.1.016 | C | UPDATE: slug en body se ignora (no está en `UpdateBusinessRequest`) | PUT con `slug="otro"` | 200 + el slug NO cambia | MEDIA |
| C.1.017 | C | UPDATE: name null | PUT con name=null | 400 | MEDIA |

### C.2 CreateTaxRequest / UpdateTaxRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.2.001 | C | name null | `{name:null, percentage:21}` | 400 | MEDIA |
| C.2.002 | C | name 50 chars (límite) | name="a"*50 | 201 | BAJA |
| C.2.003 | C | name 51 chars | name="a"*51 | 400 `@Size(max=50)` | MEDIA |
| C.2.004 | C | percentage null | `{name:"IVA",percentage:null}` | 400 `@NotNull` | MEDIA |
| C.2.005 | C | percentage=-0.01 | percentage=-0.01 | 400 `@DecimalMin(0.00)` | MEDIA |
| C.2.006 | C | percentage=0 | percentage=0 | 201 | BAJA |
| C.2.007 | C | percentage=100 | percentage=100 | 201 | BAJA |
| C.2.008 | C | percentage=100.01 | percentage=100.01 | 400 `@DecimalMax(100.00)` | MEDIA |
| C.2.009 | C | percentage="not-a-number" | string en percentage | 400 (deserialización) | MEDIA |
| C.2.010 | C | percentage con 3 decimales | percentage=21.999 | 201 o 400 (DB es DECIMAL — verificar precisión) | INFO |
| C.2.011 | C | name duplicado en mismo negocio | dos POST con mismo `name` en B1 | 1ª 201, 2ª 409 (UNIQUE `uq_tax_business_name`) | ALTA |
| C.2.012 | C | name duplicado entre negocios | mismo name en B1 y B2 | 201 ambos (UNIQUE es por `(id_business, name)`) | INFO |

### C.3 CreateBoothRequest / UpdateBoothRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.3.001 | C | name null | `{name:null}` | 400 | MEDIA |
| C.3.002 | C | name 80 chars (límite) | name="a"*80 | 201 | BAJA |
| C.3.003 | C | name 81 chars | name="a"*81 | 400 `@Size(max=80)` | MEDIA |
| C.3.004 | C | name duplicado en B1 | dos POST con mismo name | 409 UNIQUE `uq_booth_business_name` | ALTA |

### C.4 CreateBusinessHourRequest / UpdateBusinessHourRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.4.001 | C | dayOfWeek null | `{dayOfWeek:null, …}` | 400 `@NotNull` | MEDIA |
| C.4.002 | C | dayOfWeek=0 | dayOfWeek=0 | 400 `@Min(1)` | MEDIA |
| C.4.003 | C | dayOfWeek=8 | dayOfWeek=8 | 400 `@Max(7)` | MEDIA |
| C.4.004 | C | isClosed=true sin start/end | `{dayOfWeek:1,isClosed:true}` | 201 (chk_bh_times_logic permite isClosed=true con tiempos nulos) | ALTA |
| C.4.005 | C | isClosed=false sin start/end | `{dayOfWeek:1,isClosed:false}` | 400 (validación cruzada en service) | ALTA |
| C.4.006 | C | startTime >= endTime con isClosed=false | `start=18:00, end=09:00, isClosed=false` | 400 (CHECK chk_bh_times_logic) | ALTA |
| C.4.007 | C | dayOfWeek=1 duplicado | dos POST dayOfWeek=1 en B1 | 1ª 201, 2ª 409 (UNIQUE `uq_business_hours_day`) | ALTA |
| C.4.008 | C | dayOfWeek=7 (domingo) | OK con isClosed=true | 201 | BAJA |

### C.5 CreateScheduleBlockRequest (sin Update — endpoint sin PUT)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.5.001 | C | startDate null | `{startDate:null, endDate:"2026-12-25"}` | 400 `@NotNull` | MEDIA |
| C.5.002 | C | endDate null | `{startDate:"2026-12-24",endDate:null}` | 400 | MEDIA |
| C.5.003 | C | startDate > endDate | start="2027-01-01", end="2026-01-01" | 400 (CHECK chk_block_dates) | ALTA |
| C.5.004 | C | startDate == endDate | start=end="2026-12-25" | 201 (CHECK permite `<=`) | BAJA |
| C.5.005 | C | membershipId Y boothId ambos set | `{membershipId:1, boothId:1, ...}` | 400 (CHECK chk_block_target) | ALTA |
| C.5.006 | C | ambos null (global) | `{membershipId:null, boothId:null, …}` | 201 (bloqueo global) | ALTA |
| C.5.007 | C | solo membershipId | `{membershipId:1, boothId:null, …}` | 201 (bloqueo por empleado) | ALTA |
| C.5.008 | C | solo boothId | `{membershipId:null, boothId:1, …}` | 201 (bloqueo por cabina) | ALTA |
| C.5.009 | C | membershipId=0 | `{membershipId:0, …}` | 400 `@Positive` | MEDIA |
| C.5.010 | C | reason 255 chars | reason="x"*255 | 201 | BAJA |
| C.5.011 | C | reason 256 chars | reason="x"*256 | 400 `@Size(max=255)` | MEDIA |
| C.5.012 | C | reason null | omitido | 201 (opcional) | BAJA |
| C.5.013 | C | cross-tenant membershipId | membershipId de B2 con `tkA(B1)` | 404 | ALTA |
| C.5.014 | C | cross-tenant boothId | boothId de B2 con `tkA(B1)` | 404 | ALTA |

### C.6 CreateCategoryRequest / UpdateCategoryRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.6.001 | C | name null | `{name:null}` | 400 | MEDIA |
| C.6.002 | C | name 100 chars (límite) | name="a"*100 | 201 | BAJA |
| C.6.003 | C | name 101 chars | name="a"*101 | 400 `@Size(max=100)` | MEDIA |
| C.6.004 | C | name duplicado en B1 | dos POST same name | 409 UNIQUE `uq_category_business_name` | ALTA |

### C.7 CreateServiceRequest / UpdateServiceRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.7.001 | C | categoryId null | `{categoryId:null, …}` | 400 | MEDIA |
| C.7.002 | C | categoryId=0 | categoryId=0 | 400 `@Positive` | MEDIA |
| C.7.003 | C | categoryId cross-tenant | category de B2 con `tkA(B1)` | 404 | ALTA |
| C.7.004 | C | taxId null | `{taxId:null, …}` | 400 | MEDIA |
| C.7.005 | C | taxId cross-tenant | tax de B2 con `tkA(B1)` | 404 | ALTA |
| C.7.006 | C | name null | name=null | 400 | MEDIA |
| C.7.007 | C | name 151 chars | "a"*151 | 400 `@Size(max=150)` | MEDIA |
| C.7.008 | C | price null | `{price:null}` | 400 | MEDIA |
| C.7.009 | C | price=-0.01 | price=-0.01 | 400 `@DecimalMin(0.0)` | MEDIA |
| C.7.010 | C | price=0 | price=0 | 201 | BAJA |
| C.7.011 | C | durationMinutes null | duration=null | 400 | MEDIA |
| C.7.012 | C | durationMinutes=0 | duration=0 | 400 `@Min(1)` | MEDIA |
| C.7.013 | C | durationMinutes=1 | duration=1 | 201 | BAJA |
| C.7.014 | C | durationMinutes=10000 | duration muy alto | 201 (no hay max) — anotar como informativo | INFO |
| C.7.015 | C | description ausente | sin field | 201 | BAJA |
| C.7.016 | C | description muy largo | description = "x"*10000 | 201 si DB acepta TEXT, 500 si CHARACTER VARYING limitado | INFO |

### C.8 CreateClientRequest / UpdateClientRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.8.001 | C | fullName null | `{fullName:null}` | 400 | MEDIA |
| C.8.002 | C | fullName 150 chars | "a"*150 | 201 | BAJA |
| C.8.003 | C | fullName 151 chars | "a"*151 | 400 `@Size(max=150)` | MEDIA |
| C.8.004 | C | email omitido | sin email | 201 (opcional) | BAJA |
| C.8.005 | C | email null explícito | `email:null` | 201 | BAJA |
| C.8.006 | C | email mal formado | email="no-email" | 400 `@Email` | MEDIA |
| C.8.007 | C | email 151 chars | email muy largo | 400 `@Size(max=150)` | MEDIA |
| C.8.008 | C | phone 21 chars | phone="9"*21 | 400 `@Size(max=20)` | MEDIA |
| C.8.009 | C | notes muy largo | notes="x"*10000 | 201 si DB TEXT, 500 si VARCHAR | INFO |
| C.8.010 | C | notes con saltos de línea | notes="línea1\nlínea2" | 201, persistido literal | BAJA |

### C.9 CreateUserRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.9.001 | C | roleId null | `{roleId:null}` | 400 | MEDIA |
| C.9.002 | C | roleId=0 | roleId=0 | 400 `@Positive` | MEDIA |
| C.9.003 | C | roleId que no existe en `roles` | roleId=999 | 404 (RoleRepository.findById empty) | ALTA |
| C.9.004 | C | fullName null | fullName=null | 400 | MEDIA |
| C.9.005 | C | email null | email=null | 400 | MEDIA |
| C.9.006 | C | email mal formado | "notanemail" | 400 `@Email` | MEDIA |
| C.9.007 | C | password 7 chars | password="1234567" | 400 `@Size(min=8)` | MEDIA |
| C.9.008 | C | password 101 chars | password="a"*101 | 400 `@Size(max=100)` | MEDIA |
| C.9.009 | C | email global ya existe (find-or-create) | email = "admin@b1" (existe) creando en B2 | 201 + membership nueva sobre identidad existente, NO crea nuevo User | ALTA |
| C.9.010 | C | email global y misma membership (B1) | crear de nuevo `admin@b1` en B1 | 409 (ya hay UNIQUE `uq_membership_user_business`) | ALTA |
| C.9.011 | C | phone 21 chars | phone="9"*21 | 400 | BAJA |

### C.10 UpdateUserRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.10.001 | C | roleId null | `{roleId:null}` | 400 `@NotNull` | MEDIA |
| C.10.002 | C | roleId no existe | roleId=999 | 404 | ALTA |
| C.10.003 | C | Body con campos extra ignorados | `{roleId:2,"fullName":"X","email":"y@z"}` | 200, sólo cambia roleId; fullName/email NO cambian (campos no declarados en `UpdateUserRequest`) | ALTA |
| C.10.004 | C | UPDATE de la propia membership (admin se baja a employee) | `tkA(B1)` PUT /api/businesses/1/users/<su-propio-id> con roleId=EMPLOYEE | comportamiento documentado (200 + ahora EMPLOYEE en su próximo token) o 400/409 si hay regla | INFO |

### C.11 UpdateMeRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.11.001 | C | fullName null | `{fullName:null, email:"x@y"}` | 400 | MEDIA |
| C.11.002 | C | email null | email=null | 400 | MEDIA |
| C.11.003 | C | email mal formado | "no-email" | 400 `@Email` | MEDIA |
| C.11.004 | C | email duplicado en `users` | poner email de otro usuario existente | 409 (UNIQUE col-level users.email) | ALTA |
| C.11.005 | C | password en body | `{fullName:"X",email:"x@y","password":"hax"}` | 200 + password NO cambia (campo no declarado) | CRIT |
| C.11.006 | C | phone 21 chars | phone="9"*21 | 400 | BAJA |

### C.12 ChangePasswordRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.12.001 | C | currentPassword null | `{currentPassword:null,newPassword:"NewPass99"}` | 400 | MEDIA |
| C.12.002 | C | newPassword 7 chars | newPassword="1234567" | 400 `@Size(min=8)` | MEDIA |
| C.12.003 | C | newPassword 101 chars | newPassword="a"*101 | 400 `@Size(max=100)` | MEDIA |
| C.12.004 | C | currentPassword incorrecto | currentPassword="WRONG" | 400 o 401 (verificar contrato actual) | ALTA |
| C.12.005 | C | currentPassword == newPassword | mismos valores | 204 o 400 (depende de la regla; documentar) | INFO |
| C.12.006 | C | Después de PUT password, login con la antigua | login con currentPassword | 401 | ALTA |

### C.13 CreateEmployeeScheduleRequest / Update

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.13.001 | C | dayOfWeek null | `{dayOfWeek:null,…}` | 400 | MEDIA |
| C.13.002 | C | dayOfWeek=0 | dayOfWeek=0 | 400 `@Min(1)` | MEDIA |
| C.13.003 | C | dayOfWeek=8 | dayOfWeek=8 | 400 `@Max(7)` | MEDIA |
| C.13.004 | C | startTime null | start=null | 400 `@NotNull` | MEDIA |
| C.13.005 | C | endTime null | end=null | 400 | MEDIA |
| C.13.006 | C | startTime >= endTime | start=18:00, end=09:00 | 400 (CHECK chk_schedule_times) | ALTA |
| C.13.007 | C | startTime == endTime | start=end="10:00" | 400 | ALTA |
| C.13.008 | C | mismo dayOfWeek dos veces para mismo empleado | dos POST mismo dayOfWeek+membership | INFO: si la DB no tiene UNIQUE, 201; documentar | INFO |

### C.14 CreateEmployeeAbsenceRequest / Update

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.14.001 | C | startDateTime en pasado (Create) | start = "2020-01-01T00:00" | 400 `@FutureOrPresent` | MEDIA |
| C.14.002 | C | startDateTime ahora (Create) | start = now (within 1s) | 201 | BAJA |
| C.14.003 | C | startDateTime futuro | start = now+1d | 201 | BAJA |
| C.14.004 | C | endDateTime <= startDateTime | start=now+1d, end=now+1d-1h | 400 (CHECK chk_absence_times) | ALTA |
| C.14.005 | C | startDateTime null | start=null | 400 | MEDIA |
| C.14.006 | C | UPDATE con startDateTime en pasado | PUT con start = "2020-01-01T00:00" | 200 (intencionalmente SIN `@FutureOrPresent` en `UpdateEmployeeAbsenceRequest`) | INFO |
| C.14.007 | C | reason 256 chars | reason="x"*256 | 400 `@Size(max=255)` | MEDIA |
| C.14.008 | C | cross-tenant: PUT absence de B2 con `tkA(B1)` | path tiene businessId=1 pero el absence pertenece a B2 (forzar el id) | 403 (TenantGuard) o 404 (service no encuentra el id en B1) | ALTA |

### C.15 CreateAppointmentRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.15.001 | C | clientId null | `{clientId:null,…}` | 400 | MEDIA |
| C.15.002 | C | clientId=0 | clientId=0 | 400 `@Positive` | MEDIA |
| C.15.003 | C | membershipId null | membershipId=null | 400 | MEDIA |
| C.15.004 | C | startDateTime null | start=null | 400 | MEDIA |
| C.15.005 | C | startDateTime en pasado | start="2020-01-01T10:00" | 400 `@FutureOrPresent` | MEDIA |
| C.15.006 | C | startDateTime ahora | start=now | 201 (si pasa el resto de validaciones) | BAJA |
| C.15.007 | C | boothId omitido | sin boothId | 201 (opcional) | BAJA |
| C.15.008 | C | boothId=0 | boothId=0 | 400 `@Positive` | MEDIA |
| C.15.009 | C | serviceIds null | `serviceIds:null` | 400 `@NotEmpty` | MEDIA |
| C.15.010 | C | serviceIds=[] | `[]` | 400 `@NotEmpty` | MEDIA |
| C.15.011 | C | serviceIds=[null] | `[null]` | 400 (`@NotNull` por elemento — añadido 2026-05-17, commit `7c6d7f7`) | ALTA |
| C.15.012 | C | serviceIds=[-1] | `[-1]` | 400 (`@Positive` por elemento) | ALTA |
| C.15.013 | C | serviceIds=[0] | `[0]` | 400 `@Positive` | ALTA |
| C.15.014 | C | serviceIds con duplicados | `[1,1,1]` | 201 (calcula duración 3×; documentar) o 400 si hay dedupe | INFO |
| C.15.015 | C | notes muy largo | notes="x"*10000 | 201/500 según columna DB | INFO |

### C.16 UpdateAppointmentStatusRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.16.001 | C | statusName null | `{statusName:null}` | 400 `@NotBlank` | MEDIA |
| C.16.002 | C | statusName vacío | "" | 400 | MEDIA |
| C.16.003 | C | statusName espacios | "   " | 400 | MEDIA |
| C.16.004 | C | statusName desconocido | "FOO_BAR" | 400 (no existe en `appointment_statuses`) | ALTA |
| C.16.005 | C | statusName case-sensitive | "pending" minúsculas | 400 si la búsqueda es exact; 200 si normaliza; documentar | INFO |

### C.17 UpdatePaymentRequest

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.17.001 | C | isPaid null | `{isPaid:null}` | 400 `@NotNull` | MEDIA |
| C.17.002 | C | isPaid=true | `{isPaid:true}` | 200 + appointment.isPaid=true + `updated_at` cambia | ALTA |
| C.17.003 | C | isPaid=false (revertir) | `{isPaid:false}` | 200 + isPaid=false | ALTA |
| C.17.004 | C | isPaid=true dos veces seguidas (idempotente) | dos PATCH con isPaid=true | 200 ambos | BAJA |
| C.17.005 | C | isPaid="yes" (string) | `{isPaid:"yes"}` | 400 (deserialización) | MEDIA |

### C.18 Path params (todos los controllers con `@Validated`)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.18.001 | C | businessId=0 | GET /api/businesses/0 | 400 `@Positive` → handler `ConstraintViolationException` | MEDIA |
| C.18.002 | C | businessId=-1 | GET /api/businesses/-1 | 400 | MEDIA |
| C.18.003 | C | businessId="abc" | GET /api/businesses/abc | 400 `MethodArgumentTypeMismatchException` | MEDIA |
| C.18.004 | C | businessId enorme | GET /api/businesses/99999999999999999999 | 400 (overflow Long) | BAJA |
| C.18.005 | C | businessId con leading zeros | /api/businesses/01 | 200 o 400 (Spring acepta) | INFO |
| C.18.006 | C | path múltiples positivos | /api/businesses/1/users/0/schedules | 400 `@Positive` en userId | MEDIA |
| C.18.007 | C | businessId existe pero negocio desactivado | /api/businesses/<deactivated_id> con su tenant token | 200 + isActive=false (acceso permitido para reactivar) o 404 (documentar) | INFO |

### C.19 Mass assignment

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| C.19.001 | C | POST cliente con `id:999` | `{fullName:"X","id":999}` | 201 + id auto-generado (Jackson ignora) | ALTA |
| C.19.002 | C | POST cliente con `isActive:false` | `{fullName:"X","isActive":false}` | 201 + isActive=true (default) | ALTA |
| C.19.003 | C | POST cliente con `businessId:99` | `{fullName:"X","businessId":99}` | 201 + businessId del path (no del body) | CRIT |
| C.19.004 | C | POST cliente con `createdAt:"2020-01-01"` | con createdAt | 201 + createdAt=now (servidor) | ALTA |
| C.19.005 | C | PUT cliente con `id:999` | PUT /clients/1 con `{fullName:"X","id":999}` | 200 + id sigue 1 | ALTA |
| C.19.006 | C | POST appointment con `isPaid:true` directamente | en `CreateAppointmentRequest` el campo no existe | 201 + isPaid=false (default) | ALTA |
| C.19.007 | C | POST user con `passwordHash:"$2a$10$…"` | `{… ,"passwordHash":"$2a$"}` | 201 + el hash se genera en server desde `password`, NO se acepta el del body | CRIT |
| C.19.008 | C | UPDATE user con campos extra | PUT user con `{roleId:2, "fullName":"hax", "email":"hax@x.com"}` | 200, sólo roleId cambia; fullName/email intactos | ALTA |

**Subtotal C: 168 pruebas.**

---

## D. Reglas de negocio `AppointmentService.createAppointment`

15 pasos del Javadoc + concurrencia con curl paralelo (lock pesimista).

### D.1 Validaciones encadenadas (1 OK + 1+ KO por paso)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| D.1.001 | D | Paso 1 KO: business inexistente | path businessId=99, token genérico | 403 TenantGuard antes de llegar al service (con tenant token de otro business) | ALTA |
| D.1.002 | D | Paso 2 KO: clientId existe pero es de B2 | `tkA(B1)` + clientId de B2 | 404 `"No se encontró el cliente con ID X en el negocio Y"` | ALTA |
| D.1.003 | D | Paso 3 KO: cliente desactivado | `tkA(B1)` + clientId desactivado | 400 `"El cliente está desactivado"` | ALTA |
| D.1.004 | D | Paso 4 KO: membershipId de B2 | `tkA(B1)` + membershipId de B2 | 404 | ALTA |
| D.1.005 | D | Paso 5 KO: membership inactiva | `tkA(B1)` + membership con isActive=false | 400 `"El empleado está desactivado"` | ALTA |
| D.1.006 | D | Paso 6 OK: hora alineada al interval | business.appointmentInterval=30, start=14:30 | continúa (201 si todo lo demás OK) | ALTA |
| D.1.007 | D | Paso 6 KO: hora no alineada | interval=30, start=14:35 | 400 `"La hora no respeta el intervalo del negocio"` | ALTA |
| D.1.008 | D | Paso 6 — interval=15, start=14:15 | OK | 201 | MEDIA |
| D.1.009 | D | Paso 6 — interval=15, start=14:17 | KO | 400 | MEDIA |
| D.1.010 | D | Paso 7 KO: serviceId pertenece a B2 | `tkA(B1)` + serviceIds=[<B2>] | 404 | ALTA |
| D.1.011 | D | Paso 7 KO: mezcla de servicios de B1 y B2 | serviceIds=[<B1>, <B2>] | 404 (el de B2 no se encuentra) | ALTA |
| D.1.012 | D | Paso 8 KO: serviceId desactivado | service.isActive=false | 400 | ALTA |
| D.1.013 | D | Paso 9 OK: cálculo endDateTime | 2 servicios de 30 min, start=14:00 | endDateTime = 15:00 en BD | ALTA |
| D.1.014 | D | Paso 10 OK: cita en horario empleado | empleado tiene schedule L 09:00–18:00, cita L 10:00 | continúa | ALTA |
| D.1.015 | D | Paso 10 KO: cita fuera del horario | empleado 09:00–18:00, cita 19:00 | 400 `"La cita está fuera del horario del empleado"` | ALTA |
| D.1.016 | D | Paso 10 KO: cita acaba después del horario | empleado 09:00–18:00, cita 17:30 con 1h de servicio | 400 | ALTA |
| D.1.017 | D | Paso 10 — cita que cruza medianoche | empleado con schedule 22:00–06:00 (cruza); cita 23:00 con 1h | comportamiento documentado por `validateEmployeeSchedule` (debería permitir si el schedule también cruza) | INFO |
| D.1.018 | D | Paso 11 KO: solapamiento exacto | cita existente 14:00–15:00 PENDING, intento crear 14:00–15:00 mismo empleado | 409 `"Solapamiento con cita existente"` | CRIT |
| D.1.019 | D | Paso 11 KO: solapamiento por solape parcial | existente 14:00–15:00, nueva 14:30–15:30 | 409 | CRIT |
| D.1.020 | D | Paso 11 OK: cita pegada al final | existente 14:00–15:00, nueva 15:00–16:00 | 201 | ALTA |
| D.1.021 | D | Paso 11 OK: cita pegada al inicio | existente 14:00–15:00, nueva 13:00–14:00 | 201 | ALTA |
| D.1.022 | D | Paso 11: solapamiento con CANCELLED | existente CANCELLED en 14:00–15:00, nueva en mismo slot | 201 (CANCELLED se ignora) | ALTA |
| D.1.023 | D | Paso 11: solapamiento con NO_SHOW | existente NO_SHOW | 201 | ALTA |
| D.1.024 | D | Paso 11: solapamiento con COMPLETED | existente COMPLETED | 201 | ALTA |
| D.1.025 | D | Paso 11: solapamiento con IN_PROGRESS | existente IN_PROGRESS | 409 | CRIT |
| D.1.026 | D | Paso 11: solapamiento con CONFIRMED | existente CONFIRMED | 409 | CRIT |
| D.1.027 | D | Paso 12 KO: cita durante absence | empleado tiene absence 14:00–16:00 mañana, cita 14:30 mañana | 409 (validador añadido 2026-05-17, commit `c0f5e21`) | CRIT |
| D.1.028 | D | Paso 12 OK: cita justo después de absence | absence 14:00–16:00, cita 16:00 | 201 | ALTA |
| D.1.029 | D | Paso 13 KO: boothId de B2 | `tkA(B1)` + boothId de B2 | 404 | ALTA |
| D.1.030 | D | Paso 13 KO: booth desactivada | booth.isActive=false | 400 | ALTA |
| D.1.031 | D | Paso 13 KO: misma cabina, mismo slot, distinto empleado | dos empleados intentan booth=1 en 14:00–15:00 | 1ª 201, 2ª 409 | CRIT |
| D.1.032 | D | Paso 13 OK: distinta cabina, mismo slot | empleado A booth=1, empleado B booth=2, mismo horario | 201 ambos | ALTA |
| D.1.033 | D | Paso 14 KO: schedule_block global | block global el 2026-12-25, cita en ese día | 409 con message conteniendo `reason` | ALTA |
| D.1.034 | D | Paso 14 KO: schedule_block por empleado | block para membership=1 el 2026-12-25, cita ese día con membership=1 | 409 | ALTA |
| D.1.035 | D | Paso 14 OK: schedule_block por empleado, otra membership | block membership=1, cita con membership=2 mismo día | 201 | ALTA |
| D.1.036 | D | Paso 14 KO: schedule_block por cabina | block booth=1 el día X, cita con booth=1 ese día | 409 | ALTA |
| D.1.037 | D | Paso 14 OK: schedule_block por cabina, otra cabina | block booth=1, cita con booth=2 | 201 | ALTA |
| D.1.038 | D | Paso 15: AppointmentStatus PENDING | seed estándar | continúa, persistencia OK | INFO |
| D.1.039 | D | Paso 15 KO: PENDING ausente | seed manipulado (UPDATE appointment_statuses SET name='X' WHERE id=1) | 500 `"Estado PENDING no configurado"` | INFO (N/A en flujo normal) |
| D.1.040 | D | Post-15: BookedService.appliedPrice congelado | crear cita con servicio precio=10, después UPDATE service price=99 | GET appointment muestra appliedPrice=10 | CRIT |
| D.1.041 | D | Post-15: BookedService.appliedTaxPercentage congelado | crear cita con tax 21%, UPDATE tax to 25% | GET appointment muestra 21% | CRIT |
| D.1.042 | D | Post-15: status creado es PENDING | inspección | appointment.statusName="PENDING" | ALTA |
| D.1.043 | D | Post-15: AppointmentResponse incluye bookedServices con applied_* | response body | array con applied_price + applied_tax_percentage | ALTA |
| D.1.044 | D | Order de errores: aplica el primero | enviar cita con TODO mal (cliente B2, membership B2, fuera de horario) | 1er error que salta es paso 2 (cliente cross-tenant) — verificar orden literal del Javadoc | INFO |

### D.2 Concurrencia (lock pesimista `FOR UPDATE`)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| D.2.001 | D | Concurrencia empleado: 2 requests paralelas | 2× `curl POST /appointments` en background, mismo empleado, mismo slot (14:00–15:00 mañana) | 1ª 201, 2ª 409 (lock `MembershipRepository.findByIdAndBusinessIdForUpdate` previene TOCTOU) | CRIT |
| D.2.002 | D | Concurrencia cabina: 2 requests paralelas | 2× `curl`, distintos empleados, misma cabina, mismo slot | 1ª 201, 2ª 409 (lock `BoothRepository.findByIdAndBusinessIdForUpdate`) | CRIT |
| D.2.003 | D | Concurrencia con TX larga (simular delay con `sleep` en debugger) | manualmente: pausar la TX en paso 11 y enviar otra request | la 2ª espera el lock, no se ejecuta hasta soltar | CRIT |
| D.2.004 | D | Concurrencia sin lock: empleado distinto, cabina distinta | 2 requests con datos totalmente independientes | ambas 201 (locks distintos, no contention) | ALTA |
| D.2.005 | D | Concurrencia 10 requests paralelas mismo slot | 10× curl en background mismo empleado/slot | exactamente 1 201, 9 409 | CRIT |

**Subtotal D: 49 pruebas.**

---

## E. State machine `PATCH /api/businesses/{id}/appointments/{id}/status`

Cubre la tabla `VALID_TRANSITIONS` completa + transiciones inválidas explícitas + estados finales.

### E.1 Transiciones válidas

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| E.1.001 | E | PENDING → CONFIRMED | preparar appointment en PENDING, PATCH statusName="CONFIRMED" | 200 + status=CONFIRMED | ALTA |
| E.1.002 | E | PENDING → CANCELLED | PATCH statusName="CANCELLED" | 200 + status=CANCELLED | ALTA |
| E.1.003 | E | CONFIRMED → IN_PROGRESS | PATCH | 200 | ALTA |
| E.1.004 | E | CONFIRMED → CANCELLED | PATCH | 200 | ALTA |
| E.1.005 | E | CONFIRMED → NO_SHOW | PATCH | 200 | ALTA |
| E.1.006 | E | IN_PROGRESS → COMPLETED | PATCH | 200 | ALTA |
| E.1.007 | E | IN_PROGRESS → CANCELLED | PATCH | 200 | ALTA |

### E.2 Transiciones inválidas (no en VALID_TRANSITIONS)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| E.2.001 | E | PENDING → IN_PROGRESS | PATCH | 400 `"No se puede pasar de PENDING a IN_PROGRESS. Transiciones válidas: [CONFIRMED, CANCELLED]"` | ALTA |
| E.2.002 | E | PENDING → COMPLETED | PATCH | 400 | ALTA |
| E.2.003 | E | PENDING → NO_SHOW | PATCH | 400 | ALTA |
| E.2.004 | E | PENDING → PENDING (mismo estado) | PATCH | 400 | MEDIA |
| E.2.005 | E | CONFIRMED → COMPLETED (saltar IN_PROGRESS) | PATCH | 400 | ALTA |
| E.2.006 | E | CONFIRMED → PENDING (retroceder) | PATCH | 400 | ALTA |
| E.2.007 | E | CONFIRMED → CONFIRMED | PATCH | 400 | MEDIA |
| E.2.008 | E | IN_PROGRESS → PENDING | PATCH | 400 | ALTA |
| E.2.009 | E | IN_PROGRESS → CONFIRMED | PATCH | 400 | ALTA |
| E.2.010 | E | IN_PROGRESS → NO_SHOW (no permitido) | PATCH | 400 | ALTA |
| E.2.011 | E | IN_PROGRESS → IN_PROGRESS | PATCH | 400 | MEDIA |

### E.3 Estados finales (cualquier transición desde ellos = 400)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| E.3.001 | E | COMPLETED → CANCELLED | PATCH | 400 `"estado final"` | CRIT |
| E.3.002 | E | COMPLETED → PENDING | PATCH | 400 | ALTA |
| E.3.003 | E | COMPLETED → CONFIRMED | PATCH | 400 | ALTA |
| E.3.004 | E | COMPLETED → IN_PROGRESS | PATCH | 400 | ALTA |
| E.3.005 | E | COMPLETED → NO_SHOW | PATCH | 400 | ALTA |
| E.3.006 | E | COMPLETED → COMPLETED | PATCH | 400 | MEDIA |
| E.3.007 | E | CANCELLED → PENDING | PATCH | 400 | ALTA |
| E.3.008 | E | CANCELLED → CONFIRMED | PATCH | 400 | ALTA |
| E.3.009 | E | CANCELLED → IN_PROGRESS | PATCH | 400 | ALTA |
| E.3.010 | E | CANCELLED → COMPLETED | PATCH | 400 | ALTA |
| E.3.011 | E | CANCELLED → NO_SHOW | PATCH | 400 | ALTA |
| E.3.012 | E | CANCELLED → CANCELLED | PATCH | 400 | MEDIA |
| E.3.013 | E | NO_SHOW → PENDING | PATCH | 400 | ALTA |
| E.3.014 | E | NO_SHOW → CONFIRMED | PATCH | 400 | ALTA |
| E.3.015 | E | NO_SHOW → IN_PROGRESS | PATCH | 400 | ALTA |
| E.3.016 | E | NO_SHOW → COMPLETED | PATCH | 400 | ALTA |
| E.3.017 | E | NO_SHOW → CANCELLED | PATCH | 400 | ALTA |
| E.3.018 | E | NO_SHOW → NO_SHOW | PATCH | 400 | MEDIA |

### E.4 Errores fuera de tabla

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| E.4.001 | E | statusName desconocido | PATCH statusName="WAITING" | 400 (status no existe en `appointment_statuses`) | ALTA |
| E.4.002 | E | appointmentId inexistente | PATCH /appointments/9999/status | 404 | ALTA |
| E.4.003 | E | appointmentId cross-tenant | PATCH con id de B2 desde `tkA(B1)` | 404 | ALTA |
| E.4.004 | E | Body sin statusName | `{}` | 400 `@NotBlank` | MEDIA |

**Subtotal E: 40 pruebas.**

---

## F. Availability `GET /api/businesses/{id}/availability`

Único endpoint con query params validados por `@NotEmpty` y `@DateTimeFormat`.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| F.001 | F | Caso feliz | `?date=2026-12-01&serviceIds=1` (lunes, negocio abierto, servicios) | 200 + lista de slots no vacía con startTime/endTime/membershipId/membershipName/boothId/boothName | ALTA |
| F.002 | F | date ausente | `?serviceIds=1` | 400 `MissingServletRequestParameterException` (handler dedicado) | MEDIA |
| F.003 | F | serviceIds ausente | `?date=2026-12-01` | 400 `MissingServletRequestParameterException` o `@NotEmpty` | MEDIA |
| F.004 | F | serviceIds vacío | `?date=…&serviceIds=` | 400 `@NotEmpty` | MEDIA |
| F.005 | F | serviceIds=[null] | `?serviceIds=,` | 400 o behaviour documentado | INFO |
| F.006 | F | date inválida formato | `?date=2026-13-01` | 400 (parser ISO) | MEDIA |
| F.007 | F | date no ISO | `?date=01/12/2026` | 400 | MEDIA |
| F.008 | F | date año bisiesto válido | `?date=2028-02-29` | 200 | BAJA |
| F.009 | F | date año no bisiesto | `?date=2027-02-29` | 400 (parser) | MEDIA |
| F.010 | F | membershipId opcional set | `?date=…&serviceIds=1&membershipId=2` | 200 + sólo slots de membership=2 | ALTA |
| F.011 | F | boothId opcional set | `?…&boothId=1` | 200 + sólo slots con booth=1 | ALTA |
| F.012 | F | membershipId=0 | `?…&membershipId=0` | 400 `@Positive` | MEDIA |
| F.013 | F | boothId=-1 | `?…&boothId=-1` | 400 | MEDIA |
| F.014 | F | Negocio cerrado ese día (isClosed=true) | día con `business_hours.isClosed=true` | 200 + `slots=[]` (test unitario ya cubre esto) | ALTA |
| F.015 | F | Día sin entrada en business_hours | día con `dayOfWeek` no insertado | 200 + `slots=[]` | ALTA |
| F.016 | F | schedule_block global ese día | block global activo | 200 + `slots=[]` | ALTA |
| F.017 | F | schedule_block por empleado | block para membership=1 | 200 + slots NO incluyen membership=1, sí los demás | ALTA |
| F.018 | F | schedule_block por cabina | block para booth=1 | 200 + slots con boothId=1 ausentes | ALTA |
| F.019 | F | employee_absence parcial | absence 14:00–16:00 | 200 + slots en ese rango ausentes para ese empleado, los demás OK | ALTA |
| F.020 | F | Citas existentes activas | 1 cita PENDING 14:00–15:00 | 200 + slot 14:00–15:00 ausente para ese empleado | ALTA |
| F.021 | F | Citas CANCELLED ese día | cita CANCELLED 14:00–15:00 | 200 + slot 14:00–15:00 disponible | ALTA |
| F.022 | F | Negocio sin cabinas | DELETE todas las booths de B1 | 200 + slots con `boothId=null` (constraint cabina se relaja) | ALTA |
| F.023 | F | Negocio con todas las cabinas ocupadas | todas las booths con cita en mismo slot | 200 + `slots=[]` | ALTA |
| F.024 | F | membershipId que no pertenece al negocio | membership de B2 con `tkA(B1)` | 200 + `slots=[]` o 404; documentar | INFO |
| F.025 | F | serviceIds con servicio de otro negocio | serviceIds=[<B2>] con `tkA(B1)` | 404 o 200 vacío; documentar | INFO |
| F.026 | F | Día completo: paso del intervalo | business interval=30 → comprobar que los slots devueltos son cada 30 min | inspeccionar lista | ALTA |
| F.027 | F | Duración total > resto de horario | servicios suman 5h, queda 1h al final → último slot debe quedar fuera | inspeccionar lista | ALTA |

**Subtotal F: 27 pruebas.**

---

## G. Soft delete y reactivación

Según AGENTS.md, soft delete está en: `businesses`, `users`, `memberships`, `clients`, `taxes`, `service_categories`, `services`. Solo `Business` tiene endpoint `/reactivate`.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| G.001 | G | DELETE business → soft delete | DELETE /api/businesses/1 con `tkA(B1)` | 204; `businesses.is_active=0`; `deactivated_at` con timestamp | ALTA |
| G.002 | G | GET business desactivado | GET /api/businesses/1 después de G.001 | 200 + isActive=false (acceso permitido para ver el estado y reactivar) o 404; documentar | INFO |
| G.003 | G | PATCH reactivate business | PATCH /api/businesses/1/reactivate después de G.001 | 200 + isActive=true; `deactivated_at=null` | ALTA |
| G.004 | G | PATCH reactivate ya activo | PATCH /api/businesses/1/reactivate sin deactivar antes | 200 o 400 `"El negocio ya está activo"`; documentar | INFO |
| G.005 | G | DELETE client → soft delete | DELETE /api/businesses/1/clients/{id} | 204; client.isActive=false; `deactivated_at` set | ALTA |
| G.006 | G | GET clients list excluye desactivados | GET /…/clients después de G.005 | 200 + lista sin ese cliente (verificar contrato actual) | ALTA |
| G.007 | G | GET client desactivado por id | GET /…/clients/{id} desactivado | 200 con isActive=false o 404 (documentar) | INFO |
| G.008 | G | Reactivar cliente vía PUT | `CreateClientRequest`/`UpdateClientRequest` NO tienen `isActive` → confirmar que no existe forma exponible de reactivar cliente (gap) | inspección DTO + comportamiento | INFO |
| G.009 | G | DELETE cliente con citas asociadas | cliente con appointments futuras | 204 + appointments quedan con clientId apuntando a cliente desactivado (FK `RESTRICT` puede impedirlo) o 409 | INFO |
| G.010 | G | DELETE tax con servicios asociados | tax usada por un service activo | 204 (soft delete) o 409 si se valida (BD permite por ser soft delete) | INFO |
| G.011 | G | DELETE category con servicios | category con services | 204 (soft delete) | INFO |
| G.012 | G | DELETE service con citas en BookedService | service usado en appointment_services (FK NO ACTION) | 204 (soft delete; las BookedService históricas siguen apuntando, congeladas) | INFO |
| G.013 | G | DELETE membership | DELETE /api/businesses/1/users/{id} | 204; memberships.is_active=0 | ALTA |
| G.014 | G | Login con usuario cuya única membership está desactivada | después de G.013 sobre única membership | 401 `"Credenciales incorrectas"` (paso "memberships activas" del login) | ALTA |
| G.015 | G | POST appointment con cliente desactivado | clientId con isActive=false | 400 (paso 3) | ALTA |
| G.016 | G | POST appointment con membership desactivada | membershipId con isActive=false | 400 (paso 5) | ALTA |
| G.017 | G | POST appointment con servicio desactivado | serviceIds con un service desactivado | 400 (paso 8) | ALTA |
| G.018 | G | POST appointment con booth desactivada | boothId con isActive=false | 400 (paso 13) | ALTA |
| G.019 | G | DELETE recurso ya desactivado | DELETE /…/taxes/{ya-desactivado} | 204 idempotente o 400 `"ya está desactivado"`; documentar | INFO |
| G.020 | G | Listado con `?includeInactive=true` (si existe) | GET /…/clients?includeInactive=true | 404 / 200 ignorando param; documentar (no se mencionó query param en el mapa) | INFO |

**Subtotal G: 20 pruebas.**

---

## H. Edge cases temporales

JDBC URL usa `serverTimezone=UTC`. JVM probablemente Europe/Madrid en Docker (verificar).

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| H.001 | H | Appointment a medianoche | start="2026-12-01T00:00:00" | 201 si la membership tiene schedule que lo cubre | INFO |
| H.002 | H | Appointment cuya endDateTime cruza medianoche | start="2026-12-01T23:30:00" + 1h de servicios | 201 si schedule cruza medianoche; 400 si no — verificar `validateEmployeeSchedule` | INFO |
| H.003 | H | Año bisiesto válido | date="2028-02-29" en availability | 200 | BAJA |
| H.004 | H | 29 feb año no bisiesto | date="2027-02-29" | 400 (parser ISO) | BAJA |
| H.005 | H | startDateTime con `Z` (UTC explícito) | "2026-12-01T14:00:00Z" | 400 (parser de `LocalDateTime` no acepta zona) o ignora Z; documentar | INFO |
| H.006 | H | startDateTime sin zona | "2026-12-01T14:00:00" | 201 (LocalDateTime sin zona, interpretado como local del servidor) | INFO |
| H.007 | H | startDateTime con offset | "2026-12-01T14:00:00+02:00" | 400 | INFO |
| H.008 | H | DST transition (último domingo de marzo) | crear cita el 2026-03-29 con horario que cruce el cambio CET→CEST | comportamiento documentado (LocalDateTime ignora DST, persistir tal cual) | INFO |
| H.009 | H | GET appointments?from=2026-12-31&to=2027-01-01 | cruzar cambio de año | 200 + ambos días | BAJA |
| H.010 | H | BusinessHour "23:59" | startTime="23:59", endTime="00:00" | 400 (CHECK chk_bh_times_logic: start < end) | ALTA |
| H.011 | H | EmployeeAbsence cubriendo cambio de mes | start="2026-01-30T08:00", end="2026-02-02T18:00" | 201 | BAJA |
| H.012 | H | Verificar serverTimezone | inspeccionar `SHOW VARIABLES LIKE 'time_zone';` y JVM `TimeZone.getDefault()` | documentar valores y confirmar consistencia con JDBC URL | INFO |

**Subtotal H: 12 pruebas.**

---

## I. Paginación, filtros y ordenación

Endpoints con `Page<T>` (✅ Pag en mapa): taxes (19), booths (29), schedule-blocks (34), categories (38), services (43), clients (48), users (53), absences (63), appointments (68).
`spring.data.web.pageable.default-page-size=20`, `max-page-size=100`.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| I.001 | I | Paginación default | GET /…/clients sin params | 200 + `size=20`, `number=0` | ALTA |
| I.002 | I | size custom | `?size=10` | 200 + size=10 | ALTA |
| I.003 | I | size > max (clamp a 100) | `?size=101` | 200 + size=100 (Spring clamp) — confirmar | ALTA |
| I.004 | I | size > max enorme | `?size=10000` | 200 + size=100 | ALTA |
| I.005 | I | size=0 | `?size=0` | 200 con size=20 (Spring default si <=0) o 400; documentar | INFO |
| I.006 | I | size negativo | `?size=-1` | 200 con default o 400; documentar | INFO |
| I.007 | I | page negativo | `?page=-1` | 200 con page=0 o 400 | INFO |
| I.008 | I | page más alto que totalPages | `?page=999` | 200 + content=[] | BAJA |
| I.009 | I | sort por campo válido | `?sort=createdAt,desc` | 200 + ordenado | ALTA |
| I.010 | I | sort por campo inexistente | `?sort=foobar,asc` | 200 ignorando o 500 PropertyReferenceException — documentar | ALTA |
| I.011 | I | sort múltiple | `?sort=createdAt,desc&sort=id,asc` | 200 + ordenado por ambos | INFO |
| I.012 | I | Pageable.unpaged: ¿existe? | `?size=1000000` | clamp 100 | INFO |
| I.013 | I | Filtros appointments — from sólo | GET /…/appointments?from=2026-12-01 | 200 + sólo citas con startDateTime >= 2026-12-01 | ALTA |
| I.014 | I | Filtros — to sólo | `?to=2026-12-31` | 200 + sólo citas con endDateTime <= 2026-12-31 (verificar contrato exacto) | ALTA |
| I.015 | I | Filtros — from > to | `?from=2027-01-01&to=2026-01-01` | 200 + content=[] o 400 según implementación | INFO |
| I.016 | I | Filtros — membershipId | `?membershipId=1` | 200 + sólo citas con esa membership | ALTA |
| I.017 | I | Filtros — membershipId=0 | `?membershipId=0` | 400 `@Positive` | MEDIA |
| I.018 | I | Filtros — from malformado | `?from=01-12-2026` | 400 | MEDIA |
| I.019 | I | Endpoints sin paginación — hours | GET /…/hours | 200 + `List<…>` (no Page envelope) | INFO |
| I.020 | I | Endpoints sin paginación — schedules | GET /…/users/{id}/schedules | 200 + `List<…>` | INFO |

**Subtotal I: 20 pruebas.**

---

## J. Mensajes de error

Formato uniforme `ErrorResponse(int status, String error, String message, String timestamp)`. El campo `error` lleva siempre `"<código> <REASON_PHRASE>"` (verificado 2026-05-17, observación 4123).

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| J.001 | J | 404 cliente inexistente | GET /…/clients/9999 | `{status:404, error:"404 NOT_FOUND", message:"No se encontró el cliente con ID 9999 en el negocio con ID 1", timestamp:"<ISO>"}` | MEDIA |
| J.002 | J | 401 sin token | GET /api/me sin Authorization | `{status:401, error:"401 UNAUTHORIZED", message:"…", timestamp}` en español, sin stack trace | MEDIA |
| J.003 | J | 403 cross-tenant | `tkA(B1)` GET /api/businesses/2 | `{status:403, error:"403 FORBIDDEN", message:"…"}` | MEDIA |
| J.004 | J | 400 Bean Validation | POST cliente con fullName=null | `{status:400, error:"400 BAD_REQUEST", message:"<detalle del campo y regla violada>"}` | MEDIA |
| J.005 | J | 400 ConstraintViolation path | GET /api/businesses/0 | `{status:400, …, message:"<campo>: <regla>"}` | MEDIA |
| J.006 | J | 405 método no soportado | GET /api/auth/token | `{status:405, error:"405 METHOD_NOT_ALLOWED", message conteniendo métodos válidos}` + header `Allow: POST` | BAJA |
| J.007 | J | 415 content-type | POST con XML | `{status:415, error:"415 UNSUPPORTED_MEDIA_TYPE", message:…}` | BAJA |
| J.008 | J | 404 path inexistente | GET /api/no-existe | `{status:404, error:"404 NOT_FOUND", message:…}` (handler `NoResourceFoundException`) | BAJA |
| J.009 | J | 409 conflict (slug duplicado) | POST /api/auth/register con slug repetido | `{status:409, error:"409 CONFLICT", message:"Ya existe…"}` | MEDIA |
| J.010 | J | 500 catch-all sin leak | forzar 500 (ej. setear `appointment_statuses.name='X' WHERE id=1` y crear cita) | `{status:500, error:"500 INTERNAL_SERVER_ERROR", message:"<mensaje genérico>"}` sin stack ni nombres SQL ni paquetes Java | CRIT |
| J.011 | J | Idioma de los mensajes | revisar muestreo de 10 errores 404/400/409 | todos en español, sin "Not Found", "Bad Request" en el campo `message` | BAJA |
| J.012 | J | timestamp formato ISO | cualquier error | `timestamp` matchea regex ISO Instant (`YYYY-MM-DDTHH:mm:ss.SSSZ` o `YYYY-MM-DDTHH:mm:ssZ`) | BAJA |
| J.013 | J | Ningún error filtra columnas SQL | provocar UNIQUE violation | message NO contiene "uq_tax_business_name" ni "duplicate key" en bruto | CRIT |
| J.014 | J | Ningún error filtra path interno | cualquier 500 | message NO contiene "com.optima…" ni paths de package | CRIT |
| J.015 | J | 429 rate limit | tras A.6.001 | ErrorResponse 429 con message en español "Demasiadas peticiones…" | BAJA |

**Subtotal J: 15 pruebas.**

---

## K. Geocoding y SMTP (best-effort)

`GeocodingService` requiere `country` + (`city` o `postalCode`). `MailService` es best-effort (loggea fallo sin romper).

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| K.001 | K | Register con address completa | business.address="Calle Mayor 1", city="Madrid", country="ES", postalCode="28013" | 201 + BusinessResponse.latitude/longitude llenos (Nominatim resolvió) | INFO |
| K.002 | K | Register sin address | sin address/city/country/postalCode | 201 + latitude/longitude null | INFO |
| K.003 | K | Register con country pero sin city ni postalCode | country="ES", resto null | 201 + lat/long null (no llama Nominatim) | INFO |
| K.004 | K | Register con city pero sin country | city="Madrid", country=null | 201 + lat/long null | INFO |
| K.005 | K | Register con address claramente inválida | address="!!XYZ no-existe-este-sitio!!", city="?", country="ES" | 201 + lat/long null (Nominatim devuelve 0 resultados → service degrada a null) | INFO |
| K.006 | K | Nominatim timeout 5s | bloquear acceso a nominatim.openstreetmap.org (firewall local o /etc/hosts → 127.0.0.1) | 201 + lat/long null + log.warn "Geocoding falló" — no debe propagar excepción | ALTA |
| K.007 | K | PUT business cambiando address | PUT /api/businesses/1 con address nueva | 200 + lat/long re-resuelto | INFO |
| K.008 | K | Register con SMTP caído | apagar `spring.mail.host` (host inalcanzable) y register OK | 201 + business creado + log.warn "Welcome email skipped" — no propaga | ALTA |
| K.009 | K | forgot-password con SMTP caído | mismo escenario | 204 + token guardado en BD; log.warn; usuario no ve diferencia | ALTA |
| K.010 | K | SMTP timeout 5s | host con delay alto | la request no debe colgarse > 6s; degrada a best-effort | ALTA |

**Subtotal K: 10 pruebas.**

---

## L. N+1 y rendimiento ligero

Activar `logging.level.org.hibernate.SQL=DEBUG` temporalmente. Crear seed de 50 citas en B1.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| L.001 | L | GET appointments con N=50 | GET /api/businesses/1/appointments?size=50 (50 citas en B1) | 1 SELECT principal de appointments + 1 SELECT batch de booked_services + 1 SELECT de memberships (join). Sin N+1 (más de ~5 queries por la lista sería sospechoso) | ALTA |
| L.002 | L | GET clients con N=100 | seed con 100 clientes, GET ?size=100 | ~2 queries (count + select) | ALTA |
| L.003 | L | GET users con N=50 | 50 memberships con identidad asociada | ≤ 3 queries (count + memberships + users) — no 50 queries por user | ALTA |
| L.004 | L | GET availability con día lleno | 50 citas + 5 empleados + 5 cabinas en mismo día | tiempo de respuesta < 2s y queries acotadas (1 query de schedules + 1 absences + 1 schedule_blocks + 1 appointments) | ALTA |
| L.005 | L | GET appointment by id | GET /…/appointments/{id} | 1 SELECT appointment + 1 SELECT booked_services (batch). No 1 query por BookedService | ALTA |
| L.006 | L | AppointmentResponse.from no llama repos | trazado de queries durante listado | la conversión a DTO no añade queries por elemento (verificado refactor del commit `1f50810`) | CRIT |

**Subtotal L: 6 pruebas.**

---

## M. Preparación para frontend

CORS: `allowedOrigins=*`, `allowedMethods=GET,POST,PUT,PATCH,DELETE,OPTIONS`, `allowCredentials=false`.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| M.001 | M | OPTIONS preflight | `OPTIONS /api/me` con `Origin: http://localhost:3000`, `Access-Control-Request-Method: GET` | 200 + headers `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`, `Access-Control-Allow-Headers: *` | ALTA |
| M.002 | M | OPTIONS de origin externo | `OPTIONS /api/auth/token` con `Origin: https://attacker.com` | 200 con ACAO=`*` (permisivo en dev — documentar tarea pendiente de cerrar allowedOrigins) | INFO |
| M.003 | M | OPTIONS sin Origin header | `OPTIONS /api/me` sin Origin | 200 o 400; documentar | INFO |
| M.004 | M | POST devuelve 201 | POST /api/businesses/1/clients OK | 201 (no 200) | MEDIA |
| M.005 | M | DELETE devuelve 204 sin body | DELETE /api/businesses/1/clients/{id} | 204 + body vacío | MEDIA |
| M.006 | M | GET devuelve 200 | cualquier GET OK | 200 con JSON | BAJA |
| M.007 | M | PATCH devuelve 200 | PATCH appointment/status | 200 | BAJA |
| M.008 | M | PUT devuelve 200 | PUT cliente | 200 | BAJA |
| M.009 | M | Content-Type respuesta | cualquier respuesta JSON | `application/json` con charset=utf-8 | BAJA |
| M.010 | M | DELETE idempotente | DELETE 2 veces el mismo cliente | 1ª 204, 2ª 404 (decir contrato actual) | BAJA |
| M.011 | M | PUT idempotente | PUT cliente con mismo body 2 veces | 200 ambas; estado convergente | BAJA |
| M.012 | M | Allow-Credentials=false explícito | revisar headers en respuesta a request con Origin | sin `Access-Control-Allow-Credentials: true` (porque `allowCredentials=false`) | MEDIA |

**Subtotal M: 12 pruebas.**

---

## N. Seguridad — Inyección y vectores de ataque

### N.1 Inyección SQL empírica

Antes de empezar, capturar `cnt_users_before = SELECT COUNT(*) FROM users`, `cnt_appointments_before`, `cnt_businesses_before`. Después de cada caso, recomprobar.

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.1.001 | N | SQLi cliente fullName | POST /…/clients con `{"fullName":"' OR 1=1 --","email":"x@x.com"}` | 201 + fullName almacenado LITERAL ("' OR 1=1 --"). cnt sin cambios | CRIT |
| N.1.002 | N | SQLi tax name (DROP TABLE) | POST /…/taxes con `name="'; DROP TABLE users; --"` (50 chars max → necesita ajustarse) | 400 si excede 50 chars; si cabe, 201 + literal; cnt_users sin cambios | CRIT |
| N.1.003 | N | SQLi en register email | `admin.email="admin'); DROP TABLE users; --@x.com"` | 400 `@Email` (porque el email no es válido) o 201 literal; cnt_users sin cambios | CRIT |
| N.1.004 | N | SQLi en notes multilínea | POST cliente con notes="test\n'; DELETE FROM appointments; --" | 201 + notes literal; cnt_appointments sin cambios | CRIT |
| N.1.005 | N | SQLi en path | GET `/api/businesses/1/clients/1 OR 1=1` | 400 (@Positive falla al deserializar Long) | ALTA |
| N.1.006 | N | SQLi en query sort | GET /…/clients?sort=name'; DROP TABLE users; -- | 400 (Spring rechaza sort con caracteres prohibidos) o 200 ignorando; cnt_users sin cambios | CRIT |
| N.1.007 | N | Stacked queries en fullName | POST cliente fullName="X'; DROP TABLE users; --" | 201 + literal; cnt sin cambios | CRIT |
| N.1.008 | N | SQLi en business.name | POST register business.name="'; UPDATE businesses SET is_active=0; --" | 201 + literal (150 chars cabe); cnt sin cambios, otros businesses intactos | CRIT |
| N.1.009 | N | SQLi en availability serviceIds | GET /…/availability?serviceIds=1; DROP TABLE users; -- | 400 (parser Long falla) | CRIT |
| N.1.010 | N | Recuento final | después de N.1.001–N.1.009 | `cnt_users_after == cnt_users_before` y mismas filas en businesses + appointments | CRIT |

### N.2 Inyección SQL — análisis estático (grep)

Ya ejecutado preliminarmente; documentar cada uno:

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.2.001 | N | grep `createNativeQuery` | en `src/main/java` | 0 resultados | CRIT (si aparece) |
| N.2.002 | N | grep `createQuery.*\+` | en `src/main/java` | 0 resultados | CRIT (si aparece) |
| N.2.003 | N | grep `@Query.*\+` (concat strings) | en `src/main/java` | 0 resultados (todos los `@Query` usan parámetros con `:name` o `?n`) | CRIT (si aparece) |
| N.2.004 | N | grep `jdbcTemplate` | en `src/main/java` | 0 resultados | CRIT (si aparece) |
| N.2.005 | N | grep `Statement\.` (sin `Prepared`) | en `src/main/java` | 0 resultados | CRIT (si aparece) |
| N.2.006 | N | Pre-run baseline | preliminar 2026-05-17 | confirmado: ninguna de las 5 búsquedas devuelve archivos del módulo de negocio | INFO |

### N.3 XSS (política API: NO sanitiza; frontend escapa al renderizar)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.3.001 | N | XSS script tag en fullName | POST cliente fullName="<script>alert(1)</script>" | 201 + almacenado literal; GET devuelve string sin sanitizar | INFO (responsabilidad del frontend) |
| N.3.002 | N | XSS img onerror en notes | POST cliente notes='"><img src=x onerror=alert(1)>' | 201 + literal | INFO |
| N.3.003 | N | XSS svg onload en tax name | POST tax name="<svg onload=alert(1)>" | 201 si cabe en 50 chars + literal | INFO |
| N.3.004 | N | XSS en business.name | register con business.name="<script>x</script>" | 201 + literal | INFO |
| N.3.005 | N | Política documentada | revisar README/AGENTS o añadir nota | informe del audit deja claro: "API almacena strings literales sin sanitizar; el frontend DEBE escapar antes de renderizar HTML" | INFO |

### N.4 JWT manipulation

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.4.001 | N | alg=none | construir JWT con `{"alg":"none","typ":"JWT"}` y sin firma | 401 (jjwt rechaza alg=none por defecto) | CRIT |
| N.4.002 | N | alg=HS256 en vez de HS384 | firmar con HS256 sobre la misma clave | 401 (clave configurada con HS384) | CRIT |
| N.4.003 | N | userId modificado en payload | tomar JWT válido, alterar `userId` en payload, mantener firma original | 401 (firma inválida porque el payload cambió) | CRIT |
| N.4.004 | N | businessId modificado | misma estrategia, cambiar `businessId` | 401 | CRIT |
| N.4.005 | N | role modificado a SUPERADMIN | cambiar `role` a "SUPERADMIN" | 401 (firma rota) | CRIT |
| N.4.006 | N | exp en el pasado | crear token con `exp` = now-3600 | 401 | ALTA |
| N.4.007 | N | sub que no existe en BD | sub="ghost@x.com" pero JWT bien firmado | 401 (`UserRepository.findByEmailIgnoreCase` empty) | CRIT |
| N.4.008 | N | Token sin `sub` | claims sin sub, firma OK | 401 | CRIT |
| N.4.009 | N | Token sin `userId` | claims sin userId, firma OK | 401 (`AuthPrincipal` no se construye) | CRIT |
| N.4.010 | N | Token con `businessId` apuntando a negocio inexistente | bien firmado pero businessId=99999 | TenantGuard pasa (sólo compara con path), service falla 404 / o 403 si llega antes | ALTA |
| N.4.011 | N | Token con `role` no estándar ("HACKER") | role="HACKER", bien firmado | hasRole('ADMIN') falla → 403 en endpoints ADMIN | ALTA |
| N.4.012 | N | Token muy largo (>10KB) | claims con campo arbitrario gigante | 401 / 413 / parser exception manejado | BAJA |
| N.4.013 | N | Token con firma de longitud incorrecta | truncar el último byte de la firma | 401 | ALTA |

### N.5 Path traversal

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.5.001 | N | `..` en path | GET /api/businesses/../etc/passwd | 404 (Spring normaliza o no resuelve) — sin lectura de filesystem | CRIT |
| N.5.002 | N | `%2e%2e` (URL encoded) | GET /api/businesses/%2e%2e/etc | 400 o 404 | CRIT |
| N.5.003 | N | `..` intercalado | GET /api/businesses/1/../1/clients | 200 (Spring normaliza a /api/businesses/1/clients) o 404 | ALTA |
| N.5.004 | N | doble slash | GET /api/businesses//1/clients | 404 o normaliza a /api/businesses/1/clients; documentar | INFO |
| N.5.005 | N | path con `\` | GET /api/businesses/1\..\..\etc | 400/404 | BAJA |

### N.6 Header injection

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.6.001 | N | Authorization con `\r\n` | `Authorization: Bearer xxx\r\nX-Injected: yes` | 401 (Tomcat normaliza headers; no se inyecta header adicional) | ALTA |
| N.6.002 | N | Authorization con `\0` | bytes con NUL | 400 (Tomcat rechaza control chars) | ALTA |
| N.6.003 | N | Origin con Unicode RTL | `Origin: http://‮example.com` | 200 (CORS *) sin filtración de info | BAJA |
| N.6.004 | N | Content-Length manipulado | Content-Length que no coincide con el body | 400 (Tomcat lo detecta) | ALTA |

### N.7 Mass assignment (cubierto parcialmente en C.19; aquí énfasis seguridad)

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.7.001 | N | UpdateMe con passwordHash | PUT /api/me con `{"fullName":"X","email":"x@y","passwordHash":"$2a$10$hax"}` | 200 + passwordHash NO cambia | CRIT |
| N.7.002 | N | UpdateMe con email a uno ajeno (verificación de UNIQUE) | PUT /api/me con `email` de otro user existente | 409 (UNIQUE users.email) | ALTA |
| N.7.003 | N | POST register con role=SUPERADMIN | Inyectar `{"admin":{…,"role":"SUPERADMIN"}}` | 201 + el rol asignado es ADMIN (campo `role` no existe en AdminAccount) | CRIT |
| N.7.004 | N | POST appointment con statusId | `{…,"statusId":2}` (queremos saltarnos PENDING) | 201 + status=PENDING (campo ignorado) | CRIT |
| N.7.005 | N | Crear membership con id_role inválido | POST user con roleId=999 (no existe) | 404, no se crea membership con role corrupto | ALTA |

### N.8 IDOR

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.8.001 | N | Enumeración de clientes | `tkA(B1)` GET /api/businesses/1/clients/1,2,3,…,20 | IDs que existen en B1: 200; IDs que existen en B2: 404 (nunca 200) | CRIT |
| N.8.002 | N | Enumeración cross-tenant directo | `tkA(B1)` GET /api/businesses/2/clients/1 | 403 TenantGuard | CRIT |
| N.8.003 | N | IDOR appointments | `tkA(B1)` GET /api/businesses/1/appointments/<id-de-B2> | 404 | CRIT |
| N.8.004 | N | IDOR users | `tkA(B1)` GET /api/businesses/1/users/<membership-id-de-B2> | 404 | CRIT |
| N.8.005 | N | IDOR booth | mismo patrón con booth | 404 | CRIT |
| N.8.006 | N | IDOR availability con membershipId ajeno | `tkA(B1)` GET /api/businesses/1/availability?…&membershipId=<de-B2> | 200 vacío o 404 (verificar) | CRIT |

### N.9 BCrypt

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.9.001 | N | Hash empieza por $2a$ o $2b$ | `SELECT password_hash FROM users LIMIT 1` | hash con prefijo `$2a$` o `$2b$` | CRIT (si plano) |
| N.9.002 | N | Cost factor ≥ 10 | inspeccionar hash: `$2a$10$…` o superior | cost ≥ 10 | ALTA |
| N.9.003 | N | Salt único por usuario | comparar 2 users con misma password en seed: hashes distintos | hashes distintos (salt único) | CRIT |
| N.9.004 | N | Timing attack — usuario inexistente vs password incorrecto | 100 requests con email inexistente vs 100 con email válido+password incorrecto, medir media | si email inexistente <50ms y password incorrecto >150ms → leak temporal de existencia de usuarios. Documentar: AuthService.java:89-94 hace early-return SIN BCrypt match en el caso "usuario inexistente"; sí hay leak teórico | ALTA |
| N.9.005 | N | Login con password vacío | `{"email":"admin@b1","password":""}` | 400 `@NotBlank` (no llega a BCrypt) | MEDIA |
| N.9.006 | N | Login con password de 1000 chars | password muy largo | 401 (BCrypt no debería bloquearse; jjwt acepta) | BAJA |

### N.10 Logs sensibles

Activar `logging.level.root=DEBUG`, capturar `docker compose logs api` durante:

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| N.10.001 | N | Login OK no loguea password | hacer A.1.001 con DEBUG | logs NO contienen "12345678"; sí contienen `log.info` con email | CRIT |
| N.10.002 | N | Login KO no loguea password | A.1.005 con DEBUG | logs NO contienen "WRONG_PASS" | CRIT |
| N.10.003 | N | Login KO loguea email (es OK) | `log.warn` en AuthService | log incluye email parcial o entero — documentar política | INFO |
| N.10.004 | N | Register no loguea password | A.2.001 con DEBUG | logs NO contienen el password | CRIT |
| N.10.005 | N | Register no loguea passwordHash | logs DEBUG | logs NO contienen `$2a$` o `$2b$` (el hash BCrypt) | CRIT |
| N.10.006 | N | forgot-password no loguea token plano | A.3.001 con DEBUG | logs NO contienen el token enviado por email (sólo el hash o nada) | CRIT |
| N.10.007 | N | JWT no aparece completo en logs | A.1.001 | logs no contienen el token JWT entero (puede contener `userId`/`email`, no el token) | ALTA |
| N.10.008 | N | Hibernate SQL DEBUG | `show-sql=true` ya activo | parámetros vinculados pueden aparecer si activamos `org.hibernate.orm.jdbc.bind=TRACE` — documentar y desactivar en prod | ALTA |
| N.10.009 | N | Stack traces no exponen secret | provocar excepción dentro de JwtUtil | el log puede mostrar la clase pero NO la clave secreta | CRIT |

**Subtotal N: 69 pruebas.**

---

## O. Otros — hallazgos del mapa real

Cosas que están en el mapa o en el código y que merecen prueba aunque el usuario no las listó explícitamente.

### O.1 CHECK constraints SQL nivel BD

Casos en los que el bug podría meterse por la vía JPA si la validación de servicio fallara. Forzar el INSERT directo en SQL (no via API).

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.1.001 | O | chk_appointment_interval | `UPDATE businesses SET appointment_interval=20 WHERE id=1` | CHECK constraint violation | INFO |
| O.1.002 | O | chk_day_of_week (employee_schedules) | INSERT con day_of_week=8 | violation | INFO |
| O.1.003 | O | chk_schedule_times | INSERT employee_schedule con start>=end | violation | INFO |
| O.1.004 | O | chk_tax_percentage | INSERT taxes percentage=101 | violation | INFO |
| O.1.005 | O | chk_service_price | INSERT services price=-1 | violation | INFO |
| O.1.006 | O | chk_service_duration | INSERT services duration_minutes=0 | violation | INFO |
| O.1.007 | O | chk_appointment_times | INSERT appointment con start>=end (saltar paso 9) | violation | INFO |
| O.1.008 | O | chk_appsvc_applied_price | INSERT appointment_services con applied_price=-1 | violation | INFO |
| O.1.009 | O | chk_appsvc_applied_tax | INSERT con applied_tax_percentage=101 | violation | INFO |
| O.1.010 | O | chk_bh_day_of_week | INSERT business_hours day_of_week=0 | violation | INFO |
| O.1.011 | O | chk_bh_times_logic | INSERT business_hours isClosed=false sin times | violation | INFO |
| O.1.012 | O | chk_absence_times | INSERT employee_absences start>=end | violation | INFO |
| O.1.013 | O | chk_block_dates | INSERT schedule_blocks start>end | violation | INFO |
| O.1.014 | O | chk_block_target | INSERT schedule_blocks con membership y booth ambos set | violation | INFO |

### O.2 UNIQUE constraints SQL

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.2.001 | O | UNIQUE businesses.slug | doble POST register con mismo slug | 1ª 201, 2ª 409 | ALTA |
| O.2.002 | O | UNIQUE businesses.email | doble POST register con misma business.email | 409 | ALTA |
| O.2.003 | O | UNIQUE users.email (global) | crear 2 users con mismo email via UserService.create | 409 o re-uso por find-or-create | ALTA |
| O.2.004 | O | uq_membership_user_business | crear 2 memberships mismo user en mismo business | 409 | ALTA |
| O.2.005 | O | uq_business_hours_day | doble POST hours con mismo dayOfWeek en B1 | 409 (constraint añadido 2026-05-17, commit `fac0760`) | ALTA |
| O.2.006 | O | UNIQUE appointment_statuses.name | INSERT directo | violation | INFO |
| O.2.007 | O | UNIQUE roles.name | INSERT directo | violation | INFO |
| O.2.008 | O | UNIQUE password_resets.token_hash | dos forgot-password con misma collision (artificial) | violation | INFO (improbable) |

### O.3 FKs y ON DELETE

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.3.001 | O | CASCADE business_hours al borrar business | hard `DELETE FROM businesses WHERE id=1` (saltando soft delete) | business_hours del business 1 desaparece | INFO |
| O.3.002 | O | CASCADE appointment_services al borrar appointment | hard delete appointment | booked_services asociados desaparecen | INFO |
| O.3.003 | O | CASCADE employee_absences al borrar membership | hard delete membership | absences asociadas desaparecen | INFO |
| O.3.004 | O | CASCADE password_resets al borrar user | hard delete user | tokens del user desaparecen | INFO |
| O.3.005 | O | NO ACTION en FK normal | intentar hard delete business con clientes | error FK constraint | INFO |

### O.4 Datos sensibles no expuestos

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.4.001 | O | UserResponse NO incluye passwordHash | GET /api/businesses/1/users/{id} | body sin campo passwordHash (confirmado por Javadoc `UserResponse.java:10`) | CRIT |
| O.4.002 | O | MeResponse NO incluye passwordHash | GET /api/me | body sin passwordHash | CRIT |
| O.4.003 | O | TokenResponse NO incluye password | login OK | sin password ni hash | CRIT |
| O.4.004 | O | ForgotPassword response sin token | A.3.001 | body 204 (sin body), token NO devuelto por la API | CRIT |
| O.4.005 | O | PasswordResetToken nunca aparece en GET | no hay endpoint GET para password_resets | confirmar | INFO |

### O.5 Swagger / OpenAPI

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.5.001 | O | Swagger UI accesible | GET /swagger-ui.html | 200 HTML | INFO |
| O.5.002 | O | API docs accesible | GET /v3/api-docs | 200 JSON OpenAPI | INFO |
| O.5.003 | O | bearerAuth declarado | revisar securitySchemes en /v3/api-docs | sección con `bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }` | INFO |
| O.5.004 | O | Endpoints documentados | spot-check 5 endpoints en Swagger UI | cada uno con descripción, request body, response body | INFO |

### O.6 Datos congelados en BookedService

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.6.001 | O | appliedPrice congelado tras update servicio | crear cita con servicio precio=10, UPDATE servicio precio=99, GET cita | applied_price del BookedService sigue 10 | CRIT |
| O.6.002 | O | appliedTaxPercentage congelado | crear cita con tax 21%, UPDATE tax to 25%, GET cita | applied_tax_percentage sigue 21 | CRIT |
| O.6.003 | O | Cita histórica con servicio borrado | crear cita, soft-delete servicio (servicio.isActive=false), GET cita | applied_* siguen visibles; nombre del servicio puede mostrar isActive=false; verificar | INFO |

### O.7 Endpoints "no listados pero existentes"

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.7.001 | O | PATCH /api/businesses/{id}/reactivate | tras DELETE business | 200 + reactivado (endpoint existe, no aparecía en AGENTS.md) | ALTA |
| O.7.002 | O | POST /api/businesses con ADMIN | endpoint marcado "código muerto"; intentar usarlo | 201 (técnicamente funciona pero el ADMIN ya tiene business) — documentar | INFO |
| O.7.003 | O | GET /api/me/businesses con tenant token | `tkA(B1)` | 200 + lista (también devuelve las memberships del user, no sólo la del tenant actual) | INFO |

### O.8 UTF-8 y encodings

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.8.001 | O | Cliente con acentos y eñes | fullName="José María Núñez" | 201 + GET devuelve idéntico | MEDIA |
| O.8.002 | O | Cliente con emojis | fullName="Diego 🎉🚀" | 201 + GET literal (BD utf8mb4 confirma) | INFO |
| O.8.003 | O | Cliente con chino/japonés | fullName="山田太郎" | 201 + GET literal | INFO |
| O.8.004 | O | Tax name con acento | name="IVA Reducido" | 201 + GET literal | BAJA |

### O.9 Idempotencia y operaciones repetidas

| ID | Bloque | Endpoint/Caso | Input | Esperado | Sev |
|---|---|---|---|---|---|
| O.9.001 | O | DELETE cliente 2× | DELETE /…/clients/{id} dos veces | 1ª 204, 2ª 404 ("ya no existe" / "ya desactivado") | BAJA |
| O.9.002 | O | PATCH status idempotente cuando es válido | PATCH status="CONFIRMED" 2 veces seguidas | 1ª 200, 2ª 400 (CONFIRMED→CONFIRMED no listado) | INFO |
| O.9.003 | O | PATCH payment idempotente | PATCH isPaid=true 2× | 200 ambas | BAJA |

**Subtotal O: 49 pruebas.**

---

## Z. Resumen del plan

### Z.1 Conteo total y por bloque

| Bloque | Nombre | Pruebas |
|---|---|---|
| A | Autenticación | 78 |
| B | Autorización | 47 |
| C | Validación de DTOs | 168 |
| D | Reglas de negocio appointment | 49 |
| E | State machine | 40 |
| F | Availability | 27 |
| G | Soft delete y reactivación | 20 |
| H | Edge cases temporales | 12 |
| I | Paginación, filtros, ordenación | 20 |
| J | Mensajes de error | 15 |
| K | Geocoding y SMTP | 10 |
| L | N+1 y rendimiento | 6 |
| M | Preparación frontend (CORS, códigos HTTP) | 12 |
| N | Seguridad (inyección + vectores) | 69 |
| O | Otros (CHECK/UNIQUE/FK SQL, datos congelados, UTF-8, Swagger) | 49 |
| **TOTAL** | | **622** |

### Z.2 Estimación de tiempo por bloque (ejecución manual)

Asumiendo Postman + curl + SQL Workbench abiertos en paralelo. No incluye fix de bugs encontrados.

| Bloque | Estimación | Notas |
|---|---|---|
| A | 4–5 h | Los rate limits (A.6.001-009) tardan wall-clock por refill de buckets (logreseteo manual entre tests o esperar). |
| B | 2 h | Cambiar token entre tests; construir tokens manipulados con `jwt.io`. |
| C | 5–6 h | 137 casos; muchos son edits de un campo del body Postman. |
| D | 4–5 h | Incluye preparar fixtures (cliente desactivado, empleado desactivado, schedule_blocks, absences) y curl paralelo para D.2. |
| E | 2 h | 40 transiciones; reusar appointment recreando estado via SQL `UPDATE`. |
| F | 2 h | Preparar día con citas + absences + blocks + booths. |
| G | 2 h | Crear/borrar/reactivar + recomprobaciones de BD. |
| H | 1,5 h | Algunos casos requieren ajustar reloj o usar fechas concretas. |
| I | 1,5 h | Seed de N elementos previo (script SQL para tener 100 clientes). |
| J | 1 h | Spot-check de respuestas. |
| K | 1,5 h | Bloquear nominatim y SMTP con `hosts` o iptables. |
| L | 1,5 h | Activar SQL logging, contar queries, restablecer config. |
| M | 1 h | OPTIONS preflight + verificar status codes y headers. |
| N | 5–6 h | N.1 (SQLi empírica) requiere `cnt_*` antes/después de cada caso; N.4 requiere construir JWTs manipulados; N.10 requiere ciclo de DEBUG + revisión de logs. |
| O | 2–3 h | Mayoría son spot-checks rápidos; O.1 (CHECK SQL) requiere conexión a Workbench. |
| **TOTAL** | **~40–50 h** | Realista: 5–6 jornadas completas o 10–12 medias jornadas. |

### Z.3 Severidades agregadas

| Severidad | Estimación de pruebas | Significado al fallar |
|---|---|---|
| CRIT | ~105 | Bug de seguridad explotable o corrupción de datos. Bloquea release del frontend. |
| ALTA | ~230 | Fallo funcional con impacto en datos o seguridad. Hay que arreglar antes de frontend. |
| MEDIA | ~155 | Fallo funcional acotado. Aceptable arreglar en paralelo. |
| BAJA | ~50 | Cosmético. No bloquea. |
| INFO | ~80 | Documentación de comportamiento. Sin pass/fail. |

### Z.4 Pruebas marcadas N/A o que requieren preparación especial

- **D.1.039** (PENDING ausente en BD): N/A en flujo normal. Sólo si se manipula `appointment_statuses` directamente; no es una prueba que se vaya a fallar en seed estándar.
- **N.2.006**: pre-ejecutado durante diseño del plan (grep en `src/main/java` ya devuelve 0 archivos para los 5 patrones). Documentado en el plan.
- **A.6.001-005**: requieren entorno fresco o esperar el refill. Sugerencia: ejecutar al final de cada sesión para no contaminar otros tests.
- **L.001-006**: requieren activar/desactivar `org.hibernate.SQL=DEBUG` temporalmente.
- **N.10.001-009**: requieren `logging.level.root=DEBUG` y restablecer al final.
- **K.006/008/009/010**: requieren modificar `/etc/hosts` o bloquear puertos para simular caída de Nominatim/SMTP.

### Z.5 Recomendación de orden de ejecución

1. **Bloques L + N.2** primero (análisis estático): rápidos, no consumen estado, informan el diseño del resto.
2. **A + B**: necesarios para tener todos los tipos de tokens preparados (admin, employee, identity, manipulados).
3. **C**: ejecución masiva en paralelo a A/B (no comparten estado).
4. **D + E + F + G**: dependen de fixtures cuidadosamente creadas; agrupar en una jornada.
5. **H + I**: rápidos, pueden ir al final del día.
6. **J**: spot-check cruzado mientras se ejecutan los demás.
7. **K**: requiere romper el entorno (nominatim/SMTP) → al final.
8. **M**: rápido, sólo verifica headers.
9. **N.1 + N.3..N.10**: bloque dedicado de seguridad al final, antes del informe.
10. **O**: comprobaciones sueltas, pueden ir en cualquier momento.

---

## Z.6 Salidas del plan (qué entrega el audit completo)

Tras ejecutar este plan se generan:

1. **`docs/audit/02_RESULTADOS_PRUEBAS.md`**: tabla con `ID | Resultado | Observaciones` para los 622 casos.
2. **`docs/audit/03_HALLAZGOS.md`**: bugs encontrados, agrupados por severidad, con repro mínimo.
3. **`docs/audit/04_INFORME_FINAL.md`**: resumen ejecutivo "listo para frontend / no listo y por qué", con la decisión final.

> El plan en sí (este archivo) es la **especificación**. La ejecución y los hallazgos van en los archivos 02–04.

---

## Z.7 Validación del conteo

Conteo real obtenido contando filas de tabla con ID `<Bloque>.<…>` (regex `^\| [A-O]\.[0-9]+`):

```
A: 78  B: 47  C: 168  D: 49  E: 40  F: 27  G: 20  H: 12
I: 20  J: 15  K: 10   L: 6   M: 12  N: 69  O: 49
TOTAL: 622
```

Si futuras revisiones añaden o quitan filas, re-ejecutar el conteo y actualizar Z.1.
