package com.optima.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.common.exception.ErrorResponse;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.repository.MembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
 * URLs que matchea (regex ^/api/businesses/([^/]+)(/.*)?$):
 *   /api/businesses/5         -> matchea, valida tenant.
 *   /api/businesses/5/users   -> matchea, valida tenant.
 *   /api/businesses/1abc/...  -> matchea; segmento no numerico -> 403.
 *   /api/businesses           -> NO matchea (no hay segmento de negocio).
 *   /api/auth/token           -> NO matchea (login).
 *
 * REVALIDACION DE SESION (post-P9 hardening, criticos A y B):
 * Tras validar el cross-tenant, el filtro verifica en BD que la membership
 * (userId, businessId) sigue activa Y que el rol del JWT coincide con el
 * actual. Si cualquiera falla, devuelve 401 con un mensaje accionable
 * ("tu acceso ha sido revocado" / "tu sesion esta obsoleta"). El
 * interceptor del frontend (api.js) limpia localStorage en cualquier 401
 * y redirige a /login, asi un cambio de rol o una desactivacion invalidan
 * la sesion del afectado a la siguiente request. Coste: +1 query SQL por
 * request a /api/businesses/{id}/...; con el UNIQUE (id_user, id_business)
 * sobre memberships, O(1).
 */
@Component
@RequiredArgsConstructor
public class TenantGuardFilter extends OncePerRequestFilter {

    /**
     * Captura el primer segmento de paths como /api/businesses/{x}/loquesea.
     * Usa [^/]+ (cualquier segmento, no solo \d+) a proposito: asi el filtro
     * tambien matchea un businessId no numerico y lo puede rechazar, en vez
     * de dejarlo pasar sin validar (defensa en profundidad).
     */
    private static final Pattern BUSINESS_PATH =
            Pattern.compile("^/api/businesses/([^/]+)(/.*)?$");

    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Matcher m = BUSINESS_PATH.matcher(request.getRequestURI());
        if (m.matches()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
                long pathBusinessId;
                try {
                    pathBusinessId = Long.parseLong(m.group(1));
                } catch (NumberFormatException ex) {
                    // El segmento de negocio no es numerico: no se puede
                    // validar contra el token, asi que se rechaza.
                    writeForbidden(response, "Identificador de negocio no valido");
                    return;
                }
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

                // Revalidacion de sesion contra la BD (criticos A y B):
                //   1) La membership (userId, businessId) debe existir y
                //      estar activa. Si el admin la desactivo, el JWT del
                //      afectado deja de ser valido en la siguiente request.
                //   2) El rol del JWT debe coincidir con el rol actual en
                //      BD. Si el admin cambio el rol, el JWT con el rol
                //      antiguo se invalida y el afectado debe re-loguearse.
                // Se emite 401 (no 403) para que el interceptor de api.js
                // en el frontend dispare clearSession + redirect a /login.
                Optional<Membership> membershipOpt = membershipRepository
                        .findForSessionGuard(principal.userId(), principal.businessId());
                if (membershipOpt.isEmpty()
                        || !Boolean.TRUE.equals(membershipOpt.get().getIsActive())) {
                    writeUnauthorized(response,
                            "Tu acceso a este negocio ha sido revocado. Vuelve a iniciar sesión.");
                    return;
                }
                String currentRole = membershipOpt.get().getRole().getName();
                if (!Objects.equals(currentRole, principal.role())) {
                    writeUnauthorized(response,
                            "Tu sesión está obsoleta porque tu rol ha cambiado. Vuelve a iniciar sesión.");
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
        writeError(response, HttpStatus.FORBIDDEN, message);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, message);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                status.value(), status.getReasonPhrase(),
                message,
                Instant.now().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
