package com.optima.api.modules.user.controller;

import com.optima.api.modules.user.dto.response.EmployeeAbsenceResponse;
import com.optima.api.modules.user.service.EmployeeAbsenceService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador para consultar ausencias de todo el negocio.
 *
 * Complementa el CRUD por empleado y permite al calendario cargar todas
 * las ausencias de un rango.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/absences")
@RequiredArgsConstructor
@Validated
public class BusinessAbsencesController {

    private final EmployeeAbsenceService absenceService;

    /**
     * Devuelve las ausencias del negocio que solapan con el rango indicado.
     */
    @GetMapping
    public List<EmployeeAbsenceResponse> listByBusiness(
            @PathVariable @Positive Long businessId,

            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return absenceService.listByBusinessAndRange(
                businessId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        );
    }
}
