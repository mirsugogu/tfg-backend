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

/** Gestion de citas (operativa diaria del negocio). */
@RestController
@RequestMapping("/api/businesses/{businessId}/appointments")
@RequiredArgsConstructor
@Validated
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** Crea una cita. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@PathVariable @Positive Long businessId,
                                                 @Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentService.createAppointment(businessId, request);
    }

    /** Lista citas con filtros opcionales. */
    @GetMapping
    public Page<AppointmentResponse> searchAppointments(
            @PathVariable @Positive Long businessId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Positive Long membershipId,
            Pageable pageable) {
        return appointmentService.searchAppointments(businessId, from, to, membershipId, pageable);
    }

    /** Obtiene el detalle de una cita. */
    @GetMapping("/{id}")
    public AppointmentResponse getAppointmentById(@PathVariable @Positive Long businessId,
                                                  @PathVariable @Positive Long id) {
        return appointmentService.getAppointmentById(businessId, id);
    }

    /** Edita o reagenda una cita. */
    @PutMapping("/{id}")
    public AppointmentResponse updateAppointment(@PathVariable @Positive Long businessId,
                                                 @PathVariable @Positive Long id,
                                                 @Valid @RequestBody UpdateAppointmentRequest request) {
        return appointmentService.updateAppointment(businessId, id, request);
    }

    /** Cambia el estado de una cita. */
    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable @Positive Long businessId,
                                            @PathVariable @Positive Long id,
                                            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateAppointmentStatus(businessId, id, request);
    }

    /** Actualiza el estado de pago de una cita. */
    @PatchMapping("/{id}/payment")
    public AppointmentResponse markPayment(@PathVariable @Positive Long businessId,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody UpdatePaymentRequest request) {
        return appointmentService.markPayment(businessId, id, request);
    }
}
