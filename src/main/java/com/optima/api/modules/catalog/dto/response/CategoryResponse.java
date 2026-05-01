package com.optima.api.modules.catalog.dto.response;

public record CategoryResponse(
        Long idCategory,
        String name,
        Boolean isActive
) {}