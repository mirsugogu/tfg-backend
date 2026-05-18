package com.optima.api.common.geocoding;

import java.math.BigDecimal;

/**
 * Pareja inmutable de coordenadas geograficas (latitud, longitud).
 * Devuelta por GeocodingService cuando una direccion se resuelve.
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
 *
 * El constructor compacto valida que los valores sean no-null y esten en
 * el rango geografico valido. Si Nominatim devuelve basura, salta
 * IllegalArgumentException; GeocodingService lo captura en su catch
 * generico y devuelve Optional.empty() (best-effort intacto).
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LAT = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LON = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LON = BigDecimal.valueOf(180);

    public Coordinates {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                "latitude y longitude no pueden ser null");
        }
        if (latitude.compareTo(MIN_LAT) < 0 || latitude.compareTo(MAX_LAT) > 0) {
            throw new IllegalArgumentException(
                "latitude fuera de rango [-90, 90]: " + latitude);
        }
        if (longitude.compareTo(MIN_LON) < 0 || longitude.compareTo(MAX_LON) > 0) {
            throw new IllegalArgumentException(
                "longitude fuera de rango [-180, 180]: " + longitude);
        }
    }
}
