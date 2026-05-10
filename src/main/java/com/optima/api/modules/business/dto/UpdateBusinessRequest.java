package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateBusinessRequest - DTO de entrada para PUT /api/businesses/{id}.
 *
 * Mismo perfil que CreateBusinessRequest pero SIN slug: el slug es
 * inmutable una vez creado el negocio (forma parte de URLs publicas
 * y cambiarlo romperia bookmarks externos).
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en BusinessController.update.
 * - Lo consume BusinessService.update (re-geocodifica con la direccion
 *   nueva y persiste).
 */
public record UpdateBusinessRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Email @Size(max = 150) String email,
    @Size(max = 20) String phone,
    @Size(max = 255) String address,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Size(max = 100) String country,
    @Size(max = 20)  String postalCode,
    Integer appointmentInterval
) {}
