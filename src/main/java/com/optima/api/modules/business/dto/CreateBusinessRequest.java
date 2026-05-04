package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBusinessRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 150) String slug,
    @NotBlank @Email @Size(max = 150) String email,
    @Size(max = 20) String phone,
    @Size(max = 255) String address,
    Integer appointmentInterval
) {}
