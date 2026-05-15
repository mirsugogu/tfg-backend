package com.optima.api.modules.business.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UpdateBusinessRequest - DTO de entrada para PUT /api/businesses/{id}.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson, lo valida @Valid en BusinessController.update.
 * - Lo consume BusinessService.update (re-geocodifica con la direccion
 *   nueva y persiste).
 *
 * Validaciones:
 *   name                @NotBlank, max 150 chars.
 *   email               @NotBlank, @Email (formato), max 150 chars.
 *   phone               opcional, max 20 chars.
 *   address             opcional, max 255 chars.
 *   city                opcional, max 100 chars.
 *   state               opcional, max 100 chars.
 *   country             opcional, max 100 chars.
 *   postalCode          opcional, max 20 chars.
 *   appointmentInterval opcional (mantiene el actual si null).
 *
 * Decision: el slug NO se actualiza aqui. Es inmutable una vez creado
 * el negocio porque forma parte de URLs publicas y cambiarlo romperia
 * bookmarks externos. Este es el unico campo del Create que NO esta
 * en el Update.
 *
 * Notas semanticas (validadas en el service):
 *   - El email es UNIQUE GLOBAL; si cambia, se revalida unicidad.
 *   - city + postalCode (si vienen) re-resuelven lat/lng via Nominatim
 *     (best-effort).
 *   - appointmentInterval solo admite 15, 30, 45 o 60.
 */
public record UpdateBusinessRequest(

        @NotBlank(message = "El nombre del negocio es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede superar los 150 caracteres")
        String email,

        @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
        String phone,

        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String address,

        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String city,

        @Size(max = 100, message = "El estado/provincia no puede superar los 100 caracteres")
        String state,

        @Size(max = 100, message = "El país no puede superar los 100 caracteres")
        String country,

        @Size(max = 20, message = "El código postal no puede superar los 20 caracteres")
        String postalCode,

        Integer appointmentInterval
) {}
