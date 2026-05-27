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

/** Comprueba que el usuario autenticado accede solo a su negocio. */
@Component
@RequiredArgsConstructor
public class TenantGuardFilter extends OncePerRequestFilter {

    /** Detecta rutas que pertenecen a un negocio concreto. */
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
                    // El id de negocio de la URL debe ser numerico.
                    writeForbidden(response, "Identificador de negocio no valido");
                    return;
                }
                // Si el token no tiene negocio, primero debe seleccionarse uno.
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

                // Revisa en base de datos si el acceso sigue siendo valido.
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
            // Si no hay usuario autenticado, Spring Security respondera 401.
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
