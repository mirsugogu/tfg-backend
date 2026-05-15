package com.optima.api.common.security;

/**
 * Datos del usuario autenticado que viajan dentro del JWT y se exponen
 * como principal del org.springframework.security.core.Authentication
 * que el JwtAuthenticationFilter pone en el SecurityContext.
 *
 * Permite que cualquier filtro o controlador posterior obtenga el
 * userId, businessId y rol del request en curso sin volver a parsear
 * el token.
 *
 * COMUNICACION:
 * - Lo construye: JwtAuthenticationFilter.doFilterInternal() tras
 *   parsear los claims del JWT.
 * - Lo coloca en: SecurityContextHolder como principal del Authentication.
 * - Lo lee: TenantGuardFilter (compara businessId del path con el del
 *   token) y, si lo necesitara, cualquier metodo @PreAuthorize via SpEL.
 *
 * Es un record inmutable: una vez creado en el filtro, no cambia
 * durante el procesamiento del request.
 */
public record AuthPrincipal(
        Long userId,
        Long businessId,
        String email,
        String role
) {}
