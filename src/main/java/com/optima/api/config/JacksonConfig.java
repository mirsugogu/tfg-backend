package com.optima.api.config;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ajusta las fechas del servicio */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        // registra el deserializador de fechas sin zona
        return builder -> builder.deserializers(new StrictLocalDateTimeDeserializer());
    }
}
