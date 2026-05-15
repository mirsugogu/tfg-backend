package com.optima.api.modules.client.dto.response;

import com.optima.api.modules.client.model.Client;

import java.time.LocalDateTime;

/**
 * ClientResponse - DTO de salida para representar un cliente.
 *
 * COMUNICACION:
 * - Lo construye ClientResponse.from(Client) en ClientService.
 * - Lo serializa Jackson a JSON en las respuestas de ClientController.
 *
 * Expone businessId para que el frontend confirme el tenant. Incluye
 * notes (texto largo, suele tener informacion sensible: alergias,
 * preferencias) y los campos de soft delete.
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
