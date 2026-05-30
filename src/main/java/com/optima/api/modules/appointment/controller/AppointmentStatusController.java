package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.response.AppointmentStatusResponse;
import com.optima.api.modules.appointment.service.AppointmentStatusService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** expone los estados de cita */
@RestController
@RequestMapping("/api/appointment-statuses")
@RequiredArgsConstructor
@Validated
public class AppointmentStatusController {

    private final AppointmentStatusService statusService;

    /** devuelve todos los estados disponibles */
    @GetMapping
    public List<AppointmentStatusResponse> listAll() {
        return statusService.listAll();
    }

    /** devuelve un estado por id */
    @GetMapping("/{id}")
    public AppointmentStatusResponse getStatusById(@PathVariable @Positive Long id) {
        return statusService.getStatusById(id);
    }
}
