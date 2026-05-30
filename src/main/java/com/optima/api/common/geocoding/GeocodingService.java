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
 * Servicio que convierte una direccion de texto en coordenadas GPS
 * Usa la API gratuita de OpenStreetMap se llama Nominatim, si falla no pasa nada, simplemente no guarda coordenadas
 */
@Service
public class GeocodingService {

    // url base de la API de Nominatim, que es gratis y no necesita API key
    private static final String NOMINATIM_BASE = "https://nominatim.openstreetmap.org";

    private final RestClient restClient;
    private final String userAgent;

    /**
     * Configuramos el cliente HTTP con tiempo de espera para que no se quede colgado si Nominatim tarda mucho
     */
    public GeocodingService(
            @Value("${app.geocoding.user-agent}") String userAgent,
            @Value("${app.geocoding.timeout-ms:5000}") long timeoutMs) {
        this.userAgent = userAgent;

        // configuramos los timeouts de conexion y lectura
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(NOMINATIM_BASE)
                .requestFactory(factory)
                .build();
    }

    /**
     * Intenta convertir una direccion en coordenadas, si no puede devuelve vacio
     */
    public Optional<Coordinates> geocode(String addressLine, String city, String postalCode, String country) {
        // necesitamos al menos el pais para buscar algo
        if (country == null || country.isBlank()) {
            return Optional.empty();
        }
        // y ademas necesitamos ciudad o codigo postal
        boolean hasCity = city != null && !city.isBlank();
        boolean hasPostalCode = postalCode != null && !postalCode.isBlank();
        if (!hasCity && !hasPostalCode) {
            return Optional.empty();
        }

        String query = buildQuery(addressLine, city, postalCode, country);

        try {
            // llamamos a Nominatim y pedimos solo 1 resultado en formato JSON
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

            // sacamos lat y lon del primer resultado
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
            // si Nominatim falla o no responde, no pasa nada, el negocio se crea igual sin coordenadas
            return Optional.empty();
        }
    }

    /**
     * Arma el texto de busqueda juntando las partes de la direccion separadas por comas
     */
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
