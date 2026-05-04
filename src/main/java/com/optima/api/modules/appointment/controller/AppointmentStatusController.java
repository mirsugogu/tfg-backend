package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.service.AppointmentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointment-statuses")
@RequiredArgsConstructor
public class AppointmentStatusController {

    private final AppointmentStatusService statusService;

    /**
     * Devuelve todos los estados de cita disponibles.
     */
    @GetMapping
    public List<AppointmentStatusResponse> getAllStatuses() {
        return statusService.getAllStatuses();
    }

    /**
     * Devuelve un estado de cita por su ID.
     */
    @GetMapping("/{id}")
    public AppointmentStatusResponse getStatusById(@PathVariable Long id) {
        return statusService.getStatusById(id);
    }
}
