package com.optima.api.common.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/** Convierte direcciones en coordenadas sin bloquear si la API externa falla. */
@Slf4j
@Service
public class GeocodingService {

    private static final String NOMINATIM_BASE = "https://nominatim.openstreetmap.org";

    private final RestClient restClient;
    private final String userAgent;

    public GeocodingService(
            @Value("${app.geocoding.user-agent}") String userAgent,
            @Value("${app.geocoding.timeout-ms:5000}") long timeoutMs) {
        this.userAgent = userAgent;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(NOMINATIM_BASE)
                .requestFactory(factory)
                .build();
    }

    /**
     * Intenta resolver una direccion si hay datos minimos suficientes.
     */
    public Optional<Coordinates> geocode(String addressLine, String city,
                                          String postalCode, String country) {
        if (country == null || country.isBlank()) {
            return Optional.empty();
        }
        boolean hasCity = city != null && !city.isBlank();
        boolean hasPostalCode = postalCode != null && !postalCode.isBlank();
        if (!hasCity && !hasPostalCode) {
            return Optional.empty();
        }

        String query = buildQuery(addressLine, city, postalCode, country);

        try {
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
                log.warn("Geocoding sin resultados para query='{}'", query);
                return Optional.empty();
            }

            JsonNode first = body.get(0);
            String latStr = first.path("lat").asText(null);
            String lonStr = first.path("lon").asText(null);

            if (latStr == null || lonStr == null) {
                log.warn("Geocoding respuesta sin lat/lon para query='{}'", query);
                return Optional.empty();
            }

            return Optional.of(new Coordinates(
                    new BigDecimal(latStr),
                    new BigDecimal(lonStr)));
        } catch (Exception ex) {
            log.warn("Geocoding fallido para query='{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

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
