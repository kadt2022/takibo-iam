package com.takibo.adp.test.config;

import com.takibo.adp.api.AdaptiveDecisionPort;
import com.takibo.adp.api.Decision;
import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.api.DecisionResponse;
import com.takibo.adp.api.DecisionStatus;
import com.takibo.adp.api.Thresholds;
import com.takibo.adp.spring.adapter.RequestVelocityTracker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdpAuthorizationManagerTest {

    private final AdaptiveDecisionPort port = mock(AdaptiveDecisionPort.class);
    private final RequestVelocityTracker tracker = new RequestVelocityTracker();
    private final AuthorizationManager<RequestAuthorizationContext> manager =
            new TestSecurityConfig(port, null, tracker).adpAuthorizationManager();

    private static DecisionResponse response(Decision decision) {
        return new DecisionResponse("d-1", decision, 12.5, 0.8, 0.2, "explanation",
                new Thresholds(75.0, 50.0, "baseline"), List.of(), DecisionStatus.OK, Instant.now(), 1L);
    }

    private static Authentication user(String name, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(name, "n/a", AuthorityUtils.createAuthorityList(authorities));
    }

    private static MockHttpServletRequest http() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }

    private boolean decide(Authentication authentication, MockHttpServletRequest http) {
        AuthorizationDecision decision = (AuthorizationDecision) manager.authorize(
                () -> authentication, new RequestAuthorizationContext(http));
        return decision.isGranted();
    }

    private DecisionRequest sentRequest() {
        ArgumentCaptor<DecisionRequest> captor = ArgumentCaptor.forClass(DecisionRequest.class);
        verify(port).evaluate(captor.capture());
        return captor.getValue();
    }

    @Test
    void missingOrUnauthenticatedUser_isDeniedWithoutConsultingAdp() {
        assertThat(decide(null, http())).isFalse();
        assertThat(decide(UsernamePasswordAuthenticationToken.unauthenticated("alice", "pw"), http())).isFalse();
        verifyNoInteractions(port);
        assertThat(tracker.getVelocity("alice")).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(null, null));
    }

    @Test
    void onlyAnAllowDecisionGrantsAccess() {
        when(port.evaluate(any())).thenReturn(response(Decision.ALLOW));
        assertThat(decide(user("alice", "ROLE_USER"), http())).isTrue();

        when(port.evaluate(any())).thenReturn(response(Decision.DENY));
        assertThat(decide(user("alice", "ROLE_USER"), http())).isFalse();

        when(port.evaluate(any())).thenReturn(response(Decision.CHALLENGE));
        assertThat(decide(user("alice", "ROLE_USER"), http())).isFalse();
    }

    @Test
    void decisionRequest_carriesIdentityRolesPermissionsAndRequestData() {
        when(port.evaluate(any())).thenReturn(response(Decision.ALLOW));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/admin/users");
        http.setRemoteAddr("198.51.100.4");
        http.addHeader("User-Agent", "Mozilla/5.0");
        http.addHeader("Accept", "application/json");
        String sessionId = http.getSession(true).getId();
        Instant before = Instant.now();

        decide(user("admin", "ROLE_USER", "ROLE_ADMIN", "users:write", "SCOPE_openid"), http);

        DecisionRequest request = sentRequest();
        assertThat(request.subjectId()).isEqualTo("admin");
        assertThat(request.organizationId()).isEqualTo("test-org");
        assertThat(request.spaceId()).isEqualTo("test-space");
        assertThat(request.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(request.permissions()).containsExactly("users:write");
        assertThat(request.resourcePath()).isEqualTo("/api/admin/users");
        assertThat(request.httpMethod()).isEqualTo("POST");
        assertThat(request.timestamp()).isBetween(before, Instant.now());
        assertThat(request.ipAddress()).isEqualTo("198.51.100.4");
        assertThat(request.deviceFingerprint())
                .isEqualTo(Integer.toHexString("Mozilla/5.0|application/json".hashCode()));
        assertThat(request.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(request.isProxy()).isFalse();
        assertThat(request.isVpn()).isNull();
        assertThat(request.isTor()).isNull();
        assertThat(request.sessionId()).isEqualTo(sessionId);
        assertThat(request.timeoutMs()).isEqualTo(15);
        assertThat(request.policyVersion()).isEqualTo("1.0");
        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void eachAuthorizedRequest_isCountedInVelocityBeforeEvaluation() {
        when(port.evaluate(any())).thenReturn(response(Decision.ALLOW));

        decide(user("alice", "ROLE_USER"), http());

        DecisionRequest request = sentRequest();
        assertThat(request.requestCountLast10s()).isEqualTo(1);
        assertThat(request.requestCountLast60s()).isEqualTo(1);
        assertThat(request.sessionId()).isNull();
    }

    @Test
    void fingerprint_handlesMissingHeaders() {
        when(port.evaluate(any())).thenReturn(response(Decision.ALLOW));
        MockHttpServletRequest acceptOnly = http();
        acceptOnly.addHeader("Accept", "text/html");
        MockHttpServletRequest agentOnly = http();
        agentOnly.addHeader("User-Agent", "curl/8");

        decide(user("a"), http());
        decide(user("b"), acceptOnly);
        decide(user("c"), agentOnly);

        ArgumentCaptor<DecisionRequest> captor = ArgumentCaptor.forClass(DecisionRequest.class);
        verify(port, times(3)).evaluate(captor.capture());
        assertThat(captor.getAllValues()).extracting(DecisionRequest::deviceFingerprint).containsExactly(
                "UNKNOWN",
                Integer.toHexString("|text/html".hashCode()),
                Integer.toHexString("curl/8|".hashCode()));
    }

    @Test
    void proxyDetection_usesViaOrAForwardedForChain() {
        when(port.evaluate(any())).thenReturn(response(Decision.ALLOW));
        MockHttpServletRequest via = http();
        via.addHeader("Via", "1.1 gateway");
        MockHttpServletRequest chain = http();
        chain.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");
        MockHttpServletRequest single = http();
        single.addHeader("X-Forwarded-For", "198.51.100.1");

        decide(user("a"), via);
        decide(user("b"), chain);
        decide(user("c"), single);

        ArgumentCaptor<DecisionRequest> captor = ArgumentCaptor.forClass(DecisionRequest.class);
        verify(port, times(3)).evaluate(captor.capture());
        assertThat(captor.getAllValues()).extracting(DecisionRequest::isProxy).containsExactly(true, true, false);
    }
}
