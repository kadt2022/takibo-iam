package com.takibo.securitymanagement.infrastructure.security.boundary;

import com.takibo.securitycontext.model.AttributeKey;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.ContextAttributeStore;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckFailedException;
import com.takibo.securitymanagement.domain.exception.BoundaryCheckUnavailableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

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
    @ValueSource(strings = {
            "ORG_OWNER", "ROLE_ORG_OWNER", "ORG_ADMIN", "ROLE_ORG_ADMIN",
            "PLATFORM_ADMIN", "ROLE_PLATFORM_ADMIN",
            "R_PLATFORM_ADMIN", "ROLE_R_PLATFORM_ADMIN"
    })
    void nonCanonicalAuthorities_doNotBypassMembership(String role) {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(USER_ID), eq(ORG_ID)))
                .thenReturn(0);
        Authentication auth = authentication(role, ORG_ID);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void organizationToken_isRecognizedThroughItsExplicitAccount() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), eq(ACCOUNT_ID), eq(ORG_ID)))
                .thenReturn(1);
        Authentication auth = carrier(ACCOUNT_ID.toString(), SubjectNature.HUMAN,
                Map.of(StandardAttributeKeys.ACCOUNT_ID, ACCOUNT_ID));

        service.assertActorInSpaceOrg(SPACE_ID, auth);

        verify(jdbc, never()).queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), any(), any());
    }

    @Test
    void spaceToken_usesTheExplicitUser_neverTheAccountSubjectAsUser() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("u.account_id = ?"), eq(Integer.class),
                eq(USER_ID), eq(ACCOUNT_ID), eq(ORG_ID))).thenReturn(1);
        Authentication auth = carrier(ACCOUNT_ID.toString(), SubjectNature.HUMAN, Map.of(
                StandardAttributeKeys.ACCOUNT_ID, ACCOUNT_ID,
                StandardAttributeKeys.USER_ID, USER_ID));

        service.assertActorInSpaceOrg(SPACE_ID, auth);

        verify(jdbc, never()).queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(ACCOUNT_ID), any());
    }

    @Test
    void machineSubjectEqualToAUserUuid_isNotTreatedAsThatUser() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any())).thenReturn(1);
        Authentication auth = carrier(USER_ID.toString(), SubjectNature.SERVICE, Map.of());

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, auth))
                .isInstanceOf(AccessDeniedException.class);

        verify(jdbc, never()).queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(USER_ID), any());
    }

    // --- SEC-TMS-06 : identité explicite, erreurs fiables, cache (R1-R8) ---

    private Authentication spaceHuman() {
        return carrier(ACCOUNT_ID.toString(), SubjectNature.HUMAN, Map.of(
                StandardAttributeKeys.ACCOUNT_ID, ACCOUNT_ID,
                StandardAttributeKeys.USER_ID, USER_ID));
    }

    private Authentication organizationHuman() {
        return carrier(ACCOUNT_ID.toString(), SubjectNature.HUMAN,
                Map.of(StandardAttributeKeys.ACCOUNT_ID, ACCOUNT_ID));
    }

    @Test
    void r1_accountDifferentFromUser_isFoundByItsExplicitAccount() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), eq(ACCOUNT_ID), eq(ORG_ID)))
                .thenReturn(1);

        service.assertActorInSpaceOrg(SPACE_ID, organizationHuman());
    }

    @Test
    void accountAndUserNotBelongingTogether_areRefusedWithoutAnyFallback() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("u.account_id = ?"), eq(Integer.class),
                eq(USER_ID), eq(ACCOUNT_ID), eq(ORG_ID))).thenReturn(0);
        when(jdbc.queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), eq(ACCOUNT_ID), eq(ORG_ID)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");

        verify(jdbc, never()).queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), any(), any());
    }

    @Test
    void r2_spaceHuman_isAcceptedWhenUserBelongsToAccountAndOrg() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("u.account_id = ?"), eq(Integer.class),
                eq(USER_ID), eq(ACCOUNT_ID), eq(ORG_ID))).thenReturn(1);

        service.assertActorInSpaceOrg(SPACE_ID, spaceHuman());
    }

    @Test
    void r3_accountWithoutProjectionInTheTargetOrg_isRefused() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, organizationHuman()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_NOT_IN_SPACE_ORG");
    }

    @Test
    void humanWithoutAnyExplicitIdentifier_isRefusedWithoutQuerying() {
        resolveSpaceOrg(ORG_ID);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID,
                carrier("alice", SubjectNature.HUMAN, Map.of())))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_IDENTITY_MISSING");

        verify(jdbc, never()).queryForObject(contains("FROM users u"), eq(Integer.class), any());
    }

    @Test
    void r4_unavailableDatabaseOnMembership_isNeitherARefusalNorASuccess() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(BoundaryCheckUnavailableException.class);
        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, organizationHuman()))
                .isInstanceOf(BoundaryCheckUnavailableException.class);
    }

    @Test
    void r4_structuralSqlErrorOnMembership_isAnInternalFailure() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any()))
                .thenThrow(new BadSqlGrammarException("task", "sql", new java.sql.SQLException("bad")));

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, organizationHuman()))
                .isInstanceOf(BoundaryCheckFailedException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void r5_failureOfTheUserCheck_neverFallsThroughToAnotherIdentifier() {
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("u.account_id = ?"), eq(Integer.class), eq(USER_ID), eq(ACCOUNT_ID), eq(ORG_ID)))
                .thenThrow(new DataAccessResourceFailureException("down"));
        when(jdbc.queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), eq(ACCOUNT_ID), eq(ORG_ID)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(BoundaryCheckUnavailableException.class);

        verify(jdbc, never()).queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), any(), any());
    }

    @Test
    void r6_resolutionFailures_areNotSpaceNotFound() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID)))
                .thenThrow(new DataAccessResourceFailureException("down"));
        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(BoundaryCheckUnavailableException.class);

        BoundaryMembershipService other = new BoundaryMembershipService(jdbc);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid UUID string"))
                .when(jdbc).queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID));
        assertThatThrownBy(() -> other.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(BoundaryCheckFailedException.class);
    }

    @Test
    void r6_spaceWithoutOrganization_isInconsistentDataNotAMissingSpace() {
        resolveSpaceOrg(null);

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(BoundaryCheckFailedException.class);
    }

    @Test
    void r7_reallyAbsentSpace_isSpaceNotFound() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, spaceHuman()))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void r8_cache_servesResolvedOrgOnlyUntilTtl_andNeverStoresAFailure() {
        MutableClock clock = new MutableClock();
        BoundaryMembershipService cached = new BoundaryMembershipService(jdbc, clock);
        resolveSpaceOrg(ORG_ID);
        when(jdbc.queryForObject(contains("FROM users u"), eq(Integer.class), any(), any())).thenReturn(1);
        cached.assertActorInSpaceOrg(SPACE_ID, organizationHuman());

        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("down"))
                .when(jdbc).queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID));
        clock.advance(Duration.ofSeconds(5));
        cached.assertActorInSpaceOrg(SPACE_ID, organizationHuman());

        clock.advance(Duration.ofSeconds(6));
        assertThatThrownBy(() -> cached.assertActorInSpaceOrg(SPACE_ID, organizationHuman()))
                .isInstanceOf(BoundaryCheckUnavailableException.class);

        org.mockito.Mockito.doReturn(ORG_ID)
                .when(jdbc).queryForObject(anyString(), any(RowMapper.class), eq(SPACE_ID));
        cached.assertActorInSpaceOrg(SPACE_ID, organizationHuman());
    }

    @Test
    void unsupportedAuthenticationForm_isRefusedNotGuessed() {
        resolveSpaceOrg(ORG_ID);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ACTOR_IDENTITY_MISSING");
    }

    @Test
    void unauthenticated_isAnAuthenticationFailure() {
        assertThatThrownBy(() -> service.assertActorInSpaceOrg(SPACE_ID, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
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
                .attributes(new ContextAttributeStore(Map.of(StandardAttributeKeys.USER_ID, USER_ID)))
                .build();
    }

    private Authentication carrier(String subjectId, SubjectNature nature, Map<AttributeKey, Object> attributes) {
        Authentication auth = mock(Authentication.class, withSettings().extraInterfaces(TakiboSecurityContextCarrier.class));
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of()).when(auth).getAuthorities();
        TakiboSecurityContext ctx = TakiboSecurityContext.builder()
                .subject(new SubjectIdentity(subjectId, nature, Set.of(), AuthenticationMethod.PASSWORD))
                .tenant(new TenantScope(ORG_ID.toString(), null))
                .temporal(new TemporalContext(Instant.now(), null, null, 1))
                .attributes(new ContextAttributeStore(attributes))
                .build();
        when(((TakiboSecurityContextCarrier) auth).getSecurityContext()).thenReturn(ctx);
        return auth;
    }
}
