package com.optima.api.modules.catalog.dto.response;

import java.math.BigDecimal;

public record ServiceResponse(
        Long idService,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        Boolean isActive,

        // Devolvemos el ID de la categoría a la que pertenece
        // por si el Frontend necesita agruparlos visualmente.
        Long categoryId
) {}