# Mapa real del backend Optima — pre-frontend audit

**Fecha**: 2026-05-17 (actualizado 2026-05-21, 2026-05-23)
**Rama**: `audit/pre-frontend`
**Origen**: lectura directa del código (sin suposiciones).

> **Actualización 2026-05-23** — el backend creció con varias features
> que NO están reflejadas en las tablas de abajo. Para no reescribir el
> mapa, se listan aquí los deltas; la fuente canónica del contrato HTTP
> al día es **`docs/optima-postman-collection-v4.json`** (17 controllers,
> verificado al 100%):
>
> - `BusinessAbsencesController` (nuevo): `GET /api/businesses/{id}/absences?from&to`
>   — vista agregada de ausencias del negocio para el calendario.
> - `AppointmentController`: añadido `PUT /api/businesses/{id}/appointments/{id}`
>   (P9 reschedule). `AppointmentValidator` acepta `excludeAppointmentId`
>   en `validateNoOverlap` y `validateNoBoothOverlap`. `AvailabilityController`
>   acepta el mismo param.
> - `ClientController`: `GET /clients?search=` (autocompletado server-side
>   via `ClientRepository.searchActiveByBusiness`).
> - `BoothController` + DTOs (`Create/UpdateBoothRequest`, `BoothResponse`):
>   campo `color` con `@Pattern(cyan|amber|emerald|indigo|pink|sky|violet|teal)`.
> - `BusinessHourController` multi-tramo: se retiró el `UNIQUE` `(business, dayOfWeek)`
>   del schema; `BusinessHourService` valida solape `A<D AND C<B` por tramo abierto.
> - `Membership.color` (campo nuevo, VARCHAR 20) para asignar color al
>   empleado en el calendario.
> - `TenantGuardFilter.revalidateSession`: consulta la membership viva en
>   cada request y compara con los claims del JWT (rol + `is_active`);
>   401 si cambió.
>
> Actualización 2026-05-21: refleja la feature Archivar/Reactivar del 2026-05-20
> (6 endpoints `PATCH …/reactivate` + query param `?active` en los 6 listados
> con soft delete), la retirada de `@FutureOrPresent` de
> `CreateEmployeeAbsenceRequest`, el `@FutureOrPresent` añadido al `date` de
> `GET /availability`, y los amends in situ de `schema_v20.sql`
> (`uq_service_business_name` + 4 índices compuestos de optimización).

Convenciones de la tabla de endpoints:
- **Auth**: `permitAll` = sin JWT; `JWT` = cualquier JWT (incluye identity); `JWT tenant` = JWT con claim `businessId`; `ADMIN` = `@PreAuthorize("hasRole('ADMIN')")`; `sin @PreAuthorize` = cualquier autenticado (ADMIN o EMPLOYEE).
- **TG**: ✅ si el path matchea `^/api/businesses/(\d+)(/.*)?$` (sujeto a `TenantGuardFilter`); ❌ en caso contrario.
- **Pag**: ✅ si recibe `Pageable`; ❌ en caso contrario.

---

## 1. Endpoints

### 1.1 Auth (`/api/auth`) — `AuthController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Status éxito |
|---|---|---|---|---|---|---|
| 1 | POST `/api/auth/register` | permitAll | ❌ | `RegisterRequest` (anidado `@Valid CreateBusinessRequest business`, `@Valid AdminAccount admin`) | `TokenResponse` | 201 |
| 2 | POST `/api/auth/token` | permitAll | ❌ | `LoginRequest` | `TokenResponse` | 200 |
| 3 | POST `/api/auth/select-business/{businessId}` | JWT (identity o tenant); userId del `@AuthenticationPrincipal AuthPrincipal`; `@Positive Long businessId` | ❌ | — | `TokenResponse` | 200 |
| 4 | POST `/api/auth/forgot-password` | permitAll | ❌ | `ForgotPasswordRequest` | — | 204 |
| 5 | POST `/api/auth/reset-password` | permitAll | ❌ | `ResetPasswordRequest` | — | 204 |

### 1.2 Me (`/api/me`) — `MeController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Status éxito |
|---|---|---|---|---|---|---|
| 6 | GET `/api/me` | JWT | ❌ | — | `MeResponse` | 200 |
| 7 | PUT `/api/me` | JWT | ❌ | `UpdateMeRequest` | `MeResponse` | 200 |
| 8 | PUT `/api/me/password` | JWT | ❌ | `ChangePasswordRequest` | — | 204 |
| 9 | GET `/api/me/businesses` | JWT | ❌ | — | `List<MembershipSummaryResponse>` | 200 |

### 1.3 Catálogos globales

| # | Método + Path | Auth | TG | Request DTO | Response | Status éxito |
|---|---|---|---|---|---|---|
| 10 | GET `/api/roles` | permitAll | ❌ | — | `List<RoleResponse>` | 200 |
| 11 | GET `/api/appointment-statuses` | permitAll | ❌ | — | `List<AppointmentStatusResponse>` | 200 |
| 12 | GET `/api/appointment-statuses/{id}` | permitAll; `@Positive Long id` | ❌ | — | `AppointmentStatusResponse` | 200 |

### 1.4 Business raíz (`/api/businesses`) — `BusinessController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Status éxito |
|---|---|---|---|---|---|---|
| 13 | POST `/api/businesses` | ADMIN | ❌ | `CreateBusinessRequest` | `BusinessResponse` | 201 |
| 14 | GET `/api/businesses/{id}` | JWT tenant; `@Positive Long id` | ✅ | — | `BusinessResponse` | 200 |
| 15 | PUT `/api/businesses/{id}` | ADMIN | ✅ | `UpdateBusinessRequest` | `BusinessResponse` | 200 |
| 16 | DELETE `/api/businesses/{id}` | ADMIN | ✅ | — | — | 204 |
| 17 | PATCH `/api/businesses/{id}/reactivate` | ADMIN | ✅ | — | `BusinessResponse` | 200 |

### 1.5 Tax (`/api/businesses/{businessId}/taxes`) — `TaxController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 18 | POST `…/taxes` | ADMIN | ✅ | `CreateTaxRequest` | `TaxResponse` | — | 201 |
| 19 | GET `…/taxes?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<TaxResponse>` | ✅ | 200 |
| 20 | GET `…/taxes/{id}` | sin @PreAuthorize | ✅ | — | `TaxResponse` | — | 200 |
| 21 | PUT `…/taxes/{id}` | ADMIN | ✅ | `UpdateTaxRequest` | `TaxResponse` | — | 200 |
| 22 | DELETE `…/taxes/{id}` | ADMIN | ✅ | — | — | — | 204 |
| 23 | PATCH `…/taxes/{id}/reactivate` | ADMIN | ✅ | — | `TaxResponse` | — | 200 |

> `?active=true` (ausente/default) → impuestos activos; `?active=false` → archivados (vista desde la que se reactivan). `PATCH …/reactivate` revierte el soft delete (`is_active=true`, `deactivated_at=null`); 400 si ya estaba activo.

### 1.6 BusinessHour (`/api/businesses/{businessId}/hours`) — `BusinessHourController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 24 | POST `…/hours` | ADMIN | ✅ | `CreateBusinessHourRequest` | `BusinessHourResponse` | — | 201 |
| 25 | GET `…/hours` | sin @PreAuthorize | ✅ | — | `List<BusinessHourResponse>` | ❌ | 200 |
| 26 | GET `…/hours/{id}` | sin @PreAuthorize | ✅ | — | `BusinessHourResponse` | — | 200 |
| 27 | PUT `…/hours/{id}` | ADMIN | ✅ | `UpdateBusinessHourRequest` | `BusinessHourResponse` | — | 200 |
| 28 | DELETE `…/hours/{id}` | ADMIN | ✅ | — | — | — | 204 |

> BusinessHour no tiene soft delete (`is_active`), por lo que no expone `?active` ni `…/reactivate`.

### 1.7 Booth (`/api/businesses/{businessId}/booths`) — `BoothController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 29 | POST `…/booths` | ADMIN | ✅ | `CreateBoothRequest` | `BoothResponse` | — | 201 |
| 30 | GET `…/booths?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<BoothResponse>` | ✅ | 200 |
| 31 | GET `…/booths/{id}` | sin @PreAuthorize | ✅ | — | `BoothResponse` | — | 200 |
| 32 | PUT `…/booths/{id}` | ADMIN | ✅ | `UpdateBoothRequest` | `BoothResponse` | — | 200 |
| 33 | DELETE `…/booths/{id}` | ADMIN | ✅ | — | — | — | 204 |
| 34 | PATCH `…/booths/{id}/reactivate` | ADMIN | ✅ | — | `BoothResponse` | — | 200 |

> `?active=true` (ausente/default) → cabinas activas; `?active=false` → archivadas. `PATCH …/reactivate` revierte el soft delete (`is_active=true`, `deactivated_at=null`); 400 si ya estaba activa.

### 1.8 ScheduleBlock (`/api/businesses/{businessId}/schedule-blocks`) — `ScheduleBlockController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 35 | POST `…/schedule-blocks` | ADMIN | ✅ | `CreateScheduleBlockRequest` | `ScheduleBlockResponse` | — | 201 |
| 36 | GET `…/schedule-blocks` | sin @PreAuthorize | ✅ | — | `Page<ScheduleBlockResponse>` | ✅ | 200 |
| 37 | GET `…/schedule-blocks/{id}` | sin @PreAuthorize | ✅ | — | `ScheduleBlockResponse` | — | 200 |
| 38 | DELETE `…/schedule-blocks/{id}` | ADMIN | ✅ | — | — | — | 204 |

> Sin PUT en este recurso (decisión consciente del Javadoc). Hard delete: no tiene soft delete, no expone `?active` ni `…/reactivate`.

### 1.9 ServiceCategory (`/api/businesses/{businessId}/categories`) — `ServiceCategoryController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 39 | POST `…/categories` | ADMIN | ✅ | `CreateCategoryRequest` | `ServiceCategoryResponse` | — | 201 |
| 40 | GET `…/categories?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<ServiceCategoryResponse>` | ✅ | 200 |
| 41 | GET `…/categories/{id}` | sin @PreAuthorize | ✅ | — | `ServiceCategoryResponse` | — | 200 |
| 42 | PUT `…/categories/{id}` | ADMIN | ✅ | `UpdateCategoryRequest` | `ServiceCategoryResponse` | — | 200 |
| 43 | DELETE `…/categories/{id}` | ADMIN | ✅ | — | — | — | 204 |
| 44 | PATCH `…/categories/{id}/reactivate` | ADMIN | ✅ | — | `ServiceCategoryResponse` | — | 200 |

> `?active=true` (ausente/default) → categorías activas; `?active=false` → archivadas. `PATCH …/reactivate` revierte el soft delete (`is_active=true`, `deactivated_at=null`); 400 si ya estaba activa.

### 1.10 BusinessService (`/api/businesses/{businessId}/services`) — `BusinessServiceController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 45 | POST `…/services` | ADMIN | ✅ | `CreateServiceRequest` | `BusinessServiceResponse` | — | 201 |
| 46 | GET `…/services?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<BusinessServiceResponse>` | ✅ | 200 |
| 47 | GET `…/services/{id}` | sin @PreAuthorize | ✅ | — | `BusinessServiceResponse` | — | 200 |
| 48 | PUT `…/services/{id}` | ADMIN | ✅ | `UpdateServiceRequest` | `BusinessServiceResponse` | — | 200 |
| 49 | DELETE `…/services/{id}` | ADMIN | ✅ | — | — | — | 204 |
| 50 | PATCH `…/services/{id}/reactivate` | ADMIN | ✅ | — | `BusinessServiceResponse` | — | 200 |

> `?active=true` (ausente/default) → servicios activos; `?active=false` → archivados. `PATCH …/reactivate` revierte el soft delete (`is_active=true`, `deactivated_at=null`); 400 si ya estaba activo.

### 1.11 Client (`/api/businesses/{businessId}/clients`) — `ClientController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 51 | POST `…/clients` | sin @PreAuthorize | ✅ | `CreateClientRequest` | `ClientResponse` | — | 201 |
| 52 | GET `…/clients?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<ClientResponse>` | ✅ | 200 |
| 53 | GET `…/clients/{id}` | sin @PreAuthorize | ✅ | — | `ClientResponse` | — | 200 |
| 54 | PUT `…/clients/{id}` | sin @PreAuthorize | ✅ | `UpdateClientRequest` | `ClientResponse` | — | 200 |
| 55 | DELETE `…/clients/{id}` | sin @PreAuthorize | ✅ | — | — | — | 204 |
| 56 | PATCH `…/clients/{id}/reactivate` | sin @PreAuthorize | ✅ | — | `ClientResponse` | — | 200 |

> Ningún endpoint con `@PreAuthorize` (decisión consciente: ADMIN+EMPLOYEE manejan clientes) — incluido `PATCH …/reactivate` (coherente con el DELETE del recurso). `?active=true` (ausente/default) → clientes activos; `?active=false` → archivados. `PATCH …/reactivate` revierte el soft delete (`is_active=true`, `deactivated_at=null`); 400 si ya estaba activo.

### 1.12 User (`/api/businesses/{businessId}/users`) — `UserController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 57 | POST `…/users` | ADMIN | ✅ | `CreateUserRequest` | `UserResponse` | — | 201 |
| 58 | GET `…/users?active=` | sin @PreAuthorize | ✅ | query: `boolean active` (default `true`) | `Page<UserResponse>` | ✅ | 200 |
| 59 | GET `…/users/{id}` | sin @PreAuthorize | ✅ | — | `UserResponse` | — | 200 |
| 60 | PUT `…/users/{id}` | ADMIN | ✅ | `UpdateUserRequest` | `UserResponse` | — | 200 |
| 61 | DELETE `…/users/{id}` | ADMIN | ✅ | — | — | — | 204 |
| 62 | PATCH `…/users/{id}/reactivate` | ADMIN | ✅ | — | `UserResponse` | — | 200 |

> `?active=true` (ausente/default) → empleados (memberships) activos; `?active=false` → archivados. `PATCH …/reactivate` revierte el soft delete de la membership (`is_active=true`); 400 si ya estaba activo.

### 1.13 EmployeeSchedule (`/api/businesses/{businessId}/users/{userId}/schedules`) — `EmployeeScheduleController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 63 | POST `…/schedules` | ADMIN | ✅ | `CreateEmployeeScheduleRequest` | `EmployeeScheduleResponse` | — | 201 |
| 64 | GET `…/schedules` | sin @PreAuthorize | ✅ | — | `List<EmployeeScheduleResponse>` | ❌ | 200 |
| 65 | GET `…/schedules/{id}` | sin @PreAuthorize | ✅ | — | `EmployeeScheduleResponse` | — | 200 |
| 66 | PUT `…/schedules/{id}` | ADMIN | ✅ | `UpdateEmployeeScheduleRequest` | `EmployeeScheduleResponse` | — | 200 |
| 67 | DELETE `…/schedules/{id}` | ADMIN | ✅ | — | — | — | 204 |

### 1.14 EmployeeAbsence (`/api/businesses/{businessId}/users/{userId}/absences`) — `EmployeeAbsenceController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 68 | POST `…/absences` | ADMIN | ✅ | `CreateEmployeeAbsenceRequest` | `EmployeeAbsenceResponse` | — | 201 |
| 69 | GET `…/absences` | sin @PreAuthorize | ✅ | — | `Page<EmployeeAbsenceResponse>` | ✅ | 200 |
| 70 | GET `…/absences/{id}` | sin @PreAuthorize | ✅ | — | `EmployeeAbsenceResponse` | — | 200 |
| 71 | PUT `…/absences/{id}` | ADMIN | ✅ | `UpdateEmployeeAbsenceRequest` | `EmployeeAbsenceResponse` | — | 200 |
| 72 | DELETE `…/absences/{id}` | ADMIN | ✅ | — | — | — | 204 |

### 1.15 Appointment (`/api/businesses/{businessId}/appointments`) — `AppointmentController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Pag | Status éxito |
|---|---|---|---|---|---|---|---|
| 73 | POST `…/appointments` | sin @PreAuthorize | ✅ | `CreateAppointmentRequest` | `AppointmentResponse` | — | 201 |
| 74 | GET `…/appointments?from=&to=&membershipId=` | sin @PreAuthorize | ✅ | query: `LocalDate from`, `LocalDate to`, `@Positive Long membershipId` | `Page<AppointmentResponse>` | ✅ | 200 |
| 75 | GET `…/appointments/{id}` | sin @PreAuthorize | ✅ | — | `AppointmentResponse` | — | 200 |
| 76 | PATCH `…/appointments/{id}/status` | sin @PreAuthorize | ✅ | `UpdateAppointmentStatusRequest` | `AppointmentResponse` | — | 200 |
| 77 | PATCH `…/appointments/{id}/payment` | sin @PreAuthorize | ✅ | `UpdatePaymentRequest` | `AppointmentResponse` | — | 200 |

> Ningún PUT ni DELETE: una cita una vez creada solo cambia de estado.

### 1.16 Availability (`/api/businesses/{businessId}/availability`) — `AvailabilityController.java`

| # | Método + Path | Auth | TG | Request DTO | Response | Status éxito |
|---|---|---|---|---|---|---|
| 78 | GET `…/availability?date=&serviceIds=&membershipId=&boothId=` | sin @PreAuthorize | ✅ | query: `@FutureOrPresent @DateTimeFormat(ISO=DATE) LocalDate date` (obligatorio, rechaza fechas pasadas con 400), `@NotEmpty List<@NotNull @Positive Long> serviceIds` (obligatorio), `@Positive Long membershipId` (opcional), `@Positive Long boothId` (opcional) | `AvailabilityResponse` | 200 |

### 1.17 Totales

**78 endpoints REST** distribuidos en **17 controllers**.

| Grupo | Endpoints |
|---|---|
| Auth | 5 |
| Me | 4 |
| Catálogos públicos (Role + AppointmentStatus) | 1 + 2 = 3 |
| Business raíz (incluye `…/reactivate`) | 5 |
| Recursos tenant-scoped CRUD sin soft delete (Hours, Sched) | 2 × 5 = 10 |
| Recursos tenant-scoped CRUD con soft delete + `…/reactivate` (Tax, Booth, Cat, Service, Client, User) | 6 × 6 = 36 |
| ScheduleBlock (sin PUT, sin soft delete) | 4 |
| Appointment | 5 |
| Availability | 1 |
| **Total** | **78** |

> Los 6 recursos con soft delete (`taxes`, `booths`, `categories`, `services`, `clients`, `users`) ganaron el 2026-05-20 un `PATCH …/reactivate` y el query param `?active=true|false` en su listado (feature Archivar/Reactivar). `Business` ya tenía `…/reactivate` desde antes.

---

## 2. Validaciones de Request DTOs

### 2.1 Auth

**`LoginRequest`** (`auth/dto/request/LoginRequest.java`):
```text
email     @NotBlank
password  @NotBlank
```
(Sin `@Email` ni `@Size(min=8)` — falla pasa al service como 401.)

**`RegisterRequest`** (anidado):
```text
business  @NotNull @Valid → CreateBusinessRequest
admin     @NotNull @Valid → AdminAccount {
    fullName  @NotBlank @Size(max=150)
    email     @NotBlank @Email @Size(max=150)
    password  @NotBlank @Size(min=8, max=100)
    phone     @Size(max=20)               (opcional)
}
```

**`ForgotPasswordRequest`**:
```text
email     @NotBlank @Email @Size(max=150)
```

**`ResetPasswordRequest`**:
```text
token        @NotBlank @Size(max=255)
newPassword  @NotBlank @Size(min=8, max=100)
```

### 2.2 Business

**`CreateBusinessRequest`**:
```text
name                @NotBlank @Size(max=150)
slug                @NotBlank @Size(max=150)
email               @NotBlank @Email @Size(max=150)
phone               @Size(max=20)                (opcional)
address             @Size(max=255)               (opcional)
city                @Size(max=100)               (opcional)
state               @Size(max=100)               (opcional)
country             @Size(max=100)               (opcional)
postalCode          @Size(max=20)                (opcional)
appointmentInterval Integer                      (opcional; default 30 en service; service valida 15/30/45/60)
```

**`UpdateBusinessRequest`**: igual que Create EXCEPTO sin `slug` (inmutable).

**`CreateTaxRequest`** / **`UpdateTaxRequest`** (idénticos):
```text
name        @NotBlank @Size(max=50)
percentage  @NotNull @DecimalMin(0.00) @DecimalMax(100.00)  BigDecimal
```

**`CreateBoothRequest`** / **`UpdateBoothRequest`** (idénticos):
```text
name  @NotBlank @Size(max=80)
```

**`CreateBusinessHourRequest`** / **`UpdateBusinessHourRequest`** (idénticos):
```text
dayOfWeek  @NotNull @Min(1) @Max(7)
startTime  LocalTime                  (obligatoriedad cruzada con isClosed en service)
endTime    LocalTime                  (idem)
isClosed   Boolean                    (idem)
```

**`CreateScheduleBlockRequest`** (sin Update — recurso sin PUT):
```text
membershipId  @Positive            (opcional; null si bloqueo global o por cabina)
boothId       @Positive            (opcional; null si bloqueo global o por empleado)
startDate     @NotNull             (service valida startDate <= endDate)
endDate       @NotNull
reason        @Size(max=255)       (opcional)
```

### 2.3 Catalog

**`CreateCategoryRequest`** / **`UpdateCategoryRequest`** (idénticos):
```text
name  @NotBlank @Size(max=100)
```

**`CreateServiceRequest`** / **`UpdateServiceRequest`** (idénticos):
```text
categoryId       @NotNull @Positive
taxId            @NotNull @Positive
name             @NotBlank @Size(max=150)
description      String                  (opcional, sin validación)
price            @NotNull @DecimalMin(0.0)                BigDecimal
durationMinutes  @NotNull @Min(1)                          Integer
```

### 2.4 Client

**`CreateClientRequest`** / **`UpdateClientRequest`** (idénticos):
```text
fullName  @NotBlank @Size(max=150)
email     @Email @Size(max=150)     (opcional)
phone     @Size(max=20)             (opcional)
notes     String                    (opcional, sin validación)
```

### 2.5 User

**`CreateUserRequest`**:
```text
roleId    @NotNull @Positive
fullName  @NotBlank @Size(max=150)
email     @NotBlank @Email @Size(max=150)
password  @NotBlank @Size(min=8, max=100)
phone     @Size(max=20)             (opcional)
```

**`UpdateUserRequest`** (solo `roleId` — mutación de membership):
```text
roleId  @NotNull @Positive
```

**`UpdateMeRequest`** (no expone `password`):
```text
fullName  @NotBlank @Size(max=150)
email     @NotBlank @Email @Size(max=150)
phone     @Size(max=20)             (opcional)
```

**`ChangePasswordRequest`**:
```text
currentPassword  @NotBlank
newPassword      @NotBlank @Size(min=8, max=100)
```

**`CreateEmployeeScheduleRequest`** / **`UpdateEmployeeScheduleRequest`** (idénticos):
```text
dayOfWeek  @NotNull @Min(1) @Max(7)
startTime  @NotNull LocalTime
endTime    @NotNull LocalTime
```

**`CreateEmployeeAbsenceRequest`** (sin `@FutureOrPresent` — eliminado 2026-05-20; admite registrar una ausencia ya iniciada):
```text
startDateTime  @NotNull LocalDateTime
endDateTime    @NotNull LocalDateTime
reason         @Size(max=255)           (opcional)
```

**`UpdateEmployeeAbsenceRequest`** (igual que Create; nunca tuvo `@FutureOrPresent`):
```text
startDateTime  @NotNull LocalDateTime
endDateTime    @NotNull LocalDateTime
reason         @Size(max=255)           (opcional)
```

### 2.6 Appointment

**`CreateAppointmentRequest`**:
```text
clientId       @NotNull @Positive
membershipId   @NotNull @Positive
boothId        @Positive                       (opcional)
startDateTime  @NotNull @FutureOrPresent LocalDateTime
notes          String                          (opcional)
serviceIds     @NotEmpty List<@NotNull @Positive Long>
                                                (validación de elementos añadida 2026-05-17 — commit 7c6d7f7)
```

**`UpdateAppointmentStatusRequest`**:
```text
statusName  @NotBlank String
```

**`UpdatePaymentRequest`**:
```text
isPaid  @NotNull Boolean
```

### 2.7 Path params (todos los controllers)

`@Validated` a nivel de clase + `@Positive` en cada `@PathVariable Long`. Aplica al 100% de los controllers.

### 2.8 Query params

- `AvailabilityController`: `date` `@FutureOrPresent @DateTimeFormat(iso=DATE) LocalDate` obligatorio (rechaza fechas pasadas con 400 — `@FutureOrPresent` añadido 2026-05-20); `serviceIds` `@NotEmpty List<@NotNull @Positive Long>` obligatorio; `membershipId` y `boothId` `@Positive Long` opcionales.
- `AppointmentController.searchAppointments`: `from`, `to` `@DateTimeFormat(iso=DATE) LocalDate` opcionales; `membershipId` `@Positive Long` opcional.
- Listados con soft delete (`TaxController`, `BoothController`, `ServiceCategoryController`, `BusinessServiceController`, `ClientController`, `UserController`): `active` `boolean` opcional con `@RequestParam(defaultValue = "true")` — ausente/`true` = recursos activos, `false` = archivados (soft-deleted). Añadido 2026-05-20 (feature Archivar/Reactivar).

---

## 3. CHECK y UNIQUE de `schema_v20.sql`

### 3.1 CHECK constraints

> Líneas SQL referidas a `docs/schema_v20.sql` en su estado actual (incluye los amends in situ de 2026-05-20: `uq_service_business_name` y los 4 índices de optimización).

| Línea SQL | Constraint | Tabla | Regla |
|---|---|---|---|
| 109 | `chk_appointment_interval` | `businesses` | `appointment_interval IN (15, 30, 45, 60)` |
| 183 | `chk_day_of_week` | `employee_schedules` | `day_of_week BETWEEN 1 AND 7` |
| 185 | `chk_schedule_times` | `employee_schedules` | `start_time < end_time` |
| 204 | `chk_tax_percentage` | `taxes` | `percentage >= 0 AND percentage <= 100` |
| 264 | `chk_service_price` | `services` | `price >= 0` |
| 266 | `chk_service_duration` | `services` | `duration_minutes > 0` |
| 365 | `chk_appointment_times` | `appointments` | `start_datetime < end_datetime` |
| 389 | `chk_appsvc_applied_price` | `appointment_services` | `applied_price >= 0` |
| 391 | `chk_appsvc_applied_tax` | `appointment_services` | `applied_tax_percentage >= 0 AND <= 100` |
| 410 | `chk_bh_day_of_week` | `business_hours` | `day_of_week BETWEEN 1 AND 7` |
| 413 | `chk_bh_times_logic` | `business_hours` | `is_closed = TRUE OR (start_time NOT NULL AND end_time NOT NULL AND start_time < end_time)` |
| 443 | `chk_absence_times` | `employee_absences` | `start_datetime < end_datetime` |
| 475 | `chk_block_dates` | `schedule_blocks` | `start_date <= end_date` |
| 477 | `chk_block_target` | `schedule_blocks` | `id_membership IS NULL OR id_booth IS NULL` |

### 3.2 UNIQUE constraints

| Línea SQL | Constraint | Tabla | Columnas |
|---|---|---|---|
| 90 | UNIQUE (col-level) | `businesses` | `slug` |
| 91 | UNIQUE (col-level) | `businesses` | `email` |
| 118 | UNIQUE (col-level) | `roles` | `name` |
| 135 | UNIQUE (col-level) | `users` | `email` (global, post-v16) |
| 164–165 | `uq_membership_user_business` | `memberships` | `(id_user, id_business)` |
| 202–203 | `uq_tax_business_name` | `taxes` | `(id_business, name)` |
| 237–238 | `uq_category_business_name` | `service_categories` | `(id_business, name)` |
| 262–263 | `uq_service_business_name` | `services` | `(id_business, name)` — añadido 2026-05-20 (post-auditoría); faltaba solo a nivel BD (el service ya validaba el nombre duplicado) |
| 285–286 | `uq_booth_business_name` | `booths` | `(id_business, name)` |
| 296 | UNIQUE (col-level) | `appointment_statuses` | `name` |
| 367–368 | `uq_appointment_active_slot` | `appointments` | `(active_slot_key)` — columna virtual `(id_membership, start)` o NULL; anti-doble-reserva por empleado, commit `95e3958` |
| 369–370 | `uq_appointment_active_booth_slot` | `appointments` | `(active_booth_slot_key)` — columna virtual `(id_booth, start)` o NULL; anti-doble-reserva por cabina, commit `95e3958` |
| ~~420–421~~ | ~~`uq_business_hours_day`~~ | ~~`business_hours`~~ | **Quitado 2026-05-23 (P1-negocio)**: el negocio puede tener turno partido (10-14 + 16-20); la no-superposición se valida en `BusinessHourService` con overlap `A<D AND C<B`, no a nivel de SQL. |
| 502 | UNIQUE (col-level) | `password_resets` | `token_hash` |

### 3.3 Foreign Keys con `ON DELETE` no default

| FK | Tabla | DELETE_RULE |
|---|---|---|
| `fk_business_hours_business` | `business_hours` | `ON DELETE CASCADE` |
| `fk_appsvc_appointment` | `appointment_services` | `ON DELETE CASCADE` |
| `fk_absence_membership` | `employee_absences` | `ON DELETE CASCADE` |
| `fk_reset_user` | `password_resets` | `ON DELETE CASCADE` |
| El resto de FKs | varios | `NO ACTION` (default, equivalente funcional a `RESTRICT` en InnoDB) |

### 3.4 Índices compuestos de optimización

InnoDB indexa automáticamente cada PK, cada UNIQUE y cada columna FK. Estos 4 índices compuestos adicionales —añadidos 2026-05-20 (post-auditoría)— cubren las consultas del camino crítico de crear cita y de calcular disponibilidad.

| Línea SQL | Índice | Tabla | Columnas | Query que acelera |
|---|---|---|---|---|
| 529–530 | `idx_appt_membership_start` | `appointments` | `(id_membership, start_datetime)` | `AppointmentRepository.existsOverlappingAppointment` (cada POST `/appointments`) |
| 535–536 | `idx_appt_business_start` | `appointments` | `(id_business, start_datetime)` | `findActiveByBusinessAndDay` (GET `/availability`) y `searchAppointments` (GET `/appointments`) |
| 540–541 | `idx_appt_booth_start` | `appointments` | `(id_booth, start_datetime)` | `existsOverlappingBoothAppointment` (POST `/appointments` con cabina) |
| 546–547 | `idx_schedule_membership_day` | `employee_schedules` | `(id_membership, day_of_week)` | `findAllByMembershipIdAndDayOfWeek` (validación de cita y disponibilidad) |

> `employee_absences` y `schedule_blocks` se dejan a propósito sin índice (tablas pequeñas; un índice solo añadiría coste de escritura). `business_hours` ya está cubierta por su `UNIQUE(id_business, day_of_week)`.

---

## 4. Reglas de negocio de `AppointmentService.createAppointment`

Tomado del Javadoc del método `createAppointment` y del orden de invocación
real (la numeración coincide con los comentarios del cuerpo del método):

```text
 1.  Verifica que el negocio existe                          → 404 si no.
 1b. El negocio debe estar activo                            → 400 si desactivado.
 2.  Cross-tenant: cliente pertenece a este negocio          → 404 si no.
 2b. El cliente debe estar activo                            → 400 si desactivado.
 3.  Cross-tenant: empleado (membership) pertenece al negocio
     (SELECT ... FOR UPDATE — lock pesimista)                → 404 si no.
 3b. El empleado (membership) debe estar activo              → 400 si desactivado.
 4.  La hora respeta el appointmentInterval del negocio
     (validator.validateAppointmentInterval)                 → 400 si no.
 5.  Cross-tenant: cada servicio pertenece al negocio y está
     activo                                                  → 404 / 400.
 6.  Calcula endDateTime sumando las duraciones de los servicios.
 6b. La cita cae en el horario de apertura del negocio
     (validator.validateBusinessHours)                       → 400 si cerrado/no encaja.
 7.  La cita encaja en el horario del empleado
     (validator.validateEmployeeSchedule; no cruza medianoche) → 400 si no.
 8.  No solapa con otra cita activa del empleado
     (validator.validateNoOverlap)                           → 409 si sí.
 8a. No solapa con una ausencia registrada del empleado
     (validator.validateNoEmployeeAbsence)                   → 409 si sí.
 8b. Si lleva cabina: cross-tenant (lock pesimista) + activa +
     sin solapamiento (validator.validateNoBoothOverlap)     → 404/400/409.
 8c. No choca con un bloqueo de agenda (global, por empleado o
     por cabina; validator.validateNoScheduleBlock)          → 409 con `reason`.
 9.  Existe el estado PENDING en BD                          → 500 si no (error de seed).
```

Tras el paso 9: persiste `Appointment` con status PENDING, crea N `BookedService` con `applied_price` y `applied_tax_percentage` congelados, devuelve `AppointmentResponse.from(saved, savedBookedServices)`.

---

## 5. State machine de citas (`AppointmentValidator.VALID_TRANSITIONS`)

Definido como `Map<String, Set<String>>` literal (`AppointmentValidator.java:66-70`):

```java
private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
        "PENDING",     Set.of("CONFIRMED", "CANCELLED"),
        "CONFIRMED",   Set.of("IN_PROGRESS", "CANCELLED", "NO_SHOW"),
        "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED")
);
```

Transiciones permitidas:

| Desde | A |
|---|---|
| `PENDING` | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | `IN_PROGRESS`, `CANCELLED`, `NO_SHOW` |
| `IN_PROGRESS` | `COMPLETED`, `CANCELLED` |

Estados finales (no aparecen como clave; cualquier transición desde ellos lanza 400 "estado final"):

- `COMPLETED`
- `CANCELLED`
- `NO_SHOW`

Una transición no listada (p. ej. `PENDING → IN_PROGRESS`, `CONFIRMED → COMPLETED` saltándose `IN_PROGRESS`, `COMPLETED → CANCELLED`) lanza `ResponseStatusException(400, "No se puede pasar de X a Y. Transiciones válidas: [...]")`.

---

## 6. Configuración relevante

### 6.1 `application.properties`

```properties
# Server
server.port=8080
spring.application.name=api

# Datasource (local fuera de Docker)
spring.datasource.url=jdbc:mysql://localhost:3307/optima_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&useUnicode=true

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false

# Paginación
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100

# JWT
app.jwt.secret=${JWT_SECRET:EstaEsUnaClaveSuperSecretaYMuyLargaParaQueNoExplote2026!}
app.jwt.expiration-ms=${JWT_EXPIRATION:86400000}   # 24h

# Mail (Gmail SMTP)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000

# Geocoding (Nominatim)
app.geocoding.user-agent=optima-tfg/1.0 (contacto: diegoalejandrobarrero@gmail.com)
app.geocoding.timeout-ms=5000

# OpenAPI / Swagger
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
```

### 6.2 Rate limits — `RateLimitFilter.java` (hard-coded, no en properties)

| Endpoint | Política |
|---|---|
| `POST /api/auth/token` | 20 / minuto / IP |
| `POST /api/auth/register` | 20 / hora / IP |
| `POST /api/auth/forgot-password` | 10 / hora / IP |
| `POST /api/auth/reset-password` | 20 / hora / IP |

Respuesta al exceder: `429 Too Many Requests` + header `Retry-After: <segundos>`. Bucket por IP en memoria (`ConcurrentHashMap` + Bucket4j).

### 6.3 CORS — `SecurityConfig.corsConfigurationSource()`

```java
allowedOrigins:   "*"
allowedMethods:   GET, POST, PUT, PATCH, DELETE, OPTIONS
allowedHeaders:   "*"
allowCredentials: false
path:             /api/**
```

### 6.4 SecurityConfig — paths `permitAll`

```text
POST  /api/auth/token
POST  /api/auth/register
POST  /api/auth/forgot-password
POST  /api/auth/reset-password
GET   /api/roles
GET   /api/appointment-statuses/**
GET   /swagger-ui.html
GET   /swagger-ui/**
GET   /v3/api-docs
GET   /v3/api-docs/**
```

Resto: `anyRequest().authenticated()`.

Cadena de filtros (orden): `RateLimitFilter → JwtAuthenticationFilter → TenantGuardFilter`.

### 6.5 JWT

- Algoritmo: `HMAC-SHA384` (clave ≥ 32 chars validada en `@PostConstruct` de `JwtUtil`).
- Claims tenant: `sub` (email), `userId`, `businessId`, `role`, `iat`, `exp`.
- Claims identity: `sub` (email), `userId`, `iat`, `exp` (sin `businessId` ni `role`).
- Expiración por defecto: 86 400 000 ms (24 h).

---

## 7. Endpoints o validaciones detectados que NO están documentados en AGENTS.md

1. **`POST /api/businesses`** — código muerto reconocido en el Javadoc de `BusinessController` ("Inalcanzable en el flujo real…"). No aparece listado en AGENTS.md sección 8.
2. **`PATCH /api/businesses/{id}/reactivate`** — para revertir un soft delete; no aparece mencionado en AGENTS.md.
3. **El detalle de la cadena de validaciones de `createAppointment`** — AGENTS.md sólo describe el módulo `appointment` a alto nivel; esa cadena vive sólo en el Javadoc.
4. **`validateNoEmployeeAbsence` (paso 12)** — añadido 2026-05-17 (commit `c0f5e21`). Cubre el hueco descrito en el audit previo (BUG-1). No aparece en AGENTS.md.
5. **Tabla de transiciones `VALID_TRANSITIONS`** — la sección 7 "Seguridad" de AGENTS.md no incluye la state machine; sólo está en `AppointmentValidator.java:66-70`.
6. **CHECK constraints SQL** — AGENTS.md no enumera los 14 `chk_*` del schema (`chk_appointment_interval`, `chk_block_target`, `chk_bh_times_logic`, …). Sólo aparecen en `docs/schema_v20.sql`.
7. ~~**`uq_business_hours_day`** — UNIQUE compuesto en `business_hours(id_business, day_of_week)` añadido 2026-05-17 (commit `fac0760`). No aparece en AGENTS.md.~~ **Quitado 2026-05-23 (P1-negocio)** para soportar turno partido del negocio; la no-superposición se valida en `BusinessHourService` con overlap, igual que `EmployeeScheduleService`.
8. **Lock pesimista `SELECT … FOR UPDATE`** en `MembershipRepository.findByIdAndBusinessIdForUpdate` y `BoothRepository.findByIdAndBusinessIdForUpdate` — AGENTS.md no lo menciona; previene TOCTOU entre `validateNoOverlap` y el `INSERT` final.
9. **`AppointmentResponse.from(Appointment, List<BookedService>)`** — la firma actual ya NO depende de repos. AGENTS.md sección 8 mencionaba este refactor como "Pendiente" (corregido en commit `1f50810`).
10. **Validación de elementos de `serviceIds`** — `List<@NotNull @Positive Long>` añadido 2026-05-17 (commit `7c6d7f7`). No documentado en AGENTS.md.
11. **17 entidades JPA** (vs 16 que dice AGENTS.md sección 5; `PasswordResetToken` faltaba). Corregido en commit `1f50810`.
12. **Geocoding best-effort sin clave** — `GeocodingService` requiere `country` + (`city` o `postalCode`) para llamar a Nominatim; AGENTS.md sólo dice "geocoding (Nominatim) cableado".
13. **`isPaid`** — flag de pago en `appointments`. AGENTS.md menciona el endpoint pero no la columna ni que `updated_at` se rellena automáticamente en `@PreUpdate`.
14. **`PATCH .../payment` y `PATCH .../status` ambos sin `@PreAuthorize`** — decisión consciente que AGENTS.md no detalla por endpoint (sólo dice "operativa diaria, ambos roles" para `ClientController` y `AppointmentController`).
15. **`AvailabilityController` `@NotEmpty` en query param `serviceIds`** — la validación a nivel de query no aparece en AGENTS.md.
