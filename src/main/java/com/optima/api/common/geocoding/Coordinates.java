package com.optima.api.common.geocoding;

import java.math.BigDecimal;

/**
 * Pareja inmutable de coordenadas geograficas (latitud, longitud).
 * Devuelta por {@link GeocodingService} cuando una direccion se resuelve.
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
