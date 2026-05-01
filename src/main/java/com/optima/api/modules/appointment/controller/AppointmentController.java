package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.request.CreateAppointmentRequest;
import com.optima.api.modules.appointment.dto.request.UpdateAppointmentStatusRequest;
import com.optima.api.modules.appointment.dto.response.AppointmentResponse;
import com.optima.api.modules.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Crea una nueva cita con sus servicios asociados.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse response = appointmentService.createAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todas las citas de un negocio.
     */
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByBusiness(
            @PathVariable Long businessId) {
        List<AppointmentResponse> appointments = appointmentService
                .getAppointmentsByBusiness(businessId);
        return ResponseEntity.ok(appointments);
    }

    /**
     * Busca una cita por ID dentro de un negocio.
     */
    @GetMapping("/{id}/business/{businessId}")
    public ResponseEntity<AppointmentResponse> getAppointmentById(
            @PathVariable Long id,
            @PathVariable Long businessId) {
        AppointmentResponse appointment = appointmentService
                .getAppointmentById(id, businessId);
        return ResponseEntity.ok(appointment);
    }

    /**
     * Cambia el estado de una cita.
     * Usa PATCH porque modifica solo un campo, no la cita entera.
     */
    @PatchMapping("/{id}/business/{businessId}/status")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @PathVariable Long id,
            @PathVariable Long businessId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        AppointmentResponse response = appointmentService
                .updateAppointmentStatus(id, businessId, request);
        return ResponseEntity.ok(response);
    }
}