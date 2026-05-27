package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.service.AppointmentStatusService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Catalogo PUBLICO de estados de cita. */
@RestController
@RequestMapping("/api/appointment-statuses")
@RequiredArgsConstructor
@Validated
public class AppointmentStatusController {

    private final AppointmentStatusService statusService;

    /**
     * Devuelve todos los estados de cita disponibles.
     */
    @GetMapping
    public List<AppointmentStatusResponse> listAll() {
        return statusService.listAll();
    }

    /**
     * Devuelve un estado de cita por su ID.
     */
    @GetMapping("/{id}")
    public AppointmentStatusResponse getStatusById(@PathVariable @Positive Long id) {
        return statusService.getStatusById(id);
    }
}
