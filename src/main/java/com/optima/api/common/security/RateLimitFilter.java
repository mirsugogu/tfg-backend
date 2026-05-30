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
 * limita peticiones por ip en rutas de auth
 * sirve para frenar intentos repetidos
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // cada ruta tiene su propio limite

    /** 10 intentos de inicio de sesion por minuto por cada ip */
    private static final Supplier<Bucket> LOGIN_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
            .build();

    /** 20 registros por hora por cada ip */
    private static final Supplier<Bucket> REGISTER_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    /** 10 solicitudes de "olvide mi contrasena" por hora por cada ip */
    private static final Supplier<Bucket> FORGOT_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1))))
            .build();

    /** 20 intentos de resetear contrasena por hora por cada ip */
    private static final Supplier<Bucket> RESET_BUCKET = () -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();

    // se guarda un cubo por ip
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resetBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /** revisa si la peticion debe pasar por rate limit */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Bucket bucket = pickBucket(request);
        // si no hay limite para esta ruta se deja pasar
        if (bucket == null) {
            chain.doFilter(request, response);
            return;
        }

        // se intenta consumir una ficha
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        // si no quedan fichas se devuelve 429
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, waitSeconds);
    }

    /** elige el cubo segun la ruta */
    private Bucket pickBucket(HttpServletRequest request) {
        // solo se limitan peticiones post
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

    /** escribe la respuesta de too many requests */
    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        ErrorResponse body = new ErrorResponse(429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "Demasiadas peticiones. Reintenta en " + retryAfterSeconds + " segundos.", Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
