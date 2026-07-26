package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class BoundaryMembershipServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BoundaryMembershipService service = new BoundaryMembershipService(jdbc);

    @Test
    void canonicalOrgOwnerAndOrgAdminAuthorities_bypassWhenTokenOrgMatchesTargetOrg() {
        List<String> roles = List.of("R_ORG_OWNER", "ROLE_R_ORG_OWNER", "R_ORG_ADMIN", "ROLE_R_ORG_ADMIN");
        for (String role : roles) {
            BoundaryMembershipService localService = new BoundaryMembershipService(jdbc);
            Authentication auth = authentication(role, ORG_ID);
            resolveSpaceOrg(ORG_ID);

            localService.assertActorInSpaceOrg(SPACE_ID, auth);

            verify(jdbc, never()).queryForObject(contains("FROM users u"), eq(Integer.class), any(), any());
        }
    }

    @Test
    void orgOwner_doesNotBypassWhenTokenOrgDiffersFromTargetOrg() {
        Authentication auth = authentication("R_ORG_OWNER", OTHER_ORG_ID);
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(USER_ID), eq(ORG_ID)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void canonicalPlatformAdminAuthorities_bypassWithoutResolvingSpace() {
        for (String role : List.of("R_TAKIBO_PLATFORM_ADMIN", "ROLE_R_TAKIBO_PLATFORM_ADMIN")) {
            BoundaryMembershipService localService = new BoundaryMembershipService(jdbc);

            localService.assertActorInSpaceOrg(SPACE_ID, authentication(role, null));

            verify(jdbc, never()).queryForObject(anyString(), any(RowMapper.class), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORG_OWNER", "ROLE_ORG_OWNER", "ORG_ADMIN", "ROLE_ORG_ADMIN"})
    void legacyOrgAuthorities_doNotBypassMembership(String role) {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(USER_ID), eq(ORG_ID)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, authentication(role, ORG_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PLATFORM_ADMIN", "ROLE_PLATFORM_ADMIN",
            "R_PLATFORM_ADMIN", "ROLE_R_PLATFORM_ADMIN"
    })
    void phantomPlatformAdminAuthorities_doNotBypassMembership(String role) {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(USER_ID), eq(ORG_ID)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, authentication(role, ORG_ID)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    private void resolveSpaceOrg(UUID orgId) {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID))).thenReturn(orgId);
    }

    private Authentication authentication(String role, UUID orgId) {
        Authentication auth = mock(Authentication.class, withSettings().extraInterfaces(TakiboSecurityContextCarrier.class));
        when(auth.isAuthenticated()).thenReturn(true);
        Collection<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        doReturn(authorities).when(auth).getAuthorities();
        if (orgId != null) {
            when(((TakiboSecurityContextCarrier) auth).getSecurityContext()).thenReturn(context(orgId));
        }
        return auth;
    }

    private TakiboSecurityContext context(UUID orgId) {
        return TakiboSecurityContext.builder()
                .subject(new SubjectIdentity(USER_ID.toString(), SubjectNature.HUMAN, Set.of(), AuthenticationMethod.PASSWORD))
                .tenant(new TenantScope(orgId.toString(), SPACE_ID.toString()))
                .temporal(new TemporalContext(Instant.now(), null, null, 1))
                .build();
    }
}
