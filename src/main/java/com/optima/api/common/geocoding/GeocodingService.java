package com.optima.api.common.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * intenta obtener coordenadas a partir de una direccion
 * si falla devuelve vacio y sigue el flujo
 */
@Service
public class GeocodingService {

    // se usa nominatim como servicio de geocoding
    private static final String NOMINATIM_BASE = "https://nominatim.openstreetmap.org";

    private final RestClient restClient;
    private final String userAgent;

    /** crea el cliente con tiempo de espera */
    public GeocodingService(
            @Value("${app.geocoding.user-agent}") String userAgent,
            @Value("${app.geocoding.timeout-ms:5000}") long timeoutMs) {
        this.userAgent = userAgent;

        // se fijan tiempos de conexion y lectura
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(NOMINATIM_BASE)
                .requestFactory(factory)
                .build();
    }

    /** intenta obtener coordenadas y si no puede devuelve vacio */
    public Optional<Coordinates> geocode(String addressLine, String city, String postalCode, String country) {
        // sin pais la busqueda suele dar malos resultados
        if (country == null || country.isBlank()) {
            return Optional.empty();
        }
        // tambien se pide ciudad o codigo postal
        boolean hasCity = city != null && !city.isBlank();
        boolean hasPostalCode = postalCode != null && !postalCode.isBlank();
        if (!hasCity && !hasPostalCode) {
            return Optional.empty();
        }

        String query = buildQuery(addressLine, city, postalCode, country);

        try {
            // solo hace falta el primer resultado
            JsonNode body = restClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.isArray() || body.isEmpty()) {
                return Optional.empty();
            }

            // del primer resultado se leen lat y lon
            JsonNode first = body.get(0);
            String latStr = first.path("lat").asText(null);
            String lonStr = first.path("lon").asText(null);

            if (latStr == null || lonStr == null) {
                return Optional.empty();
            }

            return Optional.of(new Coordinates(
                    new BigDecimal(latStr),
                    new BigDecimal(lonStr)));
        } catch (Exception ignored) {
            // si falla se sigue sin coordenadas
            return Optional.empty();
        }
    }

    /** monta el texto de busqueda para el servicio */
    private String buildQuery(String addressLine, String city,
                               String postalCode, String country) {
        StringBuilder sb = new StringBuilder();
        if (addressLine != null && !addressLine.isBlank()) {
            sb.append(addressLine.trim()).append(", ");
        }
        if (postalCode != null && !postalCode.isBlank()) {
            sb.append(postalCode.trim()).append(' ');
        }
        if (city != null && !city.isBlank()) {
            sb.append(city.trim()).append(", ");
        }
        sb.append(country.trim());
        return sb.toString();
    }
}
