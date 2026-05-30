package com.optima.api.modules.client.dto.response;

import com.optima.api.modules.client.model.Client;

import java.time.LocalDateTime;

/**
 * respuesta con los datos publicos de un cliente
 */
public record ClientResponse(
        Long id,
        Long businessId,
        String fullName,
        String email,
        String phone,
        String notes,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(),
                c.getBusiness().getId(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getNotes(),
                c.getIsActive(),
                c.getCreatedAt(),
                c.getDeactivatedAt()
        );
    }
}
