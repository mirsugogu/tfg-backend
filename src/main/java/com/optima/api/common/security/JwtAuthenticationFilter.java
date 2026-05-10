package com.optima.api.common.security;

import com.optima.api.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que se ejecuta una vez por request y, si trae un header
 * {@code Authorization: Bearer <token>}, valida el JWT con {@link JwtUtil}
 * y autentica al usuario en el {@link SecurityContextHolder}.
 *
 * <p>El principal que se mete en el contexto es un {@link AuthPrincipal}
 * con todos los datos del JWT (userId, businessId, email, role) — asi
 * el {@code TenantGuardFilter} y los controladores pueden leerlos sin
 * volver a parsear el token. Tambien se anade {@code ROLE_<role>} como
 * authority para forward-compat con {@code @PreAuthorize}.</p>
 *
 * <p>Si el header no existe, no es Bearer, o el token es invalido,
 * el filtro NO emite 401 ni rompe la cadena: simplemente no autentica.
 * Quien decide si la ruta requiere autenticacion es {@code SecurityConfig}.</p>
 *
 * COMUNICACION:
 * - Lo registra: SecurityConfig.filterChain con addFilterBefore(...,
 *   UsernamePasswordAuthenticationFilter.class) - asi corre antes que
 *   el filter por defecto de Spring Security.
 * - Llama a: JwtUtil.parseAndValidate() para verificar firma y expiracion.
 * - Escribe en: SecurityContextHolder (autentica al usuario en el
 *   contexto del thread actual, con AuthPrincipal como principal).
 * - Le sigue: TenantGuardFilter, que lee AuthPrincipal del contexto.
 */
@Component
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

            Long userId = ((Number) claims.get("userId")).longValue();
            Long businessId = ((Number) claims.get("businessId")).longValue();
            String email = claims.getSubject();
            String role = (String) claims.get("role");

            AuthPrincipal principal = new AuthPrincipal(userId, businessId, email, role);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | ClassCastException | NullPointerException ex) {
            // Token invalido (firma mal, expirado, malformado, claims ausentes):
            // no autenticamos. SecurityConfig devolvera 401 si la ruta lo requiere.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
