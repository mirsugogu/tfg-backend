package com.optima.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.common.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Rate limiter por IP para los endpoints publicos de autenticacion.
 *
 * Implementa token bucket (RFC informal, popularizado por
 * routers Cisco): cada IP tiene un cubo con N tokens; cada request
 * consume 1; el cubo se rellena a M/intervalo. Si el cubo esta vacio
 * la request se rechaza con 429 Too Many Requests y un header
 * Retry-After con los segundos restantes.
 *
 * Politica actual (valores calibrados para que la collection Postman
 * pueda ejecutarse entera sin choque artificial, manteniendo defensa
 * contra abuso):
 * 
 *   - POST /api/auth/token: 20 intentos / minuto / IP.
 *       Mitiga brute-force de credenciales.
 *   - POST /api/auth/register: 20 intentos / hora / IP.
 *       Frena registro masivo de negocios fake.
 *   - POST /api/auth/forgot-password: 10 / hora / IP.
 *       Frena spam de correos automaticos (cada solicitud manda email).
 *   - POST /api/auth/reset-password: 20 / hora / IP.
 *       Frena fuerza bruta sobre el token (256 bits, imposible en 1h,
 *       pero limitar reduce ruido en logs).
 * 
 *
 * Bucket por IP guardado en memoria (ConcurrentHashMap).
 * Para un TFG sin replicacion horizontal es suficiente. En produccion
 * con varias replicas se sustituiria por bucket4j-redis para compartir
 * estado.
 *
 * Orden en la cadena: este filtro corre antes de
 * JwtAuthenticationFilter para que un atacante no pueda gastar
 * tokens validos del rate limit al ser rechazado por el siguiente
 * filtro. El rate limit es la primera barrera.
 *
 * COMUNICACION:
 * - Lo registra: SecurityConfig.filterChain con
 *   addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class).
 * - Lee: HttpServletRequest.getRemoteAddr() (X-Forwarded-For si hubiera
 *   un proxy delante; aqui no asumimos proxy para mantener simplicidad).
 * - Escribe: si supera el limite, escribe directamente un ErrorResponse
 *   JSON con status 429 y NO llama a chain.doFilter.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** 20 intentos por minuto contra /api/auth/token. */
    private static final Supplier<Bucket> LOGIN_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
            .build();

    /** 20 intentos por hora contra /api/auth/register. */
    private static final Supplier<Bucket> REGISTER_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    /** 10 intentos por hora contra /api/auth/forgot-password (anti-spam de emails). */
    private static final Supplier<Bucket> FORGOT_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1))))
            .build();

    /** 20 intentos por hora contra /api/auth/reset-password. */
    private static final Supplier<Bucket> RESET_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resetBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Bucket bucket = pickBucket(request);
        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, waitSeconds);
    }

    /**
     * Decide que bucket aplica al request. Devuelve null si el endpoint
     * no esta limitado (la mayoria de rutas: el rate limit solo aplica
     * a los endpoints publicos de auth).
     */
    private Bucket pickBucket(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        String ip = request.getRemoteAddr();
        if ("/api/auth/token".equals(uri)) {
            return loginBuckets.computeIfAbsent(ip, k -> LOGIN_BUCKET.get());
        }
        if ("/api/auth/register".equals(uri)) {
            return registerBuckets.computeIfAbsent(ip, k -> REGISTER_BUCKET.get());
        }
        if ("/api/auth/forgot-password".equals(uri)) {
            return forgotBuckets.computeIfAbsent(ip, k -> FORGOT_BUCKET.get());
        }
        if ("/api/auth/reset-password".equals(uri)) {
            return resetBuckets.computeIfAbsent(ip, k -> RESET_BUCKET.get());
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        ErrorResponse body = new ErrorResponse(
                429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Demasiadas peticiones. Reintenta en " + retryAfterSeconds + " segundos.",
                Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
