package com.optima.api.modules.client.dto.response;

import com.optima.api.modules.client.model.Client;

/**
 * DTO de salida para representar un cliente.
 */
public record ClientResponse(
        Long id,
        Long businessId,
        String fullName,
        String email,
        String phone,
        String notes,
        Boolean isActive
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(),
                c.getBusiness().getId(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getNotes(),
                c.getIsActive()
        );
    }
}
