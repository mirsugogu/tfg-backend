package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * CreateServiceRequest - DTO de entrada para crear un servicio.
 * El businessId viene del path, no del body.
 * La categoría y el impuesto se validan cross-tenant en el servicio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en BusinessServiceController.
 * - Lo consume BusinessServiceService.createService.
 *
 * Validaciones:
 *   categoryId        @NotNull, @Positive (cross-tenant validado en el service).
 *   taxId             @NotNull, @Positive (cross-tenant validado en el service).
 *   name              @NotBlank, max 150.
 *   description       opcional, sin validación.
 *   price             @NotNull, >= 0 (BigDecimal para precision monetaria).
 *   durationMinutes   @NotNull, >= 1 minuto.
 */
public record CreateServiceRequest(

        @NotNull(message = "El ID de la categoría es obligatorio")
        @Positive(message = "El ID de la categoría debe ser positivo")
        Long categoryId,

        @NotNull(message = "El ID del impuesto es obligatorio")
        @Positive(message = "El ID del impuesto debe ser positivo")
        Long taxId,

        @NotBlank(message = "El nombre del servicio no puede estar vacío")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String name,

        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        BigDecimal price,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser de al menos 1 minuto")
        Integer durationMinutes
) {}
