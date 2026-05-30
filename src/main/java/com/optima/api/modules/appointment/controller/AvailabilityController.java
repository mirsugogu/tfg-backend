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

/** consulta huecos libres para una fecha y unos servicios */
@RestController
@RequestMapping("/api/businesses/{businessId}/availability")
@RequiredArgsConstructor
@Validated
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /** calcula los huecos */
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

            @RequestParam(required = false) @Positive Long boothId,

            @RequestParam(required = false) @Positive Long excludeAppointmentId) {

        return availabilityService.getAvailability(
                businessId, date, serviceIds, membershipId, boothId, excludeAppointmentId);
    }
}
