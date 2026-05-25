package com.optima.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.common.exception.ErrorResponse;
import com.optima.api.common.security.JwtAuthenticationFilter;
import com.optima.api.common.security.RateLimitFilter;
import com.optima.api.common.security.TenantGuardFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

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
 * - Le inyecta: RateLimitFilter, JwtAuthenticationFilter y TenantGuardFilter
 *   (los tres son beans @Component) para encadenarlos en el filter chain.
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
    private final RateLimitFilter rateLimitFilter;

    /**
     * ObjectMapper compartido por las lambdas de entryPoint y accessDeniedHandler.
     * Instanciado UNA vez al construir el bean (singleton), no por request.
     * Coherente con el patron de TenantGuardFilter y RateLimitFilter, que tambien
     * declaran el ObjectMapper como field final. Jackson es thread-safe.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Configura el filter chain HTTP - el corazon de la seguridad.
     *
     * Decisiones (de arriba abajo):
     *   csrf().disable()           API REST sin sesiones, no aplica CSRF.
     *   cors(withDefaults())       delega CORS al bean CorsConfigurationSource
     *                              definido mas abajo en esta misma clase.
     *                              Sin esta linea, los preflight OPTIONS de
     *                              browsers recibirian 401 antes de llegar
     *                              a MVC. Imprescindible cuando hay frontend
     *                              en browser haciendo POST/PUT/DELETE.
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
     *                                POST /api/auth/register (alta de negocio)
     *                                POST /api/auth/forgot-password
     *                                POST /api/auth/reset-password
     *                                GET /api/roles (catalogo)
     *                                GET /api/appointment-statuses/** (catalogo)
     *                                GET /swagger-ui.html, /swagger-ui/**,
     *                                    /v3/api-docs(/**) (documentacion)
     *   anyRequest().authenticated todo lo demas requiere JWT valido.
     *   Orden de filtros            RateLimit -> JwtAuth -> TenantGuard.
     *                              RateLimit corre el primero (addFilterBefore
     *                              UsernamePasswordAuthFilter) para frenar
     *                              fuerza bruta antes de gastar capacidad de
     *                              parseo JWT. JwtAuth va detras. TenantGuard
     *                              cierra (necesita el AuthPrincipal del JWT).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse body = new ErrorResponse(
                            401, HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                            "Token requerido o invalido",
                            Instant.now().toString());
                    objectMapper.writeValue(res.getOutputStream(), body);
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse body = new ErrorResponse(
                            403, HttpStatus.FORBIDDEN.getReasonPhrase(),
                            "No tienes permisos suficientes para esta operacion",
                            Instant.now().toString());
                    objectMapper.writeValue(res.getOutputStream(), body);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/roles").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/appointment-statuses/**").permitAll()
                // Documentacion publica: la UI de Swagger y el JSON OpenAPI no
                // requieren JWT. Asi cualquiera puede ver la API sin autenticarse.
                .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Orden: RateLimit -> JwtAuth -> TenantGuard.
            // El rate limit es la PRIMERA barrera para que un atacante no
            // pueda gastar capacidad de los filtros siguientes.
            .addFilterBefore(rateLimitFilter,
                             UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthenticationFilter, RateLimitFilter.class)
            .addFilterAfter(tenantGuardFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Bean CorsConfigurationSource - reglas CORS aplicadas por Spring Security.
     *
     * El .cors(Customizer.withDefaults()) del filterChain BUSCA un bean de
     * este tipo y lo aplica desde dentro de la cadena de filtros. Sin este
     * bean, los defaults de Spring Security son "no allowedOrigins", asi que
     * los preflight OPTIONS de browsers se bloquearian antes de llegar a MVC.
     *
     * Decisiones:
     *   allowedOrigins           lista enumerada (sin comodin) tomada de
     *                            app.cors.allowed-origins en application.properties
     *                            (sobreescribible con la env var
     *                            CORS_ALLOWED_ORIGINS). El fallback de dev
     *                            cubre tanto el frontend web (Vite) como la
     *                            WebView de la app movil empaquetada con
     *                            Capacitor. En produccion AWS la env var
     *                            apunta al dominio publico real.
     *                            Externalizar el dominio del frontend evita
     *                            acoplarlo al binario (patron 12-factor).
     *   allowedMethods           verbos REST que el API expone.
     *   allowedHeaders("*")      el cliente puede mandar cualquier header
     *                            (Content-Type, Authorization).
     *   allowCredentials(false)  el JWT viaja en el header Authorization,
     *                            no como cookie; no hacen falta credenciales
     *                            implicitas cross-origin.
     *
     * El @Value en el parametro lo resuelve Spring al instanciar el bean:
     * la cadena separada por comas se convierte automaticamente en List<String>.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
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
