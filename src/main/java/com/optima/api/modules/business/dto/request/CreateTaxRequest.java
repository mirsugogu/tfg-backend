package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * CreateTaxRequest - DTO de entrada para crear un impuesto.
 * El businessId viene del path, no del body.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en TaxController.create.
 * - Lo consume TaxService.create.
 *
 * Validaciones:
 *   name        @NotBlank, max 50 chars.
 *   percentage  @NotNull, rango 0.00..100.00 (BigDecimal para precision).
 */
public record CreateTaxRequest(

        @NotBlank(message = "El nombre del impuesto es obligatorio")
        @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
        String name,

        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "0.00", message = "El porcentaje no puede ser negativo")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede superar 100")
        BigDecimal percentage
) {}
