package com.optima.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.common.exception.ErrorResponse;
import com.optima.api.common.security.JwtAuthenticationFilter;
import com.optima.api.common.security.TenantGuardFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

/**
 * SecurityConfig - Centraliza TODA la configuracion de Spring Security.
 *
 * Define:
 *   - El filter chain HTTP: que filtros corren, en que orden, y que
 *     rutas son publicas.
 *   - Los handlers de error de la cadena de filtros (401, 403).
 *   - El bean PasswordEncoder (BCrypt) para hashear y verificar passwords.
 *
 * COMUNICACION:
 * - Lo carga Spring Boot al arrancar (estereotipo @Configuration).
 * - Le inyecta: JwtAuthenticationFilter y TenantGuardFilter (ambos beans
 *   creados con @Component) para encadenarlos en el filter chain.
 * - Sus beans los inyectan: AuthService (PasswordEncoder).
 *
 * Anotaciones:
 *   @EnableWebSecurity    activa el filter chain de Spring Security.
 *   @EnableMethodSecurity activa @PreAuthorize / @PostAuthorize en
 *                         metodos. Sin esto, las anotaciones de los
 *                         controllers serian ignoradas.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantGuardFilter tenantGuardFilter;

    /**
     * Configura el filter chain HTTP - el corazon de la seguridad.
     *
     * Decisiones (de arriba abajo):
     *   csrf().disable()           API REST sin sesiones, no aplica CSRF.
     *   STATELESS                  no se crean HttpSession; cada request
     *                              se autentica con su JWT.
     *   authenticationEntryPoint   cuando una ruta autenticada no trae
     *                              JWT valido -> 401 con cuerpo JSON.
     *   accessDeniedHandler        cuando la cadena rechaza por falta de
     *                              permisos -> 403 con cuerpo JSON.
     *                              (Los AccessDeniedException de
     *                              @PreAuthorize los captura
     *                              GlobalExceptionHandler.)
     *   permitAll endpoints        rutas publicas (sin JWT):
     *                                POST /api/auth/token (login)
     *                                GET /api/roles (catalogo)
     *                                GET /api/appointment-statuses/** (catalogo)
     *   anyRequest().authenticated todo lo demas requiere JWT valido.
     *   addFilterBefore JwtAuth    antes de UsernamePasswordAuthFilter.
     *   addFilterAfter TenantGuard despues de JwtAuth (necesita el principal).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse body = new ErrorResponse(
                            401, "401 UNAUTHORIZED",
                            "Token requerido o invalido",
                            Instant.now().toString());
                    new ObjectMapper().writeValue(res.getOutputStream(), body);
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse body = new ErrorResponse(
                            403, "403 FORBIDDEN",
                            "No tienes permisos suficientes para esta operacion",
                            Instant.now().toString());
                    new ObjectMapper().writeValue(res.getOutputStream(), body);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/roles").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/appointment-statuses/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantGuardFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Bean PasswordEncoder usando BCrypt.
     *
     * BCrypt:
     *   - Algoritmo de hash one-way (no reversible) con sal aleatoria
     *     embebida en el hash.
     *   - Coste configurable (default 10): rounds = 2^10 = 1024 iteraciones.
     *     Lo bastante lento para frenar fuerza bruta.
     *   - El hash incluye sal y coste, asi que matches(plain, hash)
     *     no necesita parametros extra.
     *
     * Lo inyecta: AuthService.login() para verificar password.
     * Tambien lo usaria UserService.create() / .update() para hashear
     * un password nuevo antes de persistirlo.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
