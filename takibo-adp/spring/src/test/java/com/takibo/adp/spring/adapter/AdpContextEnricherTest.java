package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import com.takibo.adp.spring.config.AdpProperties;
import com.takibo.securitycontext.model.AuthenticationMethod;
import com.takibo.securitycontext.model.SubjectIdentity;
import com.takibo.securitycontext.model.SubjectNature;
import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.model.TemporalContext;
import com.takibo.securitycontext.model.TenantScope;
import com.takibo.securitycontext.model.TransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdpContextEnricherTest {

    private static final String ORG = "11111111-1111-1111-1111-111111111111";
    private static final String SPACE = "22222222-2222-2222-2222-222222222222";

    private final AdpProperties properties = new AdpProperties();
    private final RequestVelocityTracker tracker = new RequestVelocityTracker();
    private final AdpContextEnricher enricher = new AdpContextEnricher(properties, tracker);

    private static TakiboSecurityContext context(TransportContext transport) {
        return TakiboSecurityContext.builder()
                .subject(new SubjectIdentity("alice", SubjectNature.HUMAN, Set.of("USER"), AuthenticationMethod.PASSWORD))
                .tenant(new TenantScope(ORG, SPACE))
                .temporal(new TemporalContext(Instant.parse("2026-10-21T14:00:00Z"), null, null, 1))
                .transport(transport)
                .build();
    }

    private static MockHttpServletRequest http() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }

    @Test
    void buildsRequestFromSecurityContextHttpRequestAndProperties() {
        properties.setTimeoutMs(40);
        properties.setPolicyVersion("2.1");
        tracker.recordRequest("alice");
        tracker.recordRequest("alice");
        MockHttpServletRequest http = http();
        http.addHeader("User-Agent", "Mozilla/5.0");
        http.addHeader("Accept", "text/html");
        http.addHeader("Accept-Language", "fr-FR");
        String sessionId = http.getSession(true).getId();
        Instant before = Instant.now();

        DecisionRequest request = enricher.buildRequest(
                context(new TransportContext(null, "203.0.113.7", null, null, null, null, null, 0)),
                http, Set.of("USER"), Set.of("users:write"));

        assertThat(request.subjectId()).isEqualTo("alice");
        assertThat(request.organizationId()).isEqualTo(ORG);
        assertThat(request.spaceId()).isEqualTo(SPACE);
        assertThat(request.roles()).containsExactly("USER");
        assertThat(request.permissions()).containsExactly("users:write");
        assertThat(request.resourcePath()).isEqualTo("/api/admin/users");
        assertThat(request.httpMethod()).isEqualTo("POST");
        assertThat(request.timestamp()).isBetween(before, Instant.now());
        assertThat(request.ipAddress()).isEqualTo("203.0.113.7");
        assertThat(request.deviceFingerprint())
                .isEqualTo(Integer.toHexString("Mozilla/5.0|text/html|fr-FR".hashCode()));
        assertThat(request.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(request.country()).isNull();
        assertThat(request.city()).isNull();
        assertThat(request.isVpn()).isNull();
        assertThat(request.isProxy()).isFalse();
        assertThat(request.isTor()).isNull();
        assertThat(request.sessionId()).isEqualTo(sessionId);
        assertThat(request.requestCountLast10s()).isEqualTo(2);
        assertThat(request.requestCountLast60s()).isEqualTo(2);
        assertThat(request.timeoutMs()).isEqualTo(40);
        assertThat(request.policyVersion()).isEqualTo("2.1");
        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void missingRolesPermissionsSessionAndHeaders_useSafeDefaults() {
        DecisionRequest request = enricher.buildRequest(context(null), http(), null, null);

        assertThat(request.roles()).isEmpty();
        assertThat(request.permissions()).isEmpty();
        assertThat(request.sessionId()).isNull();
        assertThat(request.deviceFingerprint()).isEqualTo("UNKNOWN");
        assertThat(request.userAgent()).isNull();
        assertThat(request.requestCountLast10s()).isNull();
        assertThat(request.requestCountLast60s()).isNull();
        assertThat(request.timeoutMs()).isEqualTo(15);
        assertThat(request.policyVersion()).isEqualTo("1.0");
    }

    @Test
    void partialHeaders_stillProduceAStableFingerprint() {
        MockHttpServletRequest acceptOnly = http();
        acceptOnly.addHeader("Accept", "application/json");
        MockHttpServletRequest agentOnly = http();
        agentOnly.addHeader("User-Agent", "curl/8");

        assertThat(enricher.buildRequest(context(null), acceptOnly, null, null).deviceFingerprint())
                .isEqualTo(Integer.toHexString("|application/json|".hashCode()));
        assertThat(enricher.buildRequest(context(null), agentOnly, null, null).deviceFingerprint())
                .isEqualTo(Integer.toHexString("curl/8||".hashCode()));
    }

    @Test
    void withoutTransport_clientIpComesFromFirstForwardedForEntry() {
        MockHttpServletRequest http = http();
        http.addHeader("X-Forwarded-For", " 198.51.100.1 , 10.0.0.1");
        http.addHeader("X-Real-IP", "198.51.100.99");

        DecisionRequest request = enricher.buildRequest(context(null), http, null, null);

        assertThat(request.ipAddress()).isEqualTo("198.51.100.1");
        assertThat(request.isProxy()).isTrue();
    }

    @Test
    void withoutTransport_singleForwardedForEntryIsTrimmedAndNotAProxyChain() {
        MockHttpServletRequest http = http();
        http.addHeader("X-Forwarded-For", " 198.51.100.1 ");

        DecisionRequest request = enricher.buildRequest(context(null), http, null, null);

        assertThat(request.ipAddress()).isEqualTo("198.51.100.1");
        assertThat(request.isProxy()).isFalse();
    }

    @Test
    void withoutTransport_blankForwardedForFallsBackToRealIpThenRemoteAddress() {
        MockHttpServletRequest realIp = http();
        realIp.addHeader("X-Forwarded-For", "  ");
        realIp.addHeader("X-Real-IP", " 198.51.100.7 ");
        MockHttpServletRequest blankRealIp = http();
        blankRealIp.addHeader("X-Real-IP", " ");

        assertThat(enricher.buildRequest(context(null), realIp, null, null).ipAddress()).isEqualTo("198.51.100.7");
        assertThat(enricher.buildRequest(context(null), blankRealIp, null, null).ipAddress()).isEqualTo("192.0.2.10");
        assertThat(enricher.buildRequest(context(null), http(), null, null).ipAddress()).isEqualTo("192.0.2.10");
    }

    @Test
    void viaHeader_marksProxy() {
        MockHttpServletRequest http = http();
        http.addHeader("Via", "1.1 gateway");

        assertThat(enricher.buildRequest(context(null), http, null, null).isProxy()).isTrue();
    }

    @Test
    void missingSubjectOrTenant_isRejectedByTheRequestContract() {
        // Le builder du contexte interdit ces cas ; un contexte dégradé (simulé) ne doit pas produire
        // de requête sans sujet : le contrat DecisionRequest refuse la construction.
        TakiboSecurityContext degraded = mock(TakiboSecurityContext.class);
        when(degraded.subject()).thenReturn(null);
        when(degraded.tenant()).thenReturn(null);
        when(degraded.transport()).thenReturn(null);

        assertThatThrownBy(() -> enricher.buildRequest(degraded, http(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subjectId is required");
    }
}
