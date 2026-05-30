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
 * Filtro que limita cuantas peticiones puede hacer una misma IP a los endpoints de autenticacion
 * Esto es para evitar que alguien intente muchos logins seguidos o cree muchas cuentas de golpe
 * Usamos la libreria Bucket4j que funciona como un cubo con fichas, cada peticion gasta una ficha
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // cada endpoint tiene su propio limite, asi login puede ser mas estricto que registro

    /** 10 intentos de login por minuto por cada IP */
    private static final Supplier<Bucket> LOGIN_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
            .build();

    /** 20 registros por hora por cada IP */
    private static final Supplier<Bucket> REGISTER_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    /** 10 solicitudes de "olvide mi contrasena" por hora por cada IP */
    private static final Supplier<Bucket> FORGOT_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1))))
            .build();

    /** 20 intentos de resetear contrasena por hora por cada IP */
    private static final Supplier<Bucket> RESET_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    // guardamos un cubo por cada IP, asi cada usuario tiene su propio contador
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resetBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * En cada peticion miramos si el endpoint tiene limite y si le quedan fichas a esa IP
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Bucket bucket = pickBucket(request);
        // si no es un endpoint limitado, dejamos pasar directo
        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }

        // intentamos consumir una ficha del cubo
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        // si no quedan fichas, devolvemos 429 y le decimos cuanto tiene que esperar
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, waitSeconds);
    }

    /**
     * Segun la URL de la peticion, devuelve el cubo que le toca o null si no se limita
     */
    private Bucket pickBucket(HttpServletRequest request) {
        // solo limitamos peticiones POST, los GET no tienen limite
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

    /**
     * Escribe la respuesta 429 con el mensaje de error
     */
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
