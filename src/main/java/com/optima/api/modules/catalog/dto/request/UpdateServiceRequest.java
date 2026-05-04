package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de entrada para actualizar un servicio existente.
 * No incluye {@code businessId}: el negocio se toma del path
 * y NO se permite mover el servicio entre negocios.
 */
public record UpdateServiceRequest(

        @NotNull(message = "El ID de la categoría es obligatorio")
        Long categoryId,

        @NotNull(message = "El ID del impuesto es obligatorio")
        Long taxId,

        @NotBlank(message = "El nombre del servicio no puede estar vacío")
        @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
        String name,

        // La descripción es opcional; si el cliente no la envía, será null.
        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
        BigDecimal price,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe ser de al menos 1 minuto")
        Integer durationMinutes
) {}
