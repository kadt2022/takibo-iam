package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import com.takibo.securitycontext.exception.TakiboSecurityContextNotAvailableException;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.service.PolicyEvaluator;
import com.takibo.securitymanagement.support.TokenPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * Chaque chemin d'échec refuse l'accès (fail-closed), sans jamais laisser passer la requête.
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

    // Autorités telles que le token SPACE les porte : permissions situées de ses rôles.
    private void givenAuthenticatedHuman(Set<String> roles) {
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(TokenPermissions.of(roles, ORG, SPACE, "SPACE").stream()
                .map(SimpleGrantedAuthority::new).toList()).when(authentication).getAuthorities();
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

    @Test
    void targetOrganization_isReadFromThePath_andAnotherOrganizationIsDenied_beforeAdp() {
        givenAuthenticatedHuman(Set.of("R_ORG_OWNER"));
        String otherOrg = "99999999-0000-0000-0000-000000000009";
        String path = "/api/v1/orgs/" + otherOrg + "/spaces";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        PolicyEvaluator evaluator = spy(new PolicyEvaluator());
        ArgumentCaptor<Resource> resource = ArgumentCaptor.forClass(Resource.class);

        AuthorizationDecision decision = new PolicyBasedAuthorizationManager(
                adaptiveDecisionPort, velocityTracker, contextProvider, evaluator)
                .authorize(() -> authentication, new RequestAuthorizationContext(request));

        verify(evaluator).evaluate(any(), resource.capture(), any(), any());
        assertThat(resource.getValue().orgId()).isEqualTo(otherOrg);
        assertThat(decision.isGranted()).isFalse();
        verify(adaptiveDecisionPort, never()).evaluate(any());
    }

    // ---------- Fail-closed : tout chemin d'échec refuse ----------

    @Test
    void anonymousCaller_isDenied_withoutReadingTheContext() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        AuthorizationDecision decision = manager().authorize(() -> anonymous, createUserRequest());

        assertThat(decision.isGranted()).isFalse();
        verifyNoInteractions(contextProvider, adaptiveDecisionPort);
    }

    @Test
    void absentOrUnauthenticatedCaller_isDenied() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertThat(manager().authorize(() -> null, createUserRequest()).isGranted()).isFalse();
        assertThat(manager().authorize(() -> authentication, createUserRequest()).isGranted()).isFalse();
        verifyNoInteractions(contextProvider, adaptiveDecisionPort);
    }

    @Test
    void missingSecurityContext_isDenied_beforePolicyAndAdp() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(contextProvider.current()).thenThrow(new TakiboSecurityContextNotAvailableException("no context"));

        AuthorizationDecision decision = manager().authorize(() -> authentication, createUserRequest());

        assertThat(decision.isGranted()).isFalse();
        verifyNoInteractions(adaptiveDecisionPort);
    }

    @Test
    void unexpectedFailureBeforeAdp_isDenied_insteadOfEscaping() {
        givenAuthenticatedHuman(Set.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
        PolicyEvaluator brokenEvaluator = mock(PolicyEvaluator.class);
        when(brokenEvaluator.evaluate(any(), any(), any(), any())).thenThrow(new IllegalStateException("policy bug"));

        AuthorizationDecision decision = new PolicyBasedAuthorizationManager(
                adaptiveDecisionPort, velocityTracker, contextProvider, brokenEvaluator)
                .authorize(() -> authentication, createUserRequest());

        assertThat(decision.isGranted()).isFalse();
        verifyNoInteractions(adaptiveDecisionPort);
    }

    @Test
    void adpFailure_isDenied() {
        givenPolicyPermitsAndAdpIsConsulted();
        when(adaptiveDecisionPort.evaluate(any())).thenThrow(new IllegalStateException("adp down"));

        assertThat(manager().authorize(() -> authentication, createUserRequest()).isGranted()).isFalse();
    }

    @Test
    void adpWithoutAnswer_isDenied() {
        givenPolicyPermitsAndAdpIsConsulted();
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(null);

        assertThat(manager().authorize(() -> authentication, createUserRequest()).isGranted()).isFalse();
    }

    @Test
    void adpChallenge_isDenied_andFlaggedOnTheRequest() {
        givenPolicyPermitsAndAdpIsConsulted();
        DecisionResponse challenge = new DecisionResponse(
                "d-3", Decision.CHALLENGE, 60.0, 1.0, 0.0, "step-up",
                null, List.of(), null, Instant.now(), 1);
        when(adaptiveDecisionPort.evaluate(any())).thenReturn(challenge);
        RequestAuthorizationContext context = createUserRequest();

        AuthorizationDecision decision = manager().authorize(() -> authentication, context);

        assertThat(decision.isGranted()).isFalse();
        assertThat(context.getRequest().getAttribute("adp.challenge.required")).isEqualTo(true);
        assertThat(context.getRequest().getAttribute("adp.decision.response")).isSameAs(challenge);
    }

    private void givenPolicyPermitsAndAdpIsConsulted() {
        givenAuthenticatedHuman(Set.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
        when(velocityTracker.getVelocity("user-1"))
                .thenReturn(new RequestVelocityTracker.VelocitySnapshot(0, 0));
    }
}
