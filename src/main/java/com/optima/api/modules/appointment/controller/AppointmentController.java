package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AppointmentController - Gestion de citas (operativa diaria del negocio).
 * Recurso anidado bajo /api/businesses/{businessId}/appointments.
 *
 * COMUNICACION:
 * - Recibe: CRUD HTTP de citas. Requiere JWT (todos los endpoints).
 * - Le precede: JwtAuthFilter + TenantGuardFilter (cross-tenant via path).
 * - Llama a: AppointmentService (delega TODA la logica, incluidas las
 *   12 validaciones encadenadas en createAppointment).
 * - Devuelve: AppointmentResponse (incluye lista de bookedServices con
 *   precios e impuestos congelados).
 *
 * Permisos:
 *   NINGUN endpoint tiene @PreAuthorize. Es INTENCIONAL: en el modelo
 *   "Scenario A", AMBOS roles ADMIN y EMPLOYEE pueden gestionar citas
 *   (es operativa diaria, no configuracion). Si solo el ADMIN pudiera
 *   agendar, los empleados no podrian gestionar a sus clientes.
 *
 * Endpoints:
 *   POST   .../appointments              crear cita.
 *   GET    .../appointments              listar todas las del negocio.
 *   GET    .../appointments/{id}         detalle.
 *   PATCH  .../appointments/{id}/status  cambiar estado.
 *
 * NO hay PUT ni DELETE: una cita una vez creada solo cambia de estado
 * (la maquina de estados vive en AppointmentValidator.validateStatusTransition).
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * POST /api/businesses/{businessId}/appointments - Crea una cita.
     * El businessId se toma del path; el body trae cliente, empleado,
     * servicios y horario.
     *
     * AppointmentService aplica ~12 validaciones encadenadas: negocio
     * existe, cliente/empleado/servicios activos del negocio, intervalo
     * respetado, empleado trabaja ese dia, no cruza medianoche, no
     * solapa con otra cita activa, estado PENDING existe.
     * Estado inicial: PENDING. Devuelve la cita creada con bookedServices.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@PathVariable Long businessId,
                                                 @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.createAppointment(businessId, request);
    }

    /**
     * GET /api/businesses/{businessId}/appointments - Lista todas las citas
     * del negocio (sin filtros aun: roadmap futuro filtrar por
     * fecha/empleado/estado).
     */
    @GetMapping
    public List<AppointmentResponse> getAppointmentsByBusiness(@PathVariable Long businessId) {
        return appointmentService.getAppointmentsByBusiness(businessId);
    }

    /**
     * GET /api/businesses/{businessId}/appointments/{id} - Detalle de cita.
     * Cross-tenant safe: si la cita no esta en este businessId -> 404.
     */
    @GetMapping("/{id}")
    public AppointmentResponse getAppointmentById(@PathVariable Long businessId,
                                                  @PathVariable Long id) {
        return appointmentService.getAppointmentById(businessId, id);
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
    public AppointmentResponse updateStatus(@PathVariable Long businessId,
                                            @PathVariable Long id,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateAppointmentStatus(businessId, id, request);
    }
}
