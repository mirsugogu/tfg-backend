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
 * Filtro que se ejecuta en cada peticion para comprobar si el usuario manda un JWT valido
 * Si el token es bueno, guardamos los datos del usuario en el contexto de Spring Security
 * para que los controllers puedan saber quien esta haciendo la peticion

 * Token de identidad (cuando aun no eligio negocio):
 *   { "sub": "admin@optima.com", "userId": 1 }

 * Token de negocio (cuando ya eligio uno):
 *   { "sub": "admin@optima.com", "userId": 1, "businessId": 3, "role": "ADMIN" }

 * Si el token no trae sub o userId, lo rechazamos porque no sabemos quien es
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    /**
     * Intercepta cada peticion HTTP y busca el token en la cabecera Authorization
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        // si no viene la cabecera Authorization o no empieza con "Bearer ", dejamos pasar sin autenticar
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // quitamos el prefijo "Bearer " para quedarnos solo con el token
        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtUtil.parseAndValidate(token);

            // sacamos el email y el userId del token, estos siempre tienen que venir
            String email = claims.getSubject();
            Object userIdRaw = claims.get("userId");
            Long userId = userIdRaw instanceof Number n ? n.longValue() : null;

            if (email == null || email.isBlank() || userId == null) {
                throw new JwtException("Claims requeridos ausentes en el JWT");
            }

            // businessId y role solo vienen si el usuario ya eligio un negocio
            Number bidClaim = (Number) claims.get("businessId");
            Long businessId = bidClaim != null ? bidClaim.longValue() : null;
            String role = (String) claims.get("role");

            AuthPrincipal principal = new AuthPrincipal(userId, businessId, email, role);

            // le asignamos el rol como autoridad de Spring, asi podemos usar @PreAuthorize en los controllers
            List<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | ClassCastException | NullPointerException ignored) {
            // si el token esta mal o expirado, limpiamos el contexto y la peticion sigue sin autenticar
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
