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
 * BusinessAbsencesController - Vista agregada de las ausencias del negocio
 * (todas las memberships) que solapan con un rango de fechas. Complementa al
 * EmployeeAbsenceController (CRUD por empleado) sin sustituirlo.
 *
 * COMUNICACION:
 * - Recibe: GET /api/businesses/{businessId}/absences?from=&to=. Requiere
 *   JWT (TenantGuardFilter garantiza el businessId).
 * - Llama a: EmployeeAbsenceService.listByBusinessAndRange.
 * - Devuelve: List<EmployeeAbsenceResponse>.
 *
 * Permisos: sin @PreAuthorize. Cualquier autenticado del negocio (ADMIN o
 * EMPLOYEE) ve las ausencias para que el calendario pueda renderizarlas.
 *
 * Por que un controller dedicado y no extender EmployeeAbsenceController:
 * el path de aquel es /users/{userId}/absences (siempre con empleado).
 * Este es la vista del negocio entero; tener su propio mapping mantiene
 * la convencion path = recurso jerarquico, sin trucos opcionales.
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/absences")
@RequiredArgsConstructor
@Validated
public class BusinessAbsencesController {

    private final EmployeeAbsenceService absenceService;

    /**
     * GET /api/businesses/{businessId}/absences?from=YYYY-MM-DD&to=YYYY-MM-DD
     *
     * Devuelve las ausencias del negocio que solapan con el rango (from
     * inclusive a 00:00, to exclusivo al inicio del dia siguiente). Sin
     * paginar (decenas por negocio en la practica).
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
