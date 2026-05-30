package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** datos para crear un impuesto */
public record CreateTaxRequest(

        @NotBlank(message = "El nombre del impuesto es obligatorio")
        @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
        String name,

        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "0.00", message = "El porcentaje no puede ser negativo")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede superar 100")
        BigDecimal percentage
) {}
