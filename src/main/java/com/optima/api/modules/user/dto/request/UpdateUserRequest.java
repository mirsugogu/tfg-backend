package com.optima.api.modules.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * UpdateUserRequest - DTO de entrada para PUT /api/businesses/{businessId}/users/{id};
 * el admin gestiona la membership de un empleado en SU negocio.
 *
 * [v16 membership] Gobierna solo la "pieza local" de la membership: el rol
 * que el empleado tiene en ESTE negocio y el color con el que se pintan sus
 * citas en el calendario de este negocio. Los datos globales del usuario
 * (fullName, email, phone) se actualizan desde PUT /api/me, donde el dueno
 * de la identidad es quien decide.
 *
 * No incluye businessId: viene del path.
 * No incluye isActive: para desactivar existe DELETE /api/businesses/{id}/users/{id}.
 *
 * Es un PUT (reemplazo del estado local), asi que el cliente debe enviar
 * SIEMPRE roleId y color con sus valores actuales; un color nulo deja al
 * empleado sin color asignado (el frontend usara entonces uno automatico).
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON de PUT .../users/{id}.
 * - Lo valida @Valid en UserController.update.
 * - Lo consume UserService.update.
 *
 * Validaciones:
 *   roleId  @NotNull, @Positive.
 *   color   opcional; si viene, debe ser uno de la paleta fija.
 */
public record UpdateUserRequest(

        @NotNull(message = "El ID del rol es obligatorio")
        @Positive(message = "El ID del rol debe ser positivo")
        Long roleId,

        @Pattern(
                regexp = "cyan|amber|emerald|indigo|pink|sky|violet|teal",
                message = "El color debe ser uno de la paleta permitida"
        )
        String color
) {}
