package com.optima.api.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para generar tokens JWT firmados con HMAC-SHA.
 * Movida desde {@code modules/auth/util/JwUtil} al paquete transversal
 * {@code common/utils} porque la usa la capa de seguridad y no es
 * exclusiva del módulo {@code auth}.
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secretString;

    @Value("${app.jwt.expiration-ms}")
    private long expirationTime;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secretString == null || secretString.length() < 32) {
            throw new RuntimeException("ERROR: La clave 'app.jwt.secret' debe tener al menos 32 caracteres.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long userId, Long businessId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("businessId", businessId);
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Valida firma y expiracion del token entrante. Devuelve los claims si todo
     * es correcto. Lanza {@link io.jsonwebtoken.JwtException} si el token esta
     * mal firmado, expirado, malformado, etc. — el filtro de seguridad usa esa
     * excepcion como senal de "no autenticar esta request".
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
