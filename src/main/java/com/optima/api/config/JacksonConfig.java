package com.optima.api.config;

import com.optima.api.common.json.StrictLocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** aqui dejamos jackson como lo necesita el proyecto */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictLocalDateTimeCustomizer() {
        // ojo con esto que si se quita vuelven a entrar fechas raras
        return builder -> builder.deserializers(new StrictLocalDateTimeDeserializer());
    }
}
