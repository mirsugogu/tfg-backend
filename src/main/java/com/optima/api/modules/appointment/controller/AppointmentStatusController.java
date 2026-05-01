package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.service.AppointmentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointment-statuses")
@RequiredArgsConstructor
public class AppointmentStatusController {

    private final AppointmentStatusService statusService;

    /**
     * Devuelve todos los estados de cita disponibles.
     */
    @GetMapping
    public ResponseEntity<List<AppointmentStatusResponse>> getAllStatuses() {
        List<AppointmentStatusResponse> statuses = statusService.getAllStatuses();
        return ResponseEntity.ok(statuses);
    }

    /**
     * Devuelve un estado de cita por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentStatusResponse> getStatusById(@PathVariable Long id) {
        AppointmentStatusResponse status = statusService.getStatusById(id);
        return ResponseEntity.ok(status);
    }
}