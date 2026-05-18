package com.optima.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guard cross-tenant. Para cualquier request a un recurso anidado bajo
 * /api/businesses/{businessId/...}, comprueba que el businessId
 * del path coincide con el businessId del token (claim del JWT,
 * disponible como AuthPrincipal en el SecurityContext).
 *
 * Si no coinciden, devuelve 403 Forbidden con cuerpo JSON
 * consistente con ErrorResponse. Si la URL no apunta a un negocio
 * concreto (p.ej. POST /api/businesses, GET /api/roles, login...), o no
 * hay autenticacion en contexto (rutas publicas), el filtro deja pasar
 * la request.
 *
 * Es la barrera multi-tenant a nivel de URL: aunque la BD ya filtra
 * cross-tenant via findByIdAndBusinessId, este filtro impide
 * siquiera llegar al servicio si el usuario intenta acceder a un negocio
 * que no es el suyo. Se ejecuta DESPUES de JwtAuthenticationFilter
 * para tener el principal disponible.
 *
 * COMUNICACION:
 * - Lo registra: SecurityConfig.filterChain con addFilterAfter(...,
 *   JwtAuthenticationFilter.class) - corre justo despues de autenticar.
 * - Lee de: SecurityContextHolder.getContext().getAuthentication() para
 *   obtener el AuthPrincipal con el businessId del token.
 * - Escribe: si detecta cross-tenant, escribe directamente en
 *   HttpServletResponse un JSON ErrorResponse con status 403 y aborta
 *   la cadena (NO llama a chain.doFilter).
 *
 * URLs que matchea (regex ^/api/businesses/(\d+)(/.*)?$):
 *   /api/businesses/5         -> matchea, valida tenant.
 *   /api/businesses/5/users   -> matchea, valida tenant.
 *   /api/businesses           -> NO matchea (catalogo publico).
 *   /api/businesses/slug/abc  -> NO matchea (busqueda por slug, publica).
 *   /api/auth/token           -> NO matchea (login).
 */
@Component
public class TenantGuardFilter extends OncePerRequestFilter {

    /** Captura el businessId numerico de paths como /api/businesses/123/loquesea */
    private static final Pattern BUSINESS_PATH =
            Pattern.compile("^/api/businesses/(\\d+)(/.*)?$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Matcher m = BUSINESS_PATH.matcher(request.getRequestURI());
        if (m.matches()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
                long pathBusinessId = Long.parseLong(m.group(1));
                // [v16 membership] Distinguimos dos rechazos:
                //   - identity-only token (businessId==null): el usuario aun
                //     no ha elegido negocio; el frontend debe redirigirle al
                //     selector. Mensaje accionable.
                //   - cross-tenant (businessId!=null y != path): el usuario
                //     intenta acceder a un negocio que no es el suyo.
                if (principal.businessId() == null) {
                    writeForbidden(response,
                            "Debes seleccionar un negocio antes de acceder a este recurso");
                    return;
                }
                if (pathBusinessId != principal.businessId()) {
                    writeForbidden(response,
                            "No tienes permiso para acceder a recursos de otro negocio");
                    return;
                }
            }
            // Si no hay principal (request publica con path /api/businesses/X/...
            // que es raro porque normalmente esas rutas requieren auth), dejamos
            // pasar y SecurityConfig ya devolvera 401 si toca.
        }

        chain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                403, HttpStatus.FORBIDDEN.getReasonPhrase(),
                message,
                Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
