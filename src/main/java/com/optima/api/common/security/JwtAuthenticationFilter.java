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
 * Filtro que se ejecuta una vez por request y, si trae un header
 * Authorization: Bearer <token>, valida el JWT con JwtUtil
 * y autentica al usuario en el SecurityContextHolder.
 *
 * El principal que se mete en el contexto es un AuthPrincipal
 * con todos los datos del JWT (userId, businessId, email, role) — asi
 * el TenantGuardFilter y los controladores pueden leerlos sin
 * volver a parsear el token. Tambien se anade ROLE_<role> como
 * authority para forward-compat con @PreAuthorize.
 *
 * Si el header no existe, no es Bearer, o el token es invalido,
 * el filtro NO emite 401 ni rompe la cadena: simplemente no autentica.
 * Quien decide si la ruta requiere autenticacion es SecurityConfig.
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

            // Defensa-en-profundidad: aunque la firma del JWT sea valida,
            // un token sin sub o sin userId no identifica a nadie y por
            // contrato lo emite siempre JwtUtil con ambos claims presentes.
            // Si llega sin ellos rechazamos para no construir un
            // AuthPrincipal medio vacio que el resto del codigo asume completo.
            String email = claims.getSubject();
            Object userIdRaw = claims.get("userId");
            Long userId = userIdRaw instanceof Number n ? n.longValue() : null;

            if (email == null || email.isBlank() || userId == null) {
                log.warn("JWT con claims requeridos ausentes: sub='{}', userId={}",
                        email, userId);
                throw new JwtException("Claims requeridos ausentes en el JWT");
            }

            // [v16 membership] El token puede ser:
            //   - tenant: businessId + role presentes en los claims.
            //   - identity: ambos ausentes; el cliente aun no ha elegido negocio.
            Number bidClaim = (Number) claims.get("businessId");
            Long businessId = bidClaim != null ? bidClaim.longValue() : null;
            String role = (String) claims.get("role");

            AuthPrincipal principal = new AuthPrincipal(userId, businessId, email, role);

            // Las authorities solo se anaden cuando hay role: un identity
            // token no puede pasar @PreAuthorize("hasRole(...)"), lo cual
            // es lo correcto (solo /select-business y /me/businesses deben
            // ser accesibles con identity).
            List<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | ClassCastException | NullPointerException ex) {
            // Token invalido (firma mal, expirado, malformado, claims ausentes):
            // no autenticamos. SecurityConfig devolvera 401 si la ruta lo requiere.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
