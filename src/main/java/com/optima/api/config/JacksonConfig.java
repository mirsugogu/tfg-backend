package com.optima.api.config;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configura Jackson para aceptar solo fechas locales en la API. */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        return builder -> builder.deserializers(new StrictLocalDateTimeDeserializer());
    }
}
