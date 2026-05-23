package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.optima.api.modules.appointment.dto.request.UpdatePaymentRequest;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * AppointmentController - Gestion de citas (operativa diaria del negocio).
 * Recurso anidado bajo /api/businesses/{businessId}/appointments.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP de citas. Requiere JWT (todos los endpoints).
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via path).
 * - Llama a: AppointmentService (delega TODA la logica, incluidas las
 *   validaciones encadenadas de createAppointment).
 * - Devuelve: AppointmentResponse (incluye lista de bookedServices con
 *   precios e impuestos congelados).
 *
 * Permisos:
 *   NINGUN endpoint tiene @PreAuthorize. Es INTENCIONAL: AMBOS roles
 *   ADMIN y EMPLOYEE pueden gestionar citas (operativa diaria, no
 *   configuracion). Si solo el ADMIN pudiera agendar, los empleados
 *   no podrian gestionar a sus clientes.
 *
 * Endpoints:
 *   POST   .../appointments              crear cita.
 *   GET    .../appointments              busqueda paginada con filtros
 *                                          (from, to, membershipId).
 *   GET    .../appointments/{id}         detalle.
 *   PUT    .../appointments/{id}         editar (reagendar) — P9.
 *   PATCH  .../appointments/{id}/status  cambiar estado.
 *   PATCH  .../appointments/{id}/payment marcar pagada / no pagada.
 *
 * NO hay DELETE: las citas no se borran; pasan a CANCELLED via
 * PATCH /status. El estado cumple la funcion de soft delete.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
@Validated
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * POST /api/businesses/{businessId}/appointments - Crea una cita.
     * El businessId se toma del path; el body trae cliente, empleado,
     * servicios, cabina opcional y horario.
     *
     * AppointmentService aplica una cadena de validaciones: negocio
     * existe, cliente/empleado/servicios activos del negocio, intervalo
     * respetado, empleado trabaja ese dia, no cruza medianoche, no
     * solapa con otra cita activa, cabina (si la lleva) activa y libre,
     * no choca con bloqueos de agenda, estado PENDING existe.
     * Estado inicial: PENDING. Devuelve la cita creada con bookedServices.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@PathVariable @Positive Long businessId,
                                                 @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.createAppointment(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/appointments - Busqueda paginada de
     * citas con filtros opcionales.
     *
     * Query params:
     *   ?from=YYYY-MM-DD          fecha de inicio del rango (inclusive).
     *   ?to=YYYY-MM-DD            fecha de fin del rango (inclusive, dia entero).
     *   ?membershipId=N             filtrar por empleado concreto.
     *   ?page=N&size=M            paginacion (default size=20, max=100).
     *   ?sort=field,asc|desc      ordenacion.
     *
     * Cualquiera de los filtros puede omitirse. El service convierte los
     * LocalDate a LocalDateTime con semantica inclusiva en ambos extremos.
     */
    @GetMapping
    public Page<AppointmentResponse> searchAppointments(
            @PathVariable @Positive Long businessId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Positive Long membershipId,
            Pageable pageable) {
        return appointmentService.searchAppointments(businessId, from, to, membershipId, pageable);
    }

    /**
     * GET /api/businesses/{businessId}/appointments/{id} - Detalle de cita.
     * Cross-tenant safe: si la cita no esta en este businessId -> 404.
     */
    @GetMapping("/{id}")
    public AppointmentResponse getAppointmentById(@PathVariable @Positive Long businessId,
                                                  @PathVariable @Positive Long id) {
        return appointmentService.getAppointmentById(businessId, id);
    }

    /**
     * PUT /api/businesses/{businessId}/appointments/{id} - Edita / reagenda
     * una cita existente (P9 del BACKLOG): permite cambiar empleado, cabina,
     * fecha-hora, servicios y notas. El cliente y el estado/pago no se
     * tocan aqui (estado y pago tienen sus propios PATCH).
     *
     * Reaplica toda la cadena de validacion de POST (horario del negocio,
     * solape del empleado/cabina, ausencias, bloqueos, intervalo, cabina
     * activa) excluyendo la propia cita del check de solape. Re-congela
     * los precios de los servicios al precio actual del catalogo.
     *
     * 400 si la cita esta en estado terminal (COMPLETED / CANCELLED /
     * NO_SHOW): no se reagendan citas finalizadas.
     */
    @PutMapping("/{id}")
    public AppointmentResponse updateAppointment(@PathVariable @Positive Long businessId,
                                                 @PathVariable @Positive Long id,
                                                 @Valid @RequestBody UpdateAppointmentRequest request) {
        return appointmentService.updateAppointment(businessId, id, request);
    }

    /**
     * PATCH /api/businesses/{businessId}/appointments/{id}/status - Cambia
     * el estado de una cita. Usa PATCH porque modifica un solo campo.
     *
     * Maquina de estados (AppointmentValidator.validateStatusTransition):
     *   PENDING      -> CONFIRMED, CANCELLED
     *   CONFIRMED    -> IN_PROGRESS, CANCELLED, NO_SHOW
     *   IN_PROGRESS  -> COMPLETED, CANCELLED
     *   COMPLETED, CANCELLED, NO_SHOW  son finales (no transicionan).
     *
     * Body: {"statusName": "CONFIRMED"}.
     */
    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long id,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateAppointmentStatus(businessId, id, request);
    }

    /**
     * PATCH /api/businesses/{businessId}/appointments/{id}/payment - Marca
     * la cita como pagada / no pagada. Operacion independiente del flujo
     * de estados (separa "está pagada" de "está completada").
     *
     * Body: {"isPaid": true} o {"isPaid": false}.
     * Sin @PreAuthorize: cualquier autenticado del negocio (ADMIN o
     * EMPLOYEE) puede marcar pagos al cobrar al cliente en recepcion.
     */
    @PatchMapping("/{id}/payment")
    public AppointmentResponse markPayment(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody UpdatePaymentRequest request) {
        return appointmentService.markPayment(businessId, id, request);
    }
}
