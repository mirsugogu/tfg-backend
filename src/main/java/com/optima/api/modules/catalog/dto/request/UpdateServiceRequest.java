package com.optima.api.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * UpdateServiceRequest - DTO de entrada para actualizar un servicio existente.
 * No incluye businessId: el negocio se toma del path
 * y NO se permite mover el servicio entre negocios.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en BusinessServiceController.
 * - Lo consume BusinessServiceService.updateService.
 *
 * Mismas validaciones que CreateServiceRequest. La categoria y el impuesto
 * SI pueden cambiar (siempre dentro del mismo negocio): el service revalida
 * cross-tenant en cada update.
 */
public record UpdateServiceRequest(

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
