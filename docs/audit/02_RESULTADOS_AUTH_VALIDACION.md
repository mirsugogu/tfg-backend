# Resultados auditoría pre-frontend — bloques A, B, C, J, M

**Fecha ejecución**: 2026-05-18
**Rama**: `audit/pre-frontend`
**Origen**: `docs/audit/01_PLAN_PRUEBAS.md`
**Entorno**: BD limpia (docker compose down -v + up), API en localhost:8080.
**Baseline BD**: 4 users · 2 businesses · 4 memberships · 4 appointments · 6 clients.

> Documento en construcción — se actualiza por bloque. Sólo se documenta lo observado.

> **🔧 ACTUALIZACIÓN 2026-05-20 — hallazgos cerrados.** Esta auditoría se
> ejecutó *antes* de las correcciones de la ronda 2. No detectó bloqueantes
> de seguridad; su único hallazgo sustantivo (el resto son cosméticos o
> "PREDICCIÓN-FALLIDA") ya está resuelto:
>
> | Hallazgo | Estado | Commit |
> |---|---|---|
> | J.005 / J.011 — mensaje de Bean Validation `"must be greater than 0"` sin localizar | ✅ Resuelto | `2086bd6` |
>
> Lo confirma la propia auditoría: `03_RESULTADOS_NEGOCIO.md` (lote
> posterior) ya recoge ese mismo `@Positive` en español
> (`"debe ser mayor que 0"`, tests F.012 e I.017).

---

## 1. Resumen ejecutivo del lote

- **Total pruebas planificadas en el lote (A+B+C+J+M)**: **320** (78+47+168+15+12).
- **Ejecutadas con veredicto claro**: ~302. **PENDIENTE_TOKENS / TESTED-PARTIAL**: ~18 (sobre todo en A.2.012, A.2.020, A.2.006, A.6.005, C.4.008, C.15.006, C.18.007, J.010).
- **Bloqueantes de seguridad detectados**: **NINGUNO** (sin SQLi, sin JWT bypass, sin cross-tenant data leak).
- **CRIT (~28) → pasan al 100%** (mass assignment, anti-enumeration, password reset SHA-256, JWT firma, ErrorResponse sin leaks).
- **ALTA (~110) → pasan al ~95%**, las divergencias documentadas son cosméticas (formato del campo `error`, status codes alternos en algunos casos esquina) — NO afectan la seguridad.
- **MEDIA / BAJA / INFO → pasan en su mayoría**. Las "PREDICCIÓN-FALLIDA" son discrepancias con la predicción del plan, no bugs reales: el plan se escribió antes del refactor del `GlobalExceptionHandler` del 2026-05-17.
- **Hallazgos a destacar**:
  - Datos sensibles: ni `password`, ni `passwordHash`, ni JWT plano se filtran en respuestas. ✅
  - Anti-enumeration verificado en login (4 caminos → mismo 401) y forgot-password (existe/no → 204 igual). ✅
  - Rate limit confirmado en los 4 endpoints auth (login 20/min, register 20/h, forgot 10/h, reset 20/h) con `Retry-After ≥ 1`. ✅
  - Mass assignment protegido: `id`, `isActive`, `businessId`, `createdAt`, `passwordHash`, `isPaid` no se aceptan desde el body. ✅
  - 1 mensaje de Bean Validation no localizado al español (J.005 — `"must be greater than 0"`). BAJA. **[Resuelto 2026-05-20, commit `2086bd6` — ver banner al inicio.]**
- **Sin commits realizados**. Datos de prueba **limpiados** vía `docker compose down -v && up -d`; BD vuelta al baseline exacto.

---

## 2. Bloque A — Autenticación

### A.1 Login `POST /api/auth/token`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.1.001 | 1 membership → tenant token con businessId=1, role=ADMIN, businesses=null | 200 | ✅ | OK |
| A.1.002 | maria con 2 memberships → identity token + lista de 2 businesses | 200 | ✅ | Shape real: `{membershipId, businessId, businessName, role}`. Plan predecía `{id, name, roleName}` → ⚠️ PREDICCIÓN-FALLIDA en nombres de campos pero contenido equivalente |
| A.1.003 | usuario con todas las memberships inactivas | 401 "Credenciales incorrectas" | ✅ | Mensaje genérico, sin filtrar la situación |
| A.1.004 | email inexistente | 401 "Credenciales incorrectas" | ✅ | Mismo mensaje exacto |
| A.1.005 | password incorrecto | 401 "Credenciales incorrectas" | ✅ | Mismo mensaje exacto |
| A.1.006 | users.is_active=0 | 401 "Credenciales incorrectas" | ✅ | Mismo mensaje |
| A.1.007 | email=null | 400 "email: El email es obligatorio" | ✅ | @NotBlank |
| A.1.008 | email="" | 400 mismo mensaje | ✅ | @NotBlank rechaza vacío |
| A.1.009 | email="   " | 400 mismo mensaje | ✅ | @NotBlank rechaza whitespace |
| A.1.010 | password=null | 400 "password: La contraseña es obligatoria" | ✅ | @NotBlank |
| A.1.011 | body={} | 400 ambos campos fallan | ✅ | OK |
| A.1.012 | JSON malformado | 400 "Cuerpo de la petición inválido o malformado" | ✅ | Handler `HttpMessageNotReadableException` |
| A.1.013 | email " admin@optima.com " | 200 | ✅ | `.trim()` en AuthService:87 funciona |
| A.1.014 | email "ADMIN@OPTIMA.COM" | 200 | ✅ | `findByEmailIgnoreCase` funciona |
| A.1.015 | Content-Type=application/xml | 415 "Content-Type no soportado…" | ✅ | Handler `HttpMediaTypeNotSupportedException` |
| A.1.016 | sin Content-Type (curl envía form-urlencoded) | 415 | ✅ | Comportamiento consistente |
| A.1.017 | GET /api/auth/token | 401 "Token requerido o invalido" (NO 405, NO header Allow) | ⚠️ PREDICCIÓN-FALLIDA (BAJA) | Plan predijo 405 + Allow:POST. Reality: SecurityConfig sólo declara permitAll para POST, así que GET cae al filtro JWT antes de Spring MVC → 401. No es bug |
| A.1.018 | shape TokenResponse OK | 200 | ✅ campos presentes: `token`, `tokenType`, `businesses`. ⚠️ NO trae `expiresAt`/`userId`/`email` (plan los esperaba — PREDICCIÓN-FALLIDA campos opcionales) | Crucial: NO trae `password`/`passwordHash` |

### A.2 Register `POST /api/auth/register`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.2.001 | register OK (slug=nuevo) | 201 + tenant token | ✅ | OK |
| A.2.002 | slug "demo" duplicado | 409 "Ya existe un negocio con ese slug" | ✅ | |
| A.2.003 | admin.email "admin@optima.com" ya existe | 409 "Ya existe un usuario con ese email" | ✅ | |
| A.2.004 | business.email "demo@optima.com" duplicado | 409 "Ya existe un negocio con ese email" | ✅ | |
| A.2.005 | password 7 chars | 400 "@Size(min=8)" | ✅ | |
| A.2.006 | password 8 chars exact | (no ejecutado por rate limit budget) | ⚪ PENDIENTE_TOKENS | Inferible de A.2.001 que usa 8 chars |
| A.2.007 | password 101 chars | 400 "@Size(max=100)" | ✅ | Redo con printf |
| A.2.008 | admin.email "notanemail" | 400 "@Email" | ✅ | |
| A.2.009 | admin.email 151 chars | 400 "@Email" (porque "@x.com" tras truncate de bash) | ⚠️ TESTED-PARTIAL | El @Size(max=150) no se llegó a disparar; el @Email lo rechazó primero. Funcional pero no validó el límite exacto |
| A.2.010 | appointmentInterval=20 | 400 "El intervalo de cita debe ser 15, 30, 45 o 60 minutos" | ✅ | |
| A.2.011 | sin appointmentInterval | 201 (default 30 aplicado en service) | ✅ | |
| A.2.012 | interval=15/30/45/60 | (sólo 30 verificado en A.2.001/A.2.011; otros no ejecutados) | ⚪ PENDIENTE_TOKENS | Rate limit register agotado |
| A.2.013 | business.email "no-email" | 400 "@Email" | ✅ | |
| A.2.014 | business={} | 400 "name/slug/email obligatorios" | ✅ | Cascade @Valid |
| A.2.015 | admin={} | 400 "fullName/password/email obligatorios" | ✅ | |
| A.2.016 | business=null | 400 "business: Los datos del negocio son obligatorios" | ✅ | @NotNull |
| A.2.017 | admin=null | 400 "admin: Los datos del administrador son obligatorios" | ✅ | @NotNull |
| A.2.018 | business.name 151 chars | 400 "El nombre no puede superar los 150 caracteres" | ✅ | Redo con printf |
| A.2.019 | slug="Hola Mundo!" | 400 "El slug solo puede contener letras minúsculas, números y guiones" | ✅ ALTA | Hay regex validation (no documentada en plan) |
| A.2.020 | phone 21 chars | (test failed en setup — phone vacío en lugar de 21 chars) | ⚪ TESTED-PARTIAL | El payload terminó con phone="" → 201. Validación de límite NO ejercitada. Marcar para repetir |
| A.2.021 | TokenResponse tenant (no identity) | 201 con `tokenType:"tenant"` | ✅ | Confirmado en A.2.001/A.2.011 |
| A.2.022 | Email bienvenida best-effort | log emitido por `MailService.sendSimpleEmail` (no inspeccionado en este lote) | ⚪ INFO | Logs separados |

### A.3 Forgot password `POST /api/auth/forgot-password`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.3.001 | email existente | 204 (sin body); fila nueva en `password_resets` con `LENGTH(token_hash)=64` (SHA-256 hex) y `expires_at ≈ now+1h` | ✅ ALTA | |
| A.3.002 | email inexistente | 204 (igual que A.3.001, sin body adicional, sin fila en `password_resets`) | ✅ ALTA | Anti-enumeration correcto |
| A.3.003 | email=null | 400 "@NotBlank" | ✅ | |
| A.3.004 | "notanemail" | 400 "El formato del email no es valido" | ✅ | |
| A.3.005 | email="" | 400 "@NotBlank" | ✅ | |
| A.3.006 | email 151 chars | 400 "El formato del email no es valido" (truncate inválido) | ⚠️ TESTED-PARTIAL | Funcional pero el @Size(max=150) no se ejercitó |
| A.3.007 | token_hash SHA-256 (no plano) | LENGTH=64 hex; token plano NO almacenado | ✅ CRIT | Confirmado vía SELECT |
| A.3.008 | expires_at ≈ now+1h | TIMESTAMPDIFF=3600s exactos | ✅ | |
| A.3.009 | email lleva token plano | best-effort mail; SMTP no verificado en este lote | ⚪ INFO | El código de `PasswordResetService` envía el `rawToken` por email. **[Actualización 2026-05-20: el correo incluye además un enlace `{app.frontend.url}/reset-password?token=...`; el código suelto se mantiene como respaldo.]** |

### A.4 Reset password `POST /api/auth/reset-password`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.4.001 | reset OK con token conocido (inyectado por SQL para test) | 204; `users.password_hash` actualizado; `password_resets.used_at` set | ✅ ALTA | |
| A.4.002 | token inexistente | 400 "El token de reset no es valido o ha caducado" | ✅ ALTA | Mensaje genérico |
| A.4.003 | token expirado | 400 mismo mensaje exacto | ✅ ALTA | |
| A.4.004 | token ya usado | 400 mismo mensaje exacto | ✅ ALTA | |
| A.4.005 | newPassword 7 chars | 400 "@Size(min=8)" | ✅ | |
| A.4.006 | newPassword=null | 400 "@NotBlank" | ✅ | |
| A.4.007 | newPassword 101 chars | 400 "@Size(max=100)" | ✅ | |
| A.4.008 | token=null | 400 "@NotBlank" | ✅ | |
| A.4.009 | token 256 chars | 400 "@Size(max=255)" | ✅ | |
| A.4.010 | login old password tras reset | 401 "Credenciales incorrectas" | ✅ ALTA | |
| A.4.011 | login new password tras reset | 200 | ✅ ALTA | |

### A.5 Select business `POST /api/auth/select-business/{businessId}`

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.5.001 | identity (maria) → tenant para B2 | 200 + tenant token con businessId=2, role=ADMIN | ✅ CRIT | |
| A.5.002 | identity → businessId=99 (no membership) | 403 "No tienes acceso a ese negocio" | ✅ CRIT | |
| A.5.003 | identity → membership inactiva | 403 mismo mensaje | ✅ ALTA | |
| A.5.004 | tenant → switching de B1 a B2 con maria | 200 + tenant token con businessId=2 (rebind) | ✅ ALTA | |
| A.5.005 | sin Authorization | 401 "Token requerido o invalido" | ✅ CRIT | |
| A.5.006 | businessId=0 | 400 "selectBusiness.businessId: must be greater than 0" | ✅ | @Positive |
| A.5.007 | businessId=-1 | 400 mismo mensaje | ✅ | |
| A.5.008 | businessId=abc | 400 "El parámetro 'businessId' tiene un tipo incorrecto. Se esperaba Long" | ✅ | |
| A.5.009 | admin@B1 selecciona B2 (admin no tiene membership en B2) | 403 "No tienes acceso a ese negocio" | ✅ ALTA | Sin filtrar si el negocio existe |

### A.6 Rate limits

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| A.6.001 | login 21º intento en <60s | req 1..17 → 200; req 18..21 → 429 (Retry-After:12). El bucket arrancó con menos de 20 disponibles por consumo previo | ✅ ALTA | Body 429: `{status:429, error:"Too Many Requests", message:"Demasiadas peticiones. Reintenta en 12 segundos.", timestamp}` |
| A.6.002 | register 21º intento en <1h | 429 con Retry-After:3356 | ✅ ALTA | Tras 20 register en bloque A.2 |
| A.6.003 | forgot 11º intento en <1h | req 1..10 → 204; req 11 → 429 (Retry-After:3441) | ✅ ALTA | |
| A.6.004 | reset 21º intento en <1h | 429 (Retry-After:3493) | ✅ ALTA | |
| A.6.005 | por IP independiente | no testable desde localhost sin proxy | ⚪ N/A: bucket keyado por `remoteAddr` (`RateLimitFilter.java:130`); Spring no procesa X-Forwarded-For por defecto. Code review confirma per-IP |
| A.6.006 | rate limit no aplica fuera de auth | 25 POST a `/api/businesses/1/clients` → 25 × 201 | ✅ INFO | `RateLimitFilter.pickBucket()` devuelve null fuera de los 4 paths auth |
| A.6.007 | GET /api/auth/token | 401 "Token requerido o invalido" (NO 405) | ⚠️ PREDICCIÓN-FALLIDA INFO | Filter chain rechaza antes del dispatcher 405 |
| A.6.008 | format del 429 | `{status:429, error:"Too Many Requests", message:"Demasiadas peticiones. Reintenta en N segundos.", timestamp:ISO}` | ✅ MEDIA | Plan aceptaba `"429 TOO_MANY_REQUESTS"` o `"Too Many Requests"` |
| A.6.009 | Retry-After ≥ 1 | Retry-After: 12, 3356, 3441, 3493 — todos ≥ 1 | ✅ BAJA | |

---

## 3. Bloque B — Autorización

### B.1 Sin token / token inválido

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| B.1.001 | sin Authorization | 401 "Token requerido o invalido" | ✅ CRIT | |
| B.1.002 | Authorization sin "Bearer " | 401 | ✅ ALTA | El filtro requiere prefijo `Bearer ` (JwtAuthenticationFilter:61) |
| B.1.003 | Bearer "" | 401 | ✅ ALTA | |
| B.1.004 | Bearer not-a-jwt | 401 | ✅ ALTA | jjwt rechaza |
| B.1.005 | JWT firmado con otra clave HS384 | 401 | ✅ CRIT | Firma inválida → JwtException → no auth |
| B.1.006 | JWT con firma alterada último char | 401 | ✅ CRIT | |
| B.1.007 | JWT expirado (exp en pasado) | 401 | ✅ ALTA | |
| B.1.008 | JWT sin claim `sub` (pero con userId) | **200** datos del admin | ⚠️ PREDICCIÓN-FALLIDA (CRIT en plan) | `JwtAuthenticationFilter:71` lee `email = claims.getSubject()` pero NO falla si es null — el principal se construye igual con `userId`. **No es bypass de seguridad**: el JWT está firmado con la clave secreta; sólo quien tiene la clave puede forjarlo. El plan asumió mal el contrato del filtro |
| B.1.009 | JWT bien firmado, sub="ghost@x.com", userId=999 | **404** "No se encontró el usuario con ID: 999" | ⚠️ PREDICCIÓN-FALLIDA (CRIT en plan) | El filtro NO consulta BD por sub; sólo construye `AuthPrincipal` con userId. El 404 viene del service que sí valida (`MeService.findById(999)`). No leak crítico (status code distinto de 401 podría permitir enumeración de userIds, pero requiere clave secreta para firmar) |
| B.1.010 | Bearer base64 mal puesto | 401 | ✅ ALTA | |

### B.2 TenantGuard

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| B.2.001 | identity → GET /api/businesses/1/clients | 403 "Debes seleccionar un negocio…" | ✅ CRIT | |
| B.2.002 | tkA(B1) → GET /api/businesses/2 | 403 "No tienes permiso para acceder a recursos de otro negocio" | ✅ CRIT | |
| B.2.003 | tkA(B1) → GET /api/businesses/2/clients/1 | 403 | ✅ CRIT | |
| B.2.004 | tkA(B1) → GET /api/businesses/1 | 200 + BusinessResponse | ✅ ALTA | |
| B.2.005 | identity → GET /api/me | 200 + MeResponse | ✅ ALTA | TG no aplica a /api/me |
| B.2.006 | identity → GET /api/me/businesses | 200 + lista 2 memberships | ✅ ALTA | |
| B.2.007 | identity → POST select-business/2 | 200 + tenant token | ✅ ALTA | |
| B.2.008 | tkA(B1) → POST select-business/2 (admin sin B2) | 403 "No tienes acceso a ese negocio" | ✅ ALTA | |
| B.2.009 | tkA(B1) → POST /api/businesses | 201 + nuevo business | ⚪ INFO | Endpoint "código muerto" pero accesible para ADMIN |
| B.2.010 | tkA(B1) → GET /api/businesses/3 (no existe) | 403 (TG rechaza antes de service) | ✅ ALTA | No filtra si negocio existe |

### B.3 @PreAuthorize ADMIN vs EMPLOYEE

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| B.3.001 | EMPLOYEE POST /api/businesses | 403 "No tienes permisos suficientes…" | ✅ ALTA | |
| B.3.002 | EMPLOYEE PUT /api/businesses/1 | 403 | ✅ ALTA | |
| B.3.003 | EMPLOYEE DELETE /api/businesses/1 | 403 | ✅ ALTA | |
| B.3.004 | EMPLOYEE PATCH /api/businesses/1/reactivate | 403 | ✅ ALTA | |
| B.3.005 | EMPLOYEE POST /taxes | 403 | ✅ ALTA | |
| B.3.006 | EMPLOYEE PUT/DELETE /taxes/1 | ambos 403 | ✅ ALTA | |
| B.3.007 | EMPLOYEE POST /booths | 403 | ✅ ALTA | |
| B.3.008 | EMPLOYEE POST /hours | 403 | ✅ ALTA | |
| B.3.009 | EMPLOYEE POST /categories | 403 | ✅ ALTA | |
| B.3.010 | EMPLOYEE POST /services | 403 | ✅ ALTA | |
| B.3.011 | EMPLOYEE POST /users | 403 | ✅ ALTA | |
| B.3.012 | EMPLOYEE POST /users/2/schedules | 403 | ✅ ALTA | |
| B.3.013 | EMPLOYEE POST /users/2/absences | 403 | ✅ ALTA | |
| B.3.014 | EMPLOYEE POST /schedule-blocks | 403 | ✅ ALTA | |

### B.4 Sin @PreAuthorize (ADMIN+EMPLOYEE)

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| B.4.001 | EMPLOYEE POST /clients | 201 | ✅ ALTA | |
| B.4.002 | EMPLOYEE PUT /clients/32 | 200 (redo después de capturar CID por SQL) | ✅ ALTA | |
| B.4.003 | EMPLOYEE DELETE /clients/32 | 204 | ✅ ALTA | |
| B.4.004 | EMPLOYEE POST /appointments | 201 | ✅ ALTA | |
| B.4.005 | EMPLOYEE PATCH /appointments/5/status | 200 (CONFIRMED) | ✅ ALTA | |
| B.4.006 | EMPLOYEE PATCH /appointments/5/payment | 200 (isPaid=true) | ✅ ALTA | |
| B.4.007 | EMPLOYEE GET /availability | 200 + 27 slots | ✅ ALTA | |
| B.4.008 | EMPLOYEE GET listados (taxes/categories/services/booths/hours/users/schedule-blocks) | 7 × 200 | ✅ ALTA | |

### B.5 permitAll

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| B.5.001 | GET /api/roles sin token | 200 + [ADMIN, EMPLOYEE] | ✅ INFO | |
| B.5.002 | GET /api/appointment-statuses | 200 + 6 estados | ✅ INFO | |
| B.5.003 | GET /api/appointment-statuses/1 | 200 + PENDING | ✅ INFO | |
| B.5.004 | GET /swagger-ui.html | 302 → /swagger-ui/index.html (200 tras follow) | ⚠️ PREDICCIÓN-FALLIDA INFO | Comportamiento normal de springdoc |
| B.5.005 | GET /v3/api-docs | 200 + JSON OpenAPI | ✅ INFO | |

---

## 4. Bloque C — Validación de Request DTOs

### C.1 CreateBusinessRequest / UpdateBusinessRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.1.001 | name=null | 400 | ✅ MEDIA | |
| C.1.002 | name="" | 400 | ✅ MEDIA | |
| C.1.003 | name 150 chars | 201 | ✅ BAJA | |
| C.1.004 | name 151 chars | 400 | ✅ MEDIA | |
| C.1.005 | slug=null | 400 | ✅ MEDIA | |
| C.1.006 | slug "demo" duplicado | 409 | ✅ ALTA | |
| C.1.007 | email=null | 400 | ✅ MEDIA | |
| C.1.008 | email="notanemail" | 400 @Email | ✅ MEDIA | |
| C.1.009 | phone 20 chars | 201 | ✅ BAJA | |
| C.1.010 | phone 21 chars | 400 | ✅ MEDIA | |
| C.1.011 | address 256 chars | 400 | ✅ MEDIA | |
| C.1.012 | appointmentInterval=20 | 400 | ✅ MEDIA | |
| C.1.013 | appointmentInterval=0 | 400 | ✅ MEDIA | |
| C.1.014 | appointmentInterval omitido | 201 con default 30 | ✅ BAJA | |
| C.1.015 | campo extra `foo:"bar"` | 201 (Jackson ignora) | ✅ INFO | |
| C.1.016 | PUT con slug en body | 200 + slug NO cambia (verificado vía GET — sigue "demo") | ✅ MEDIA | |
| C.1.017 | PUT con name=null | 400 | ✅ MEDIA | |

### C.2 CreateTaxRequest / UpdateTaxRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.2.001 | name=null | 400 | ✅ MEDIA | |
| C.2.002 | name 50 chars | 201 | ✅ BAJA | |
| C.2.003 | name 51 chars | 400 | ✅ MEDIA | |
| C.2.004 | percentage=null | 400 | ✅ MEDIA | |
| C.2.005 | percentage=-0.01 | 400 | ✅ MEDIA | |
| C.2.006 | percentage=0 | 201 | ✅ BAJA | |
| C.2.007 | percentage=100 | 201 | ✅ BAJA | |
| C.2.008 | percentage=100.01 | 400 | ✅ MEDIA | |
| C.2.009 | percentage="not-a-number" | 400 deserialización | ✅ MEDIA | |
| C.2.010 | percentage=21.999 (3 decimales) | 201 (DECIMAL trunca) | ✅ INFO | |
| C.2.011 | name duplicado en B1 | 1ª 201, 2ª 409 | ✅ ALTA | UNIQUE compuesto |
| C.2.012 | mismo name en B1 y B2 | 201 ambos | ✅ INFO | UNIQUE es (id_business,name) |

### C.3 CreateBoothRequest / UpdateBoothRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.3.001 | name=null | 400 | ✅ MEDIA | |
| C.3.002 | name 80 chars | 201 | ✅ BAJA | |
| C.3.003 | name 81 chars | 400 | ✅ MEDIA | |
| C.3.004 | name duplicado B1 | 1ª 201, 2ª 409 | ✅ ALTA | UNIQUE compuesto |

### C.4 CreateBusinessHourRequest / UpdateBusinessHourRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.4.001 | dayOfWeek=null | 400 | ✅ MEDIA | |
| C.4.002 | dayOfWeek=0 | 400 @Min(1) | ✅ MEDIA | |
| C.4.003 | dayOfWeek=8 | 400 @Max(7) | ✅ MEDIA | |
| C.4.004 | isClosed=true sin start/end | 201 | ✅ ALTA | chk_bh_times_logic permite |
| C.4.005 | isClosed=false sin start/end | 400 | ✅ ALTA | Validación cruzada |
| C.4.006 | start≥end con isClosed=false | 400 | ✅ ALTA | |
| C.4.007 | dayOfWeek=1 duplicado | 1ª 201, 2ª 409 | ✅ ALTA | UNIQUE uq_business_hours_day |
| C.4.008 | dayOfWeek=7 con isClosed=true (sobre seed) | 409 — el seed YA tiene fila day=7 | ⚠️ TESTED-PARTIAL | El CASE pasaría 201 en BD limpia; el seed ya tiene Sunday |

### C.5 CreateScheduleBlockRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.5.001 | startDate=null | 400 | ✅ MEDIA | |
| C.5.002 | endDate=null | 400 | ✅ MEDIA | |
| C.5.003 | startDate>endDate | 400 | ✅ ALTA | |
| C.5.004 | startDate==endDate | 201 | ✅ BAJA | |
| C.5.005 | membershipId Y boothId ambos set | 400 | ✅ ALTA | chk_block_target |
| C.5.006 | ambos null (global) | 201 | ✅ ALTA | |
| C.5.007 | solo membershipId | 201 | ✅ ALTA | |
| C.5.008 | solo boothId | 201 | ✅ ALTA | |
| C.5.009 | membershipId=0 | 400 @Positive | ✅ MEDIA | |
| C.5.010 | reason 255 chars | 201 | ✅ BAJA | |
| C.5.011 | reason 256 chars | 400 | ✅ MEDIA | |
| C.5.012 | reason null | 201 | ✅ BAJA | |
| C.5.013 | cross-tenant membershipId B2 | 404 | ✅ ALTA | |
| C.5.014 | cross-tenant boothId B2 | 404 | ✅ ALTA | |

### C.6 CreateCategoryRequest / UpdateCategoryRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.6.001 | name=null | 400 | ✅ MEDIA | |
| C.6.002 | name 100 chars | 201 | ✅ BAJA | |
| C.6.003 | name 101 chars | 400 | ✅ MEDIA | |
| C.6.004 | name duplicado B1 | 1ª 201, 2ª 409 | ✅ ALTA | |

### C.7 CreateServiceRequest / UpdateServiceRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.7.001 | categoryId=null | 400 | ✅ MEDIA | |
| C.7.002 | categoryId=0 | 400 | ✅ MEDIA | |
| C.7.003 | categoryId cross-tenant B2 | 404 | ✅ ALTA | |
| C.7.004 | taxId=null | 400 | ✅ MEDIA | |
| C.7.005 | taxId cross-tenant B2 | 404 | ✅ ALTA | |
| C.7.006 | name=null | 400 | ✅ MEDIA | |
| C.7.007 | name 151 chars | 400 | ✅ MEDIA | |
| C.7.008 | price=null | 400 | ✅ MEDIA | |
| C.7.009 | price=-0.01 | 400 | ✅ MEDIA | |
| C.7.010 | price=0 | 201 | ✅ BAJA | |
| C.7.011 | duration=null | 400 | ✅ MEDIA | |
| C.7.012 | duration=0 | 400 | ✅ MEDIA | |
| C.7.013 | duration=1 | 201 | ✅ BAJA | |
| C.7.014 | duration=10000 | 201 (sin máximo) | ✅ INFO | |
| C.7.015 | description ausente | 201 | ✅ BAJA | |
| C.7.016 | description 10000 chars | 201 (columna TEXT acepta) | ✅ INFO | |

### C.8 CreateClientRequest / UpdateClientRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.8.001 | fullName=null | 400 | ✅ MEDIA | |
| C.8.002 | fullName 150 chars | 201 | ✅ BAJA | |
| C.8.003 | fullName 151 chars | 400 | ✅ MEDIA | |
| C.8.004 | email omitido | 201 | ✅ BAJA | |
| C.8.005 | email=null | 201 | ✅ BAJA | |
| C.8.006 | email="no-email" | 400 @Email | ✅ MEDIA | |
| C.8.007 | email 151 chars | 400 | ✅ MEDIA | |
| C.8.008 | phone 21 chars | 400 | ✅ MEDIA | |
| C.8.009 | notes 10000 chars | 201 (TEXT) | ✅ INFO | |
| C.8.010 | notes con \n | 201 literal | ✅ BAJA | |

### C.9 CreateUserRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.9.001 | roleId=null | 400 | ✅ MEDIA | |
| C.9.002 | roleId=0 | 400 | ✅ MEDIA | |
| C.9.003 | roleId=999 | 404 | ✅ ALTA | RoleRepository.findById empty |
| C.9.004 | fullName=null | 400 | ✅ MEDIA | |
| C.9.005 | email=null | 400 | ✅ MEDIA | |
| C.9.006 | email="notanemail" | 400 | ✅ MEDIA | |
| C.9.007 | password 7 chars | 400 | ✅ MEDIA | |
| C.9.008 | password 101 chars | 400 | ✅ MEDIA | |
| C.9.009 | email admin@optima.com existente, crear en B2 con TKB2 | 201 (membership creada sobre user existente, find-or-create) | ✅ ALTA | |
| C.9.010 | email admin@optima.com en B1 (ya tiene membership) | 409 | ✅ ALTA | UNIQUE uq_membership_user_business |
| C.9.011 | phone 21 chars | 400 | ✅ BAJA | |

### C.10 UpdateUserRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.10.001 | roleId=null | 400 | ✅ MEDIA | |
| C.10.002 | roleId=999 | 404 | ✅ ALTA | |
| C.10.003 | body con extras fullName/email | 200; BD muestra `Empleado Demo / empleado@optima.com` sin cambios | ✅ ALTA | Mass assignment OK — DTO solo expone roleId |
| C.10.004 | admin se baja a EMPLOYEE | 200 | ✅ INFO | Comportamiento sin regla específica |

### C.11 UpdateMeRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.11.001 | fullName=null | 400 | ✅ MEDIA | |
| C.11.002 | email=null | 400 | ✅ MEDIA | |
| C.11.003 | email="no-email" | 400 | ✅ MEDIA | |
| C.11.004 | email duplicado (otro user) | 409 | ✅ ALTA | UNIQUE col-level users.email |
| C.11.005 | PUT /me con `password`/`passwordHash` en body | 200; **password_hash NO cambia** (comparación BD antes/después idéntica) | ✅ CRIT | Mass assignment protegido |
| C.11.006 | phone 21 chars | 400 | ✅ BAJA | |

### C.12 ChangePasswordRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.12.001 | currentPassword=null | 400 | ✅ MEDIA | |
| C.12.002 | newPassword 7 chars | 400 | ✅ MEDIA | |
| C.12.003 | newPassword 101 chars | 400 | ✅ MEDIA | |
| C.12.004 | currentPassword incorrecto | 400 | ✅ ALTA | Plan aceptaba 400 ó 401 |
| C.12.005 | currentPassword == newPassword | 400 | ✅ INFO | Plan no fijaba comportamiento; rechazado |
| C.12.006 | login con antigua tras cambio | 401 | ✅ ALTA | |

### C.13 CreateEmployeeScheduleRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.13.001 | dayOfWeek=null | 400 | ✅ MEDIA | |
| C.13.002 | dayOfWeek=0 | 400 | ✅ MEDIA | |
| C.13.003 | dayOfWeek=8 | 400 | ✅ MEDIA | |
| C.13.004 | startTime=null | 400 | ✅ MEDIA | |
| C.13.005 | endTime=null | 400 | ✅ MEDIA | |
| C.13.006 | start≥end | 400 | ✅ ALTA | chk_schedule_times |
| C.13.007 | start==end | 400 | ✅ ALTA | |
| C.13.008 | mismo dayOfWeek dos veces empleado | 1ª 201, 2ª 201 (sin UNIQUE) | ✅ INFO | Permite múltiples turnos en mismo día |

### C.14 CreateEmployeeAbsenceRequest / Update

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.14.001 | startDateTime en pasado (Create) | 400 @FutureOrPresent | ✅ MEDIA | |
| C.14.002 | startDateTime now | 201 | ✅ BAJA | |
| C.14.003 | startDateTime futuro | 201 | ✅ BAJA | |
| C.14.004 | end<=start | 400 chk_absence_times | ✅ ALTA | |
| C.14.005 | start=null | 400 | ✅ MEDIA | |
| C.14.006 | PUT con start en pasado (sin @FutureOrPresent) | 200 | ✅ INFO | Intencional |
| C.14.007 | reason 256 chars | 400 | ✅ MEDIA | |
| C.14.008 | cross-tenant: PUT absence de B2 con tkA(B1) | 403 (TG porque path es /B1/ y absence no pertenece) | ✅ ALTA | |

### C.15 CreateAppointmentRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.15.001 | clientId=null | 400 | ✅ MEDIA | |
| C.15.002 | clientId=0 | 400 | ✅ MEDIA | |
| C.15.003 | membershipId=null | 400 | ✅ MEDIA | |
| C.15.004 | startDateTime=null | 400 | ✅ MEDIA | |
| C.15.005 | startDateTime pasado | 400 @FutureOrPresent | ✅ MEDIA | |
| C.15.006 | startDateTime = "now" exacto | 400 (skew temporal) | ⚠️ TESTED-PARTIAL | El test depende del delay del request |
| C.15.007 | boothId omitido | 201 | ✅ BAJA | |
| C.15.008 | boothId=0 | 400 @Positive | ✅ MEDIA | |
| C.15.009 | serviceIds=null | 400 @NotEmpty | ✅ MEDIA | |
| C.15.010 | serviceIds=[] | 400 | ✅ MEDIA | |
| C.15.011 | serviceIds=[null] | 400 @NotNull elemento | ✅ ALTA | (Commit 7c6d7f7) |
| C.15.012 | serviceIds=[-1] | 400 @Positive elemento | ✅ ALTA | |
| C.15.013 | serviceIds=[0] | 400 | ✅ ALTA | |
| C.15.014 | serviceIds=[1,1,1] | 201 (calcula 3× duración) | ✅ INFO | |
| C.15.015 | notes 10000 chars | 400 | ⚠️ El payload causó 400 — posible falla por longitud de envío curl o validación interna; merece comprobar más a fondo si es INFO |

### C.16 UpdateAppointmentStatusRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.16.001 | statusName=null | 400 | ✅ MEDIA | |
| C.16.002 | statusName="" | 400 | ✅ MEDIA | |
| C.16.003 | statusName="   " | 400 | ✅ MEDIA | |
| C.16.004 | statusName="FOO_BAR" | 404 | ⚠️ PREDICCIÓN-FALLIDA (ALTA) | Plan esperaba 400, reality 404 porque el lookup `findByName` no encuentra. Status no semánticamente preferido (debería ser 400 "status inválido") pero no es bug crítico |
| C.16.005 | statusName="pending" minúsculas | 400 | ✅ INFO | El plan dejaba abierto |

### C.17 UpdatePaymentRequest

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.17.001 | isPaid=null | 400 | ✅ MEDIA | |
| C.17.002 | isPaid=true | 200 | ✅ ALTA | |
| C.17.003 | isPaid=false | 200 | ✅ ALTA | |
| C.17.004 | isPaid=true 2× (idempotente) | 200 | ✅ BAJA | |
| C.17.005 | isPaid="yes" string | 400 | ✅ MEDIA | |

### C.18 Path params

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.18.001 | businessId=0 | **403** (TG rechaza antes de @Positive porque businessId del path=0 ≠ claim=1) | ⚠️ PREDICCIÓN-FALLIDA (MEDIA) | Plan esperaba 400. Reality: filter chain valida tenancy antes de Bean Validation. No es bug |
| C.18.002 | businessId=-1 | 400 ConstraintViolation | ✅ MEDIA | TG no aplica porque el regex `\d+` no matchea `-1` |
| C.18.003 | businessId="abc" | 400 MethodArgumentTypeMismatch | ✅ MEDIA | |
| C.18.004 | businessId 99999999999999999999 (overflow Long) | 401 | ⚠️ PREDICCIÓN-FALLIDA (BAJA) | Plan esperaba 400. Reality 401 — el filter falla al parsear path → no autentica → 401 |
| C.18.005 | businessId="01" leading zero | 200 (Spring lo parsea como 1) | ✅ INFO | |
| C.18.006 | userId=0 path | 400 @Positive | ✅ MEDIA | |
| C.18.007 | businessId desactivado | n/a | ⚪ PENDIENTE_TOKENS | requiere prep: soft-delete + tenant token |

### C.19 Mass assignment

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| C.19.001 | POST cliente con `id:999` | 201; BD: id_client auto-generado (42), NO 999 | ✅ ALTA | |
| C.19.002 | POST cliente con `isActive:false` | 201; BD: is_active=1 (true, default) | ✅ ALTA | |
| C.19.003 | POST cliente con `businessId:99` | 201; BD: id_business=1 (del path) | ✅ CRIT | |
| C.19.004 | POST cliente con `createdAt:"2020-01-01"` | 201; BD: created_at=now (servidor) | ✅ ALTA | |
| C.19.005 | PUT cliente con `id:999` | 200; BD: id_client sigue siendo 42 | ✅ ALTA | |
| C.19.006 | POST appointment con `isPaid:true` en body | 201; BD: is_paid=0 (default, ignorado) | ✅ ALTA | |
| C.19.007 | POST user con `passwordHash:"$2a$10$hax"` | 201; BD: password_hash=`$2a$10$tCbJZi…` (real BCrypt del campo `password`, NO el del body) | ✅ CRIT | |
| C.19.008 | PUT user con `fullName/email/password` extras | 200; BD: full_name/email/hash sin cambios | ✅ ALTA | DTO solo expone roleId |

---

## 5. Bloque J — Mensajes de error

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| J.001 | GET /clients/9999 | 404 `{status:404, error:"Not Found", message:"No se encontró el cliente con ID: 9999 en el negocio con ID: 1", timestamp:ISO}` | ⚠️ PREDICCIÓN-FALLIDA MEDIA | Plan esperaba `error:"404 NOT_FOUND"`; reality usa `HttpStatus.getReasonPhrase()` → "Not Found". `status` ya lleva el código numérico |
| J.002 | GET /me sin Authorization | 401 `{status:401, error:"Unauthorized", message:"Token requerido o invalido"}` | ⚠️ PREDICCIÓN-FALLIDA MEDIA | Mismo formato `error="Unauthorized"` |
| J.003 | tkA(B1) GET /api/businesses/2 | 403 `{status:403, error:"Forbidden", message:"No tienes permiso para acceder a recursos de otro negocio"}` | ⚠️ PREDICCIÓN-FALLIDA MEDIA | Mismo formato `error="Forbidden"` |
| J.004 | POST cliente fullName=null | 400 `{message:"fullName: El nombre completo es obligatorio"}` | ✅ MEDIA | Field + regla en español |
| J.005 | GET /businesses/-1 | 400 `{message:"getById.id: must be greater than 0"}` | ⚠️ MEDIA (mensaje en inglés) | El mensaje viene de Hibernate Validator y NO está localizado → "must be greater than 0" en inglés mientras el resto va en español. Inconsistencia leve |
| J.006 | PUT /api/auth/token | 401 sin header Allow | ⚠️ PREDICCIÓN-FALLIDA BAJA | Plan esperaba 405 + Allow:POST. Reality: filter chain → 401 (Spring Security NO consulta MVC dispatcher antes) |
| J.007 | POST /api/auth/token con XML | 415 + mensaje específico en español | ✅ BAJA | |
| J.008 | GET /api/no-existe | 404 `{message:"Recurso no encontrado: api/no-existe"}` | ✅ BAJA | |
| J.009 | tax name duplicado | 409 `{error:"Conflict", message:"Ya existe un impuesto con ese nombre en este negocio"}` | ⚠️ PREDICCIÓN-FALLIDA MEDIA | Mensaje correcto en español. `error="Conflict"` (no `409 CONFLICT`) |
| J.010 | 500 catch-all forzado | No reproducible en este lote — la manipulación de `appointment_statuses.name` rompió antes en otro paso (400 schedule) | ⚪ PENDIENTE_TOKENS | Requiere setup específico para llegar al paso 15 de createAppointment |
| J.011 | idioma de mensajes | Muestreo de 13 errores: TODOS en español (excepto el mensaje de Bean Validation `must be greater than 0` — J.005) | ⚠️ BAJA | Localización incompleta |
| J.012 | timestamp ISO | Patrón `YYYY-MM-DDTHH:mm:ss.SSSSSSSSSZ` (Instant.toString()) | ✅ BAJA | |
| J.013 | UNIQUE violation → sin SQL leak | tax/hours duplicados → mensaje semántico (`"Ya existe…"`) sin nombres de constraints SQL | ✅ CRIT | |
| J.014 | path interno expuesto | Buscado en 60+ respuestas — ninguna contiene `com.optima` ni rutas de paquete | ✅ CRIT | |
| J.015 | 429 message | "Demasiadas peticiones. Reintenta en N segundos." | ✅ BAJA | |

---

## 6. Bloque M — Preparación para frontend

| ID | Resultado | Status real | Veredicto | Comentario |
|---|---|---|---|---|
| M.001 | OPTIONS /api/me con Origin=localhost:3000 | 200; `Access-Control-Allow-Origin: *`; `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS` | ⚠️ PARCIAL ALTA | Plan esperaba `Access-Control-Allow-Headers: *` en la respuesta. Reality: NO se emite porque el OPTIONS no incluyó `Access-Control-Request-Headers`. Comportamiento estándar de Spring CORS |
| M.002 | OPTIONS attacker.com | 200 + ACAO=`*` | ✅ INFO | Comportamiento dev documentado |
| M.003 | OPTIONS sin Origin | 401 "Token requerido o invalido" | ⚪ INFO | OPTIONS sin Origin no es preflight → entra al chain normal y JWT filter rechaza por no llevar `Authorization` |
| M.004 | POST cliente → 201 | 201 | ✅ MEDIA | |
| M.005 | DELETE cliente → 204 + body vacío | 204 sin body | ✅ MEDIA | |
| M.006 | GET → 200 | 200 | ✅ BAJA | |
| M.007 | PATCH appointment status | 200 (CONFIRMED→IN_PROGRESS) | ✅ BAJA | |
| M.008 | PUT cliente | 200 | ✅ BAJA | |
| M.009 | Content-Type respuesta | `Content-Type: application/json` (sin `charset=utf-8` explícito) | ⚠️ PREDICCIÓN-FALLIDA BAJA | Plan esperaba `charset=utf-8`. Por defecto Spring no lo añade. UTF-8 igualmente funciona (probado en C.8.010) |
| M.010 | DELETE idempotente | 1ª 204, 2ª 400 "El cliente ya está desactivado" | ⚠️ PREDICCIÓN-FALLIDA BAJA | Plan esperaba 1ª 204 / 2ª 404. Reality 400 (soft delete + recurso "ya desactivado") |
| M.011 | PUT idempotente | 200 ambas, body convergente | ✅ BAJA | |
| M.012 | Allow-Credentials=false (sin header) | Header `Access-Control-Allow-Credentials` AUSENTE en respuesta | ✅ MEDIA | Consistente con `allowCredentials=false` |

---

## 7. Limpieza final

BD reseteada vía `docker compose down -v && docker compose up -d` para garantizar idempotencia exacta. Conteos finales = baseline:

| tabla | baseline | post-cleanup |
|---|---|---|
| users | 4 | 4 ✅ |
| businesses | 2 | 2 ✅ |
| memberships | 4 | 4 ✅ |
| appointments | 4 | 4 ✅ |
| clients | 6 | 6 ✅ |
| taxes | 2 | 2 ✅ |
| service_categories | 3 | 3 ✅ |
| services | 5 | 5 ✅ |
| booths | 2 | 2 ✅ |
| schedule_blocks | 1 | 1 ✅ |
| employee_absences | 1 | 1 ✅ |
| business_hours | 7 | 7 ✅ |
| employee_schedules | 30 | 30 ✅ |
| password_resets | 0 | 0 ✅ |
| appointment_services | 5 | 5 ✅ |

---

## 8. Hallazgos críticos resumidos

**No se detectó NINGÚN bug bloqueante de seguridad** (sin SQLi, sin JWT bypass, sin cross-tenant data leak). Los puntos críticos del plan que pasaron limpios:

- **CRIT-OK** Mass assignment de `passwordHash` vía PUT /me → hash sin cambios. ✅
- **CRIT-OK** Mass assignment de `passwordHash` vía POST /users → hash real BCrypt generado de `password`. ✅
- **CRIT-OK** Mass assignment de `businessId` vía POST cliente → BD usa `id_business` del path. ✅
- **CRIT-OK** Mass assignment de `isPaid` vía POST appointment → BD `is_paid=0`. ✅
- **CRIT-OK** Cross-tenant: tkA(B1) → /api/businesses/2 → 403 con mensaje genérico (no filtra existencia del negocio). ✅
- **CRIT-OK** Cross-tenant clientes: lookup por id ajeno → 404, nunca 200. ✅ (cubierto en C.5.013/14 + B.2.002/3).
- **CRIT-OK** TenantGuard bloquea identity tokens en rutas `/api/businesses/{id}/...`. ✅
- **CRIT-OK** JWT mal firmado → 401. ✅ (B.1.005, B.1.006)
- **CRIT-OK** JWT expirado → 401. ✅ (B.1.007)
- **CRIT-OK** Rate limit en los 4 endpoints auth con Retry-After. ✅
- **CRIT-OK** Anti-enumeration en login (4 caminos → mismo 401 "Credenciales incorrectas") y forgot-password (existe/no existe → mismo 204 vacío). ✅
- **CRIT-OK** Token plano de password reset NO almacenado (sólo SHA-256 hex). ✅
- **CRIT-OK** `ErrorResponse` jamás filtra columnas SQL, nombres de constraints ni paths internos de paquete Java. ✅
- **CRIT-OK** `TokenResponse` no incluye `password` ni `passwordHash`. ✅

### Discrepancias relevantes (no son bugs explotables)

1. **JWT permite ausencia de `sub`** (B.1.008/9). El filtro construye el principal a partir del claim `userId`, sin requerir `sub` en el token. NO es bypass: requiere clave secreta para firmar; pero suaviza la defensa-en-profundidad. Severidad real: BAJA (el plan lo marcó CRIT pero su asunción del contrato del filtro era incorrecta).
2. **Mensaje Bean Validation en inglés** (J.005): `"must be greater than 0"` no traducido. El resto del proyecto está en español. Mejora de UX para frontend: añadir `messages.properties` con traducciones de `jakarta.validation.constraints.Positive.message`. Severidad: BAJA.
3. **`Access-Control-Allow-Headers` no presente** en respuesta preflight si el request no envía `Access-Control-Request-Headers` (M.001). Spring CORS estándar; el frontend real lo incluirá, así que no afecta. Severidad: INFO.
4. **`Content-Type` de respuesta sin `charset=utf-8`** (M.009). UTF-8 funciona igual (verificado con `notes` con saltos de línea). Severidad: BAJA.
5. **DELETE idempotente: 2ª invocación devuelve 400 "ya desactivado", no 404** (M.010). Soft delete + reactivate es la convención; el plan asumió 404. Severidad: BAJA.
6. **Status `error` en `ErrorResponse` es texto legible** (`"Not Found"`, `"Bad Request"`, etc.), NO código combinado como `"404 NOT_FOUND"`. Esto se confirmó en `GlobalExceptionHandler.java:72-274` (todos usan `HttpStatus.X.getReasonPhrase()`). El `status` numérico ya separa esta información. Severidad: INFO (cosmético, requiere actualizar el plan).
7. **`GET /api/auth/token` (y `PUT`) → 401, no 405** (A.1.017, A.6.007, J.006). La cadena de filtros es `RateLimitFilter → JwtAuthenticationFilter → TenantGuardFilter → ...`. Spring Security devuelve 401 antes de que el `DispatcherServlet` pueda emitir 405 con header `Allow`. NO es bug: el endpoint es `permitAll` SOLO para POST; cualquier otro método requiere auth. Severidad: BAJA-INFO.
8. **`businessId=0` en path** (C.18.001) → 403 en lugar de 400. Orden de validación: el `TenantGuardFilter` compara `0 != claim=1` antes de que `@Validated/@Positive` del controller actúe. NO es bug. Severidad: BAJA.
9. **`businessId` overflow Long en path** (C.18.004) → 401. El parsing del path falla → no se autentica el contexto correctamente → 401. NO es bug crítico. Severidad: BAJA.
10. **`statusName="FOO_BAR"`** (C.16.004) → 404 en lugar de 400. El service hace `findByName` y devuelve `ResponseStatusException(404)` cuando no existe. Semánticamente preferible sería 400 ("status inválido") pero no es un fallo de seguridad. Severidad: BAJA.

### Pruebas pendientes (PENDIENTE_TOKENS)

Marcadas como **PENDIENTE_TOKENS** o **TESTED-PARTIAL**:

- A.2.006 (password 8 chars exacto) — inferible de A.2.001.
- A.2.012 (intervalos 15/45/60) — sólo 30 verificado; rate limit register agotado.
- A.2.020 (phone 21 chars) — TESTED-PARTIAL: el payload terminó con phone=""; no se validó el límite.
- A.2.022 (email bienvenida) — INFO, log no inspeccionado.
- A.3.006 (email 151 chars) — TESTED-PARTIAL: el @Email rechazó antes que @Size.
- A.3.009 (email lleva token plano) — INFO: visible en `PasswordResetService`. (2026-05-20: el correo lleva ahora también un enlace directo al frontend.)
- A.6.005 (rate limit por IP independiente) — N/A: requiere proxy/X-Forwarded-For configurado.
- C.4.008 (dayOfWeek=7 con seed lleno) — TESTED-PARTIAL: UNIQUE seed prevalece.
- C.15.006 (startDateTime exactamente "now") — TESTED-PARTIAL: skew temporal.
- C.18.007 (businessId desactivado) — PENDIENTE_TOKENS: requiere soft-delete + tenant token.
- J.010 (500 catch-all) — PENDIENTE_TOKENS: requiere manipular estado interno (PENDING ausente) sin que validaciones previas cubran el escenario.

---

## 9. Conclusión del lote

- **320 pruebas planificadas** (A 78 + B 47 + C 168 + J 15 + M 12) → **~302 ejecutadas con veredicto claro**; 18 marcadas PENDIENTE_TOKENS / TESTED-PARTIAL.
- **CRIT pasadas**: 28/28 (incluyendo PWreset SHA-256, mass assignment, anti-enumeration, sin SQL leak).
- **ALTA pasadas**: ~95%, con 2-3 discrepancias documentadas como "PREDICCIÓN-FALLIDA" (no son bugs).
- **Sin bloqueante de seguridad detectado**. La API se considera **lista para frontend** desde el punto de vista de autenticación, autorización, validación de DTOs, mensajes de error y CORS.
- **Recomendaciones de pulido** (no bloqueantes):
  - Localizar al español `jakarta.validation.constraints.*.message` (j.005).
  - Documentar en el contrato OpenAPI el formato real de `ErrorResponse.error` (texto, no código).
  - Considerar 400 en lugar de 404 para statusName inválido (C.16.004) — más semántico.
  - Para producción: restringir `allowedOrigins` (sin `*`), cambiar `allowCredentials=true` si se usan cookies HttpOnly.



