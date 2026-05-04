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

@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Crea una nueva cita con sus servicios asociados.
     * El businessId se toma del path; el body trae cliente, empleado,
     * servicios y horario.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@PathVariable Long businessId,
                                                 @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.createAppointment(businessId, request);
    }

    /**
     * Lista todas las citas de un negocio.
     */
    @GetMapping
    public List<AppointmentResponse> getAppointmentsByBusiness(@PathVariable Long businessId) {
        return appointmentService.getAppointmentsByBusiness(businessId);
    }

    /**
     * Busca una cita por ID dentro de un negocio.
     */
    @GetMapping("/{id}")
    public AppointmentResponse getAppointmentById(@PathVariable Long businessId,
                                                  @PathVariable Long id) {
        return appointmentService.getAppointmentById(id, businessId);
    }

    /**
     * Cambia el estado de una cita.
     * Usa PATCH porque modifica solo un campo, no la cita entera.
     */
    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable Long businessId,
                                            @PathVariable Long id,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateAppointmentStatus(id, businessId, request);
    }
}
