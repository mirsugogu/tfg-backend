package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTaxRequest(
    @NotBlank @Size(max = 50) String name,
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal percentage
) {}
