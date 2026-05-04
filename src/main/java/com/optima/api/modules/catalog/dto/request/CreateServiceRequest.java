package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de entrada para crear un servicio.
 * El {@code businessId} viene del path, no del body.
 * La categoría y el impuesto se validan cross-tenant en el servicio.
 */
public record CreateServiceRequest(

        @NotNull(message = "El ID de la categoría es obligatorio")
        Long categoryId,

        @NotNull(message = "El ID del impuesto es obligatorio")
        Long taxId,

        @NotBlank(message = "El nombre del servicio no puede estar vacío")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String name,

        // La descripción es opcional, por lo que no le ponemos @NotBlank ni @NotNull.
        // Si el cliente no la envía, simplemente será 'null'.
        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        BigDecimal price,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser de al menos 1 minuto")
        Integer durationMinutes
) {}
