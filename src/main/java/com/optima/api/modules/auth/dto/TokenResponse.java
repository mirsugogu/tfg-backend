package com.optima.api.modules.auth.dto;

/**
 * DTO de salida para el endpoint de login.
 * Encapsula el JWT firmado que el cliente debe enviar en cabeceras
 * {@code Authorization: Bearer <token>} de las siguientes peticiones.
 *
 * COMUNICACION:
 * - Lo construye: AuthService.login().
 * - Lo serializa Jackson a JSON al devolverlo desde AuthController.token().
 *
 * Estructura del JWT (3 partes separadas por puntos):
 *   header    - {"alg":"HS384","typ":"JWT"}
 *   payload   - {"sub":"email","userId":1,"businessId":1,"role":"ADMIN","iat":...,"exp":...}
 *   signature - HMAC-SHA384(header.payload, secretKey)
 *
 * El cliente NO necesita decodificar el token: solo guardarlo y
 * reenviarlo. La validacion la hace JwtAuthenticationFilter en cada
 * request entrante.
 */
public record TokenResponse(String token) {}
