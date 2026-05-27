package com.optima.api.common.security;

/** Datos del usuario autenticado que se obtienen del JWT. */
public record AuthPrincipal(
        Long userId,
        Long businessId,
        String email,
        String role
) {}
