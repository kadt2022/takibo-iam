package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import com.takibo.securitymanagement.domain.service.PolicyEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Vérifie que la politique déterministe (PolicyEvaluator) est bien câblée dans le chemin
 * d'autorisation ACTIF : un DENY de politique court-circuite l'ADP, un PERMIT le consulte.
 */
@ExtendWith(MockitoExtension.class)
class PolicyBasedAuthorizationManagerTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String READABLE_USERS_PATH = "/api/v1/orgs/takibo-iam/spaces/finance/users";

    @Mock private AdaptiveDecisionPort adaptiveDecisionPort;
    @Mock private RequestVelocityTracker velocityTracker;
    @Mock private CurrentTakiboSecurityContextProvider contextProvider;
    @Mock private Authentication authentication;

    private PolicyBasedAuthorizationManager manager() {
        return new PolicyBasedAuthorizationManager(
                adaptiveDecisionPort, velocityTracker, contextProvider, new PolicyEvaluator());
    }

    private TakiboSecurityContext humanContext(Set<String> roles) {
        return TakiboSecurityContext.builder()
                .subject(new SubjectIdentity("user-1", SubjectNature.HUMAN, roles, AuthenticationMethod.PASSWORD))
                .tenant(new TenantScope(ORG, SPACE))
                .temporal(new TemporalContext(Instant.now(), "req-1", "http://localhost:8081", 1))
                .build();
    }

    private RequestAuthorizationContext createUserRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", READABLE_USERS_PATH);
        request.setRequestURI(READABLE_USERS_PATH);
        return new RequestAuthorizationContext(request);
    }

    private void givenAuthenticatedHuman(Set<String> roles) {
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(List.of()).when(authentication).getAuthorities();
        when(contextProvider.current()).thenReturn(humanContext(roles));
    }

    @Test
    void createUser_withoutAdminRole_deniedByPolicy_beforeAdp() {
        givenAuthenticatedHuman(Set.of());

        AuthorizationDecision decision = manager().authorize(() -> authentication, createUserRequest());

        assertThat(decision.isGranted()).isFalse();
        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    @Test
    void unknownTmsRoute_deniedByPolicy_beforeAdp_evenForOrgOwner() {
        givenAuthenticatedHuman(Set.of("R_ORG_OWNER"));
        String path = "/api/v1/orgs/" + ORG + "/future-resource";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);

        AuthorizationDecision decision = manager().authorize(
                () -> authentication, new RequestAuthorizationContext(request));

        assertThat(decision.isGranted()).isFalse();
        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    @Test
    void encodedOrParameterizedTmsUuid_cannotBypassPolicy() {
        givenAuthenticatedHuman(Set.of("R_SPACE_ADMIN"));
        String encodedOrg = ORG.replace("-", "%2D");

        for (String path : new String[]{
                "/api/v1/orgs/" + encodedOrg + "/spaces",
                "/api/v1/orgs/" + ORG + ";source=review/spaces"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRequestURI(path);

            AuthorizationDecision decision = manager().authorize(
                    () -> authentication, new RequestAuthorizationContext(request));

            assertThat(decision.isGranted()).as(path).isFalse();
        }

        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    @Test
    void malformedRequestPath_failsClosedBeforePolicyAndAdp() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(contextProvider.current()).thenReturn(humanContext(Set.of("R_ORG_OWNER")));
        String path = "/api/v1/orgs/%ZZ/spaces";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);

        AuthorizationDecision decision = manager().authorize(
                () -> authentication, new RequestAuthorizationContext(request));

        assertThat(decision.isGranted()).isFalse();
        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    @Test
    void createUser_withRealFounderRoles_passesPolicy_thenConsultsAdp() {
        givenAuthenticatedHuman(Set.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
        when(velocityTracker.getVelocity("user-1"))
                .thenReturn(new RequestVelocityTracker.VelocitySnapshot(0, 0));
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(new DecisionResponse(
                "d-1", Decision.ALLOW, 0.0, 1.0, 0.0, "ok",
                null, List.of(), null, Instant.now(), 1));

        AuthorizationDecision decision = manager().authorize(() -> authentication, createUserRequest());

        assertThat(decision.isGranted()).isTrue();
        verify(adaptiveDecisionPort).evaluate(any());
    }

    @Test
    void adpDeny_isNotOverriddenByPolicyPermit() {
        givenAuthenticatedHuman(Set.of("R_SPACE_ADMIN"));
        when(velocityTracker.getVelocity("user-1"))
                .thenReturn(new RequestVelocityTracker.VelocitySnapshot(0, 0));
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(new DecisionResponse(
                "d-2", Decision.DENY, 90.0, 1.0, 0.0, "high risk",
                null, List.of(), null, Instant.now(), 1));

        AuthorizationDecision decision = manager().authorize(() -> authentication, createUserRequest());

        assertThat(decision.isGranted()).isFalse();
    }
}
