package com.optima.api.common.geocoding;

import java.math.BigDecimal;

/**
 * Pareja inmutable de coordenadas geograficas (latitud, longitud).
 * Devuelta por {@link GeocodingService} cuando una direccion se resuelve.
 *
 * COMUNICACION:
 * - La construye: GeocodingService.geocode() al parsear la respuesta
 *   de Nominatim.
 * - La consume: BusinessService.create() y .update() via ifPresent(),
 *   asignando latitude y longitude a la entidad Business.
 *
 * Es un record: inmutable, accessors generados (latitude(), longitude()),
 * equals/hashCode/toString automaticos.
 *
 * BigDecimal en lugar de double: precision exacta para coordenadas (las
 * BD guardan DECIMAL(10,8) y DECIMAL(11,8), evitando perdida de precision
 * por aritmetica binaria).
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
