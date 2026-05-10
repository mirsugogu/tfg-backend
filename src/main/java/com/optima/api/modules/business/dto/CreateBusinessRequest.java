package com.optima.api.modules.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CreateBusinessRequest - DTO de entrada para crear un negocio.
 *
 * COMUNICACION:
 * - Lo deserializa Jackson desde el body JSON de POST /api/businesses.
 * - Lo valida @Valid en BusinessController.create.
 * - Lo consume BusinessService.create.
 *
 * Campos:
 *   name              @NotBlank, max 150.
 *   slug              @NotBlank, max 150 (ademas se valida formato y
 *                     unicidad en el service).
 *   email             @NotBlank, @Email, max 150 (unico globalmente).
 *   phone, address    opcionales, validacion solo de longitud.
 *   city, state,      datos de direccion. Si city o postalCode estan
 *   country, postal   presentes, GeocodingService los usa para resolver
 *                     latitude/longitude via Nominatim.
 *   appointmentInterval opcional, default 30. Solo 15/30/45/60.
 */
public record CreateBusinessRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 150) String slug,
    @NotBlank @Email @Size(max = 150) String email,
    @Size(max = 20) String phone,
    @Size(max = 255) String address,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Size(max = 100) String country,
    @Size(max = 20)  String postalCode,
    Integer appointmentInterval
) {}
