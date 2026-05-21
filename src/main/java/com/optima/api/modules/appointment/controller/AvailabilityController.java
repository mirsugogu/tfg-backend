package com.optima.api.modules.appointment.controller;

import com.optima.api.modules.appointment.dto.response.AvailabilityResponse;
import com.optima.api.modules.appointment.service.AvailabilityService;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
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
 * AvailabilityController - Consulta de huecos libres del negocio para una
 * fecha y unos servicios elegidos.
 *
 * Endpoint:
 *   GET /api/businesses/{businessId}/availability
 *        ?date=YYYY-MM-DD
 *        &serviceIds=1,2,3
 *        [&membershipId=N]
 *        [&boothId=N]
 *
 * COMUNICACION:
 * - Recibe: GET HTTP con query params. Requiere JWT (TenantGuardFilter
 *   garantiza que el businessId del path coincide con el del token).
 * - Llama a: AvailabilityService.getAvailability().
 * - Devuelve: AvailabilityResponse con la lista plana de slots.
 *
 * Permisos:
 *   GET sin @PreAuthorize - cualquier autenticado del negocio puede
 *   consultar disponibilidad (es operativa de agendar citas).
 */
@RestController
@RequestMapping("/api/businesses/{businessId}/availability")
@RequiredArgsConstructor
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /**
     * GET /api/businesses/{businessId}/availability - Calcula los huecos
     * libres del negocio para una fecha y unos servicios concretos.
     *
     * El servicio aplica un algoritmo de 9 pasos: valida negocio, suma
     * duracion de los servicios, lee horario semanal, descarta dias
     * bloqueados (schedule_blocks), cruza horarios de empleados,
     * descuenta ausencias y citas activas, asigna primera cabina libre
     * y devuelve la lista plana de slots.
     *
     * Filtros opcionales:
     *   membershipId  restringe a un empleado concreto.
     *   boothId       restringe a una cabina concreta.
     */
    @GetMapping
    public AvailabilityResponse getAvailability(
            @PathVariable @Positive Long businessId,

            @RequestParam
            @FutureOrPresent(message = "La fecha no puede estar en el pasado")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @RequestParam
            @NotEmpty(message = "Debe indicar al menos un servicio")
            List<@NotNull(message = "ningún id puede ser nulo")
                 @Positive(message = "los ids deben ser positivos") Long> serviceIds,

            @RequestParam(required = false) @Positive Long membershipId,

            @RequestParam(required = false) @Positive Long boothId) {

        return availabilityService.getAvailability(
                businessId, date, serviceIds, membershipId, boothId);
    }
}
