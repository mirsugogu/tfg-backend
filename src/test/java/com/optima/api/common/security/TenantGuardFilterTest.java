package com.optima.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de TenantGuardFilter.
 *
 * Aisla el filtro sin levantar contexto Spring: usa los Mock* del paquete
 * spring-test (MockHttpServletRequest, MockHttpServletResponse,
 * MockFilterChain) y manipula el SecurityContextHolder directamente.
 *
 * Casos cubiertos:
 *  - El businessId del path coincide con el del JWT -> pasa la cadena, 200.
 *  - El businessId del path NO coincide con el del JWT -> aborta con 403
 *    y NO invoca el siguiente filtro.
 *  - El JWT es identity-only (businessId==null) y el path apunta a un negocio
 *    -> aborta con 403 y mensaje pidiendo seleccionar negocio.
 */
class TenantGuardFilterTest {

    private final TenantGuardFilter filter = new TenantGuardFilter(new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permitePasoCuandoBusinessIdDelPathCoincideConElDelToken() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "admin@optima.com", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/businesses/5/users");
        req.setRequestURI("/api/businesses/5/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // chain.getRequest() != null prueba que la cadena se invoco
        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void devuelve403CuandoBusinessIdDelPathNoCoincideConElDelToken() throws Exception {
        // Token dice businessId=5, path dice 7 -> cross-tenant -> 403
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "admin@optima.com", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/businesses/7/users");
        req.setRequestURI("/api/businesses/7/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // El filtro corta la cadena: chain.getRequest() debe ser null
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getContentAsString())
                .contains("No tienes permiso para acceder a recursos de otro negocio");
    }

    @Test
    void devuelve403PidiendoSeleccionarNegocioCuandoTokenEsIdentityOnly() throws Exception {
        // [v16 membership] Identity token: businessId=null. El usuario debe
        // pasar por /api/auth/select-business antes de acceder a recursos
        // tenant. El mensaje del 403 debe ser accionable, no "negocio ajeno".
        AuthPrincipal principal = new AuthPrincipal(10L, null, "maria@optima.com", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/businesses/1/users");
        req.setRequestURI("/api/businesses/1/users");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString())
                .contains("Debes seleccionar un negocio antes de acceder a este recurso");
    }
}
