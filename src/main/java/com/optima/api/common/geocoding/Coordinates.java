package com.optima.api.common.geocoding;

import java.math.BigDecimal;

/**
 * Coordenadas geograficas devueltas por el geocoding.
 *
 * Se usa BigDecimal para conservar la precision que despues se guarda en
 * la base de datos.
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
