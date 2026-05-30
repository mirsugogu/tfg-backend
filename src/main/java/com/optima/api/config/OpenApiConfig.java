package com.optima.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** configura Swagger con el esquema JWT de autenticacion */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // este nombre se reutiliza en la configuracion de seguridad
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Optima API")
                        .version("0.0.1")
                        .description("Backend REST multi-tenant para la gestion de citas (TFG DAM)."))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName,
                                new SecurityScheme()
                                        .name(schemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        // deja lista la documentacion para probar rutas con token JWT
                                        .description("Pega el JWT obtenido en POST /api/auth/token. " +
                                                "Swagger anadira automaticamente la cabecera " +
                                                "Authorization: Bearer <token>.")));
    }
}
