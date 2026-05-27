package com.optima.api.common.security;

import com.optima.api.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida el JWT recibido en el header Authorization.
 *
 * Si el token es correcto, guarda los datos del usuario en el contexto de
 * seguridad para que el resto de filtros y controladores puedan usarlos.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtUtil.parseAndValidate(token);

            // Un token valido debe identificar siempre a un usuario.
            String email = claims.getSubject();
            Object userIdRaw = claims.get("userId");
            Long userId = userIdRaw instanceof Number n ? n.longValue() : null;

            if (email == null || email.isBlank() || userId == null) {
                log.warn("JWT con claims requeridos ausentes: sub='{}', userId={}",
                        email, userId);
                throw new JwtException("Claims requeridos ausentes en el JWT");
            }

            // El token puede ser de identidad o de negocio seleccionado.
            Number bidClaim = (Number) claims.get("businessId");
            Long businessId = bidClaim != null ? bidClaim.longValue() : null;
            String role = (String) claims.get("role");

            AuthPrincipal principal = new AuthPrincipal(userId, businessId, email, role);

            // Solo los tokens con rol pueden pasar validaciones por rol.
            List<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | ClassCastException | NullPointerException ex) {
            // Si el token no es valido, la request queda sin autenticar.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
