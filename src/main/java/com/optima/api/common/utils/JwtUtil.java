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
 * Movida desde modules/auth/util/JwUtil al paquete transversal
 * common/utils porque la usa la capa de seguridad y no es
 * exclusiva del módulo auth.
 *
 * COMUNICACION:
 * - Lo inyectan: AuthService (genera tokens en login) y
 *   JwtAuthenticationFilter (valida tokens en cada request).
 * - Lee de application.properties:
 *     app.jwt.secret        clave HMAC, minimo 32 caracteres.
 *     app.jwt.expiration-ms duracion del token en milisegundos.
 *
 * Algoritmo de firma: la libreria JJWT auto-selecciona segun el tamano
 * de la clave. Para nuestro secret de 56 bytes -> HS384 (rango 48-64).
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secretString;

    @Value("${app.jwt.expiration-ms}")
    private long expirationTime;

    private SecretKey secretKey;

    /**
     * Inicializacion post-inyeccion. Spring invoca este metodo despues
     * de inyectar @Value en secretString (la inyeccion via @Value es por
     * reflexion, no por constructor, asi que el constructor termina antes
     * de que el campo este puesto).
     *
     * Valida que la clave es lo bastante larga (minimo 32 chars / 256 bits)
     * y construye un SecretKey HMAC para firmar y validar tokens.
     *
     * Si el secret no cumple, la app falla AL ARRANCAR - asi descubrimos
     * errores de configuracion en startup, no en runtime.
     */
    @PostConstruct
    public void init() {
        if (secretString == null || secretString.length() < 32) {
            throw new RuntimeException("ERROR: La clave 'app.jwt.secret' debe tener al menos 32 caracteres.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Construye un JWT "tenant-bound" con la membership seleccionada.
     *
     * Claims del payload:
     *   sub        -> email (subject estandar de JWT)
     *   userId     -> id en BD del usuario (identidad)
     *   businessId -> id del negocio (clave del multi-tenancy)
     *   role       -> nombre del rol en ese negocio (ADMIN o EMPLOYEE)
     *   iat        -> issued at, timestamp actual
     *   exp        -> expiracion = ahora + app.jwt.expiration-ms
     *
     * [v16 membership] Es el token que se emite cuando el usuario ya ha
     * elegido un negocio (1 sola membership activa o tras invocar
     * /api/auth/select-business/{id}).
     *
     * Lo invoca: AuthService (login con 1 membership y select-business).
     * Lo decodifica: JwtAuthenticationFilter en cada request autenticada.
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
     * Construye un JWT de "identidad" (sin negocio asociado).
     *
     * Claims del payload:
     *   sub    -> email
     *   userId -> id de la identidad
     *   iat / exp
     *
     * [v16 membership] Se emite cuando el usuario tiene >1 membership
     * activas: el cliente debe llamar a /api/auth/select-business/{id}
     * pasando este token para canjearlo por un tenant token.
     *
     * Lo invoca: AuthService.login (caso multi-membership).
     * Lo decodifica: JwtAuthenticationFilter (sin claim businessId el
     * principal queda con businessId=null y role=null).
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
     * Valida firma y expiracion del token entrante. Devuelve los claims si todo
     * es correcto. Lanza io.jsonwebtoken.JwtException si el token esta
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
