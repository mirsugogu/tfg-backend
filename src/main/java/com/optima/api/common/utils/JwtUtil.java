package com.optima.api.common.utils;

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

    @Value("${JWT_SECRET:EstaEsUnaClaveSuperSecretaYMuyLargaParaQueNoExplote2026!}")
    private String secretString;

    @Value("${JWT_EXPIRATION:86400000}")
    private long expirationTime;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secretString == null || secretString.length() < 32) {
            throw new RuntimeException("ERROR: La clave 'app.jwt.secret' debe tener al menos 32 caracteres.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long businessId, String role) {
        Map<String, Object> claims = new HashMap<>();
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
}
