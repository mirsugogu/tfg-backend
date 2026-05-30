package com.optima.api.common.geocoding;

import java.math.BigDecimal;

/**
 * guarda latitud y longitud
 * usa bigdecimal para no perder precision
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    // limites validos de latitud y longitud
    private static final BigDecimal MIN_LAT = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LON = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LON = BigDecimal.valueOf(180);

    /** valida que las coordenadas esten en rango */
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
