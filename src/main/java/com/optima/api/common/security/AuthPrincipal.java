package com.optima.api.common.security;

/**
 * Datos del usuario autenticado que viajan dentro del JWT y se exponen
 * como {@code principal} del {@link org.springframework.security.core.Authentication}
 * que el {@code JwtAuthenticationFilter} pone en el {@code SecurityContext}.
 *
 * <p>Permite que cualquier filtro o controlador posterior obtenga el
 * userId, businessId y rol del request en curso sin volver a parsear
 * el token.</p>
 */
public record AuthPrincipal(
        Long userId,
        Long businessId,
        String email,
        String role
) {}
