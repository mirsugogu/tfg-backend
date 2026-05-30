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
 * Utilidad para crear y validar los tokens JWT
 * Tenemos dos tipos de token: el de identidad (cuando aun no eligio negocio)
 * y el de negocio (cuando ya selecciono uno y tiene rol asignado)
 */
@Component
public class JwtUtil {

    // estos valores se leen del application.properties
    @Value("${app.jwt.secret}")
    private String secretString;

    @Value("${app.jwt.expiration-ms}")
    private long expirationTime;

    private SecretKey secretKey;

    /**
     * Se ejecuta al arrancar la app, convierte el string secreto en una clave de firma
     */
    @PostConstruct
    public void init() {
        // la clave tiene que tener minimo 32 caracteres por seguridad
        if (secretString == null || secretString.length() < 32) {
            throw new IllegalStateException("La clave 'app.jwt.secret' debe tener al menos 32 caracteres.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un token completo con el negocio y rol ya seleccionados
     */
    public String generateTenantToken(String email, Long userId, Long businessId, String role) {
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
     * Genera un token basico solo con email y userId, para cuando aun no eligio negocio
     */
    public String generateIdentityToken(String email, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Valida que el token no este expirado ni manipulado y devuelve los datos que tiene dentro
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
