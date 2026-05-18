package com.optima.api.config;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JacksonConfig - Ajustes globales del ObjectMapper de Spring Boot.
 *
 * Registra {@link StrictLocalDateTimeDeserializer} para que cualquier
 * campo {@code LocalDateTime} de los DTOs request rechace cadenas con
 * sufijo "Z" o offset explicito. Sin esto, Jackson acepta silenciosamente
 * la zona horaria y la descarta — provocando que el frontend crea que
 * envia UTC mientras el backend guarda hora local.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        return builder -> builder.deserializers(new StrictLocalDateTimeDeserializer());
    }
}
