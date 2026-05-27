package com.optima.api.common.security;

/**
 * Datos del usuario autenticado durante una peticion.
 *
 * Se crea a partir del JWT y permite consultar el usuario, negocio y rol
 * actuales sin volver a leer el token.
 */
public record AuthPrincipal(
        Long userId,
        Long businessId,
        String email,
        String role
) {}
