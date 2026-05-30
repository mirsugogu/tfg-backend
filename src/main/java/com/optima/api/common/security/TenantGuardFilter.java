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
 * comprueba que el usuario solo entre en su negocio
 * tambien revisa que la relacion siga activa
 */
@Component
@RequiredArgsConstructor
public class TenantGuardFilter extends OncePerRequestFilter {

    // este patron obtiene el id del negocio de la ruta
    private static final Pattern BUSINESS_PATH = Pattern.compile("^/api/businesses/([^/]+)(/.*)?$");

    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;

    /** compara el negocio de la ruta con el del token */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        Matcher m = BUSINESS_PATH.matcher(request.getRequestURI());
        if (m.matches()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
                // primero se lee el id de la ruta
                long pathBusinessId;
                try {
                    pathBusinessId = Long.parseLong(m.group(1));
                } catch (NumberFormatException ex) {
                    writeForbidden(response, "Identificador de negocio no valido");
                    return;
                }
                // si aun no ha elegido negocio no puede entrar
                if (principal.businessId() == null) {
                    writeForbidden(response, "Debes seleccionar un negocio antes de acceder a este recurso");
                    return;
                }
                // si no coincide se rechaza
                if (pathBusinessId != principal.businessId()) {
                    writeForbidden(response, "No tienes permiso para acceder a recursos de otro negocio");
                    return;
                }

                // se revisa en base de datos por si ha cambiado algo
                Optional<Membership> membershipOpt = membershipRepository
                        .findForSessionGuard(principal.userId(), principal.businessId());
                if (membershipOpt.isEmpty()
                        || !Boolean.TRUE.equals(membershipOpt.get().getIsActive())) {
                    writeUnauthorized(response,
                            "Tu acceso a este negocio ha sido revocado. Vuelve a iniciar sesión.");
                    return;
                }
                // si el rol cambio se obliga a iniciar sesion otra vez
                String currentRole = membershipOpt.get().getRole().getName();
                if (!Objects.equals(currentRole, principal.role())) {
                    writeUnauthorized(response, "Tu sesión está obsoleta porque tu rol ha cambiado. Vuelve a iniciar sesión.");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, message);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, message);
    }

    /** escribe el error en formato comun */
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
