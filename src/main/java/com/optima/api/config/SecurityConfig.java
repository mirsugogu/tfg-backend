package com.optima.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Desactivar CSRF (fundamental para que funcione DELETE y POST en Postman)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Abrir las rutas para desarrollo
                .authorizeHttpRequests(auth -> auth
                        // Permitimos TODO lo que empiece por /api/ para no tener que añadir línea por línea
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/api/services/**").permitAll()
                        .requestMatchers("/api/clients/").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}