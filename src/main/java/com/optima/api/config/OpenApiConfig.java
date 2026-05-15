package com.optima.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de OpenAPI/Swagger.
 *
 * Declara un esquema de seguridad HTTP Bearer (JWT) para que la UI de
 * Swagger muestre el boton "Authorize" arriba a la derecha y permita
 * pegar un token JWT. Sin esta configuracion, springdoc no sabe que la
 * API esta protegida con JWT y la UI no ofrece autenticacion.
 *
 * La declaracion del scheme y el SecurityRequirement aplicado a nivel
 * global hace que todos los endpoints "heredien" el requisito en la UI.
 * Los endpoints publicos (login, swagger, catalogos) siguen siendo
 * accesibles sin token porque la decision de autorizar la toma
 * SecurityConfig, no springdoc - este solo describe.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
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
                                        .description("Pega el JWT obtenido en POST /api/auth/token. " +
                                                "Swagger anadira automaticamente la cabecera " +
                                                "Authorization: Bearer <token>.")));
    }
}
