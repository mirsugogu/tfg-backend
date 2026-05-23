package com.optima.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optima.api.modules.business.model.Membership;
import com.optima.api.modules.business.model.Role;
import com.optima.api.modules.business.repository.MembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de TenantGuardFilter.
 *
 * Aisla el filtro sin levantar contexto Spring: usa los Mock* del paquete
 * spring-test (MockHttpServletRequest, MockHttpServletResponse,
 * MockFilterChain) y manipula el SecurityContextHolder directamente. Para
 * la revalidacion de sesion post-P9 (criticos A y B) se mockea
 * MembershipRepository con Mockito; ningun test toca BD.
 *
 * Casos cubiertos:
 *  - El businessId del path coincide con el del JWT y la membership sigue
 *    activa con el rol correcto -> pasa la cadena, 200.
 *  - El businessId del path NO coincide con el del JWT -> aborta con 403
 *    y NO invoca el siguiente filtro.
 *  - El JWT es identity-only (businessId==null) y el path apunta a un negocio
 *    -> aborta con 403 y mensaje pidiendo seleccionar negocio.
 *  - La membership esta desactivada en BD -> 401 "acceso revocado".
 *  - El rol del JWT no coincide con el actual en BD -> 401 "sesion obsoleta".
 */
@ExtendWith(MockitoExtension.class)
class TenantGuardFilterTest {

    @Mock
    private MembershipRepository membershipRepository;

    private TenantGuardFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Construye el filter en cada test, usando el mock recien preparado. */
    private TenantGuardFilter newFilter() {
        return new TenantGuardFilter(new ObjectMapper(), membershipRepository);
    }

    /** Membership activa con el rol indicado, para stubbear findForSessionGuard. */
    private Membership activeMembershipWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Membership m = new Membership();
        m.setIsActive(true);
        m.setRole(role);
        return m;
    }

    @Test
    void permitePasoCuandoBusinessIdYRolCoinciden() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "admin@optima.com", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        when(membershipRepository.findForSessionGuard(10L, 5L))
                .thenReturn(Optional.of(activeMembershipWithRole("ADMIN")));

        filter = newFilter();

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
        // Token dice businessId=5, path dice 7 -> cross-tenant -> 403.
        // El filtro corta ANTES de la revalidacion de membership, asi que no
        // se necesita stub del repo.
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "admin@optima.com", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        filter = newFilter();

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

        filter = newFilter();

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

    @Test
    void devuelve401CuandoLaMembershipFueDesactivada() throws Exception {
        // Critico B: el admin desactivo la membership; el JWT del afectado
        // sigue siendo valido criptograficamente pero la sesion debe morir.
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "ex@optima.com", "EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        // Repo devuelve membership con isActive=false.
        Membership inactive = activeMembershipWithRole("EMPLOYEE");
        inactive.setIsActive(false);
        when(membershipRepository.findForSessionGuard(10L, 5L))
                .thenReturn(Optional.of(inactive));

        filter = newFilter();

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/businesses/5/clients");
        req.setRequestURI("/api/businesses/5/clients");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString())
                .contains("Tu acceso a este negocio ha sido revocado");
    }

    @Test
    void devuelve401CuandoElRolDelJwtNoCoincideConBd() throws Exception {
        // Critico A: el admin cambio el rol de EMPLOYEE a ADMIN; el JWT
        // viejo lleva role=EMPLOYEE y debe invalidarse en la siguiente
        // request para que el afectado vuelva a loguear y reciba el JWT nuevo.
        AuthPrincipal principal = new AuthPrincipal(10L, 5L, "u@optima.com", "EMPLOYEE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        when(membershipRepository.findForSessionGuard(10L, 5L))
                .thenReturn(Optional.of(activeMembershipWithRole("ADMIN")));

        filter = newFilter();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/businesses/5/clients");
        req.setRequestURI("/api/businesses/5/clients");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString())
                .contains("Tu sesión está obsoleta");
    }
}
