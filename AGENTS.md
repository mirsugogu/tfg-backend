# TFG Backend — Contexto del proyecto

Documento de contexto para que cualquier IA (Codex, Claude, GPT, etc.)
entienda el proyecto sin tener que releer todos los archivos. Mantenerlo
al día cuando haya cambios estructurales.

---

## 1. Descripción general

**Optima** es un backend REST de un SaaS multi-tenant para la gestión de
citas de pequeños negocios (peluquerías, clínicas, talleres, etc.).

Cada negocio (tenant) tiene sus propios usuarios, clientes, servicios,
impuestos y categorías. El alta de negocios es por auto-registro público
(sin superadmin de plataforma).

Proyecto **TFG de DAM** (Desarrollo de Aplicaciones Multiplataforma) de
un equipo de 3 personas. **NO se despliega a producción**: filtrar
propuestas por valor académico real, no por buenas prácticas abstractas.

---

## 2. Stack técnico

- **Java 21**
- **Spring Boot 3.4.1**
- **Maven** (build tool)
- **MySQL 8** (base de datos)
- **Docker + Docker Compose** (entorno local)
- **Lombok** (reducir boilerplate)
- **JJWT 0.12.6** (tokens JWT)
- **springdoc-openapi 2.8.0** (Swagger UI en `/swagger-ui.html`)

### Configuración

- `ddl-auto=none` (Hibernate NO modifica tablas; el schema SQL se aplica
  manualmente vía `docker-compose` con `docs/schema_v18.sql`).
- Puerto API: `8080`. Puerto MySQL host: `3307` (`3306` interno).

---

## 3. Arquitectura

### Multi-tenancy

**Shared Database, Shared Schema**: todos los negocios conviven en la
misma BD; cada fila lleva `id_business` (o `id_membership` que apunta a
la pertenencia usuario↔negocio). La validación cross-tenant se hace
**manualmente en la capa de servicio** (`findByIdAndBusinessId(...)`),
no a nivel de SQL.

### Estructura de paquetes (feature-based)

```
com.optima.api/
├── ApiApplication.java
├── config/                # SecurityConfig, WebConfig, OpenApiConfig
├── common/                # transversal
│   ├── exception/         # GlobalExceptionHandler, ErrorResponse
│   ├── security/          # JwtAuthenticationFilter, TenantGuardFilter, AuthPrincipal
│   ├── utils/             # JwtUtil
│   ├── geocoding/         # GeocodingService (Nominatim)
│   └── mail/              # MailService (Gmail SMTP)
└── modules/
    ├── auth/              # login 2-pasos, register
    ├── business/          # Business, Role, Tax, BusinessHour, Booth, ScheduleBlock, Membership
    ├── user/              # User, EmployeeSchedule, EmployeeAbsence, /api/me/*
    ├── client/            # Client
    ├── catalog/           # ServiceCategory, BusinessService (entity)
    └── appointment/       # Appointment, AppointmentStatus, BookedService, Availability
```

Cada módulo tiene `model/`, `repository/`, `service/`, `controller/`, y
`dto/` (algunos con `request/`+`response/` y otros con DTOs planos —
oportunidad de normalización).

### División del trabajo (TFG)

- **Persona 1**: módulo `business/` (incluye Tax, Role, BusinessHour,
  Booth, ScheduleBlock, Membership).
- **Persona 2**: módulos `user/` (incluye EmployeeSchedule,
  EmployeeAbsence) y `client/`.
- **Persona 3**: módulos `catalog/` (ServiceCategory, BusinessService) y
  `appointment/` (Appointment, AppointmentStatus, BookedService,
  Availability).
- **Transversal**: `auth/`, `config/`, `common/`.

---

## 4. Base de datos

Schema activo: `docs/schema_v18.sql` — **17 tablas** en inglés.
Versiones anteriores en `docs/archive/` (v13–v17).

| # | Tabla | Notas |
|---|---|---|
| 1 | `businesses` | tenants del SaaS |
| 2 | `roles` | catálogo global (ADMIN, EMPLOYEE) |
| 3 | `users` | identidad global; email UNIQUE global desde v16 |
| 4 | `memberships` | [v16] usuario↔negocio + rol; UNIQUE(id_user, id_business) |
| 5 | `employee_schedules` | horarios semanales; FK `id_membership` |
| 6 | `taxes` | impuestos (cada negocio define los suyos); `created_at` añadido en v18 |
| 7 | `clients` | clientes finales por negocio |
| 8 | `service_categories` | agrupación de servicios |
| 9 | `services` | mapeada a entidad `BusinessService` |
| 10 | `booths` | [v14] cabinas físicas tenant-scoped |
| 11 | `appointment_statuses` | catálogo global (PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW) |
| 12 | `appointments` | citas; FK `id_membership` (empleado), `id_booth` opcional |
| 13 | `appointment_services` | servicios reservados; precios congelados |
| 14 | `business_hours` | horario semanal de apertura |
| 15 | `employee_absences` | bloqueos puntuales por horas; FK `id_membership` |
| 16 | `schedule_blocks` | [v15] bloqueos por días completos (global / por empleado / por cabina) |
| 17 | `password_resets` | [v17] tokens efímeros (1h) SHA-256 para reset de password por email |

### Decisiones de diseño clave

- **Sin tabla `superadmins`**: alta de negocios por `POST /api/auth/register`.
- **Identidad global desde v16**: `users.email` es UNIQUE global; la
  pareja (negocio, persona, rol) vive en `memberships`. Una misma
  persona puede ser ADMIN de un negocio y EMPLOYEE de otro.
- **Soft delete** (`is_active` + `deactivated_at`) en tablas con
  histórico: `businesses`, `users`, `clients`, `taxes`,
  `service_categories`, `services`, `booths`. En `memberships` solo
  `is_active`.
- **Hard delete** en tablas sin histórico: `employee_schedules`,
  `appointment_services` (CASCADE), `schedule_blocks`.
- **Sin soft delete en `appointments`**: se usa el estado
  (`CANCELLED`, `NO_SHOW`).
- **Campos congelados** en `appointment_services`: `applied_price` y
  `applied_tax_percentage` se guardan al reservar; cambios posteriores
  no afectan el histórico.
- **Claves primarias**: SQL `id_xxx`, Java siempre `id` con
  `@Column(name = "id_xxx")`.
- **Nomenclatura honesta post-v16**: los DTOs Response/Request usan
  `membershipId` (no `employeeId` ni `userId`) cuando el valor es el ID
  de la membership. Los nombres de personas se llaman `userFullName`
  (no `employeeName`) porque viven en `users.full_name`, no en
  `memberships`. Paths URL conservan `/users/{userId}/` y `/businesses/`
  porque son lenguaje del negocio, no técnico; el `{userId}` del path
  ahí transporta un `membershipId` internamente (documentado en cada
  controller).

---

## 5. Entidades JPA

**16 entidades** (una por tabla):

- **`business/`**: `Business`, `Role`, `Tax`, `BusinessHour`, `Booth`,
  `ScheduleBlock`, `Membership`.
- **`user/`**: `User`, `EmployeeSchedule`, `EmployeeAbsence`.
- **`client/`**: `Client`.
- **`catalog/`**: `ServiceCategory`, `BusinessService` (renombrada para
  evitar conflicto con `@Service` de Spring; `appointment_services`
  pasó a `BookedService`).
- **`appointment/`**: `Appointment`, `AppointmentStatus`, `BookedService`.

### Convenciones

- `@Entity` + `@Table(name = "...")`.
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`.
  **Nunca** `@Data` ni `@EqualsAndHashCode` (problemas con relaciones).
- PK: `@Id @GeneratedValue(IDENTITY)` + `@Column(name = "id_xxx") private Long id;`.
- Relaciones siempre `@ManyToOne(fetch = LAZY)` y `@JoinColumn(nullable = false)`.
- Atributos de relación usan el objeto, no el ID (`private Business business;`).
- Defaults inicializados en Java (`private Boolean isActive = true;`).
- `createdAt` con `@PrePersist` + `LocalDateTime.now()` y `updatable = false`.
- Precios y porcentajes: `BigDecimal`. Fechas/horas: `LocalDateTime`,
  `LocalTime`, `LocalDate`.

---

## 6. Convenciones de código

- **Idioma del código**: inglés. **Comentarios y JavaDoc**: español.
- **Mensajes de error de la API**: español, con formato uniforme:
  - 404: `"No se encontró el X con ID: A en el negocio con ID: B"`
  - 409: `"Ya existe un X con ese nombre en este negocio"`
  - 400: `"El X está desactivado"` / `"El X ya está desactivado"`
- **Mappers**: manuales (no MapStruct). Cada Response DTO expone
  `public static XxxResponse from(Entity e)`.
- **Servicios**: `@Service` directo (sin interfaz separada).
  `@Transactional` a nivel de clase; lectores con
  `@Transactional(readOnly = true)`.
- **Inyección por constructor** con `@RequiredArgsConstructor` + `private final`.
- **Excepciones**: siempre `ResponseStatusException` con código HTTP
  correcto. Prohibido `IllegalArgumentException` o `IllegalStateException`
  desde la capa de servicio.
- **Controllers**: devuelven el DTO directo (no `ResponseEntity<>`),
  con `@ResponseStatus(CREATED)` en POST y `(NO_CONTENT)` en DELETE.
  Path params validados con `@Validated` + `@Positive`.
- **DTOs Request**: siempre `record` con Bean Validation
  (`@NotBlank`, `@NotNull`, `@Size`, `@Email`, etc.).
- **DTOs Response**: siempre `record`, PK siempre llamada `id`.
  Cuando el recurso pertenece a un tenant, incluye `businessId`.

---

## 7. Seguridad

- **BCrypt** para hash de contraseñas.
- **JWT** sin estado, firmado con HMAC-SHA384 (clave ≥ 32 chars
  validada en arranque).
- **Login en 2 pasos** (post-v16):
  - `POST /api/auth/token` con `{email, password}`. Si el usuario tiene
    1 sola membership devuelve **tenant token** (con `businessId`+`role`);
    si tiene varias, devuelve **identity token** + lista
    `MembershipSummaryResponse` para que el frontend muestre selector.
  - `POST /api/auth/select-business/{businessId}` canjea el identity
    token por uno tenant.
  - Mismo mensaje 401 (`"Credenciales incorrectas"`) en todos los
    fallos para no filtrar info.
- **`JwtAuthenticationFilter`** distingue identity vs tenant token por
  presencia del claim `businessId`.
- **`TenantGuardFilter`** rechaza con 403 cualquier
  `/api/businesses/{id}/...` cuando el principal es identity-only
  (mensaje accionable `"Debes seleccionar un negocio..."`) o cuando el
  `businessId` del path no coincide con el del token.
- **`@PreAuthorize("hasRole('ADMIN')")`** en mutaciones de
  configuración (negocios, taxes, services, users, schedules, etc.).
  `ClientController` y `AppointmentController` están sin `@PreAuthorize`
  intencionalmente (operativa diaria, ambos roles).
- **`GlobalExceptionHandler`** centraliza traducción a `ErrorResponse`.
- **Rate limiting** con Bucket4j (token-bucket en memoria, por IP):
  `/api/auth/token` 5/min, `/api/auth/register` 3/h,
  `/api/auth/forgot-password` 5/h, `/api/auth/reset-password` 10/h.
  Devuelve **429** + header `Retry-After`.
- **Password reset por email** (tabla `password_resets`):
  - `POST /api/auth/forgot-password {email}` → SIEMPRE 204 (anti-enumeration).
    Si el email existe, manda mail con token de 256 bits válido 1h.
  - `POST /api/auth/reset-password {token, newPassword}` → 204 si OK,
    400 con mensaje genérico si el token es inválido / caducado / ya
    usado (anti-replay marcando `used_at`). BD guarda `SHA-256(token)`,
    nunca el token plano.

---

## 8. Estado del proyecto

✅ **Completado**:
- Setup Spring Boot 3.4.1 + Java 21 + Maven + Docker.
- Schema v16 con 16 tablas; entidades JPA + repositories + DTOs
  (records) + services + controllers para los 16 recursos.
- Seguridad real (JWT 2-pasos, filtros, ADMIN/EMPLOYEE).
- Geocoding (Nominatim) cableado en `Business.create/update`.
- SMTP Gmail cableado (`MailService` best-effort; usado en
  `/api/auth/register`).
- Swagger UI en `/swagger-ui.html` con `bearerAuth` declarado.
- Paginación (`Page<T>` + `Pageable`) en los listados grandes.
- Endpoints `/api/me/*` (perfil identidad, password, lista memberships).
- `POST /api/auth/register` (auto-registro público; transacción
  identidad + Business + Membership ADMIN).
- `GET /api/businesses/{id}/availability` (algoritmo de huecos libres
  cruzando 6 calendarios: business_hours, employee_schedules,
  employee_absences, schedule_blocks, appointments, booths).
- `PATCH /api/businesses/{id}/appointments/{id}/payment` (campo `isPaid`).
- Tests: 14 verdes (`AuthService`, `TenantGuardFilter`,
  `AppointmentService`, `AvailabilityService`, `AppointmentControllerWebMvcTest`).
- JavaDoc pedagógico en español en todo el código (~130 archivos).

📋 **Pendiente** (orden de prioridad):
1. Refactor `AppointmentResponse.from(Appointment, repos)` → resolver
   N+1 cargando `BookedService` en una sola query previa.
2. Más tests (cobertura de services críticos, ~5–8 unit tests más).
3. Regenerar Postman collection (login ya no lleva `businessSlug`,
   nuevos endpoints `/api/me/*` y `/api/auth/select-business/{id}`).

---

## 9. Comandos útiles

```powershell
# Compilar
.\mvnw clean compile

# Ejecutar tests
.\mvnw test

# Levantar entorno (MySQL + API en Docker)
docker compose up -d

# Parar entorno
docker compose down
```

**MySQL local desde Workbench**: `127.0.0.1:3307`, user `optima_user`,
password `optima_pass`, db `optima_db`.

**Endpoints de exploración**:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 10. Repositorio

- URL: `https://github.com/mirsugogu/tfg-backend`
- Rama principal: `main` (la activa de desarrollo es `develop`).
- Política de ramas: `feat/...`, `fix/...`, `refactor/...`, `chore/...`,
  `docs/...`.
