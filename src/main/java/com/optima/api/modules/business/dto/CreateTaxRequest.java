package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de entrada para crear un impuesto.
 * El {@code businessId} viene del path, no del body.
 */
public record CreateTaxRequest(
    @NotBlank @Size(max = 50) String name,
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal percentage
) {}
