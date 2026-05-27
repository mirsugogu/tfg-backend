package com.optima.api.config;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura la lectura de fechas en los JSON de entrada.
 *
 * Las fechas con hora se tratan como hora local, por eso no se aceptan
 * valores con zona horaria.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        return builder -> builder.deserializers(new StrictLocalDateTimeDeserializer());
    }
}
