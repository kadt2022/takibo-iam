package com.takibo.adp.core.model;

import com.takibo.adp.api.DecisionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessContextTest {

    @Test
    void fromRequest_copiesEveryField_andUsesSystemTimezone() {
        Instant now = Instant.parse("2026-10-21T14:00:00Z");
        DecisionRequest request = new DecisionRequest("alice", "org-1", "space-1", Set.of("ADMIN"), Set.of("read"),
                "/api/admin", "POST", now, "10.0.0.1", "fp-1", "agent/1.0", "FR", "Paris", true, false, null,
                "session-1", 3, 12, 15, "1.0", Map.of("k", "v"));

        AccessContext context = AccessContext.fromRequest(request);

        assertThat(context.getSubjectId()).isEqualTo("alice");
        assertThat(context.getOrganizationId()).isEqualTo("org-1");
        assertThat(context.getSpaceId()).isEqualTo("space-1");
        assertThat(context.getRoles()).containsExactly("ADMIN");
        assertThat(context.getPermissions()).containsExactly("read");
        assertThat(context.getTimestamp()).isEqualTo(now);
        assertThat(context.getUserTimezone()).isEqualTo(ZoneId.systemDefault());
        assertThat(context.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(context.getCountry()).isEqualTo("FR");
        assertThat(context.getCity()).isEqualTo("Paris");
        assertThat(context.getIsVpn()).isTrue();
        assertThat(context.getIsProxy()).isFalse();
        assertThat(context.getIsTor()).isNull();
        assertThat(context.getDeviceFingerprint()).isEqualTo("fp-1");
        assertThat(context.getUserAgent()).isEqualTo("agent/1.0");
        assertThat(context.getResourcePath()).isEqualTo("/api/admin");
        assertThat(context.getHttpMethod()).isEqualTo("POST");
        assertThat(context.getSessionId()).isEqualTo("session-1");
        assertThat(context.getRequestCountLast10s()).isEqualTo(3);
        assertThat(context.getRequestCountLast60s()).isEqualTo(12);
        assertThat(context.getMetadata()).containsExactlyEntriesOf(Map.of("k", "v"));
    }

    @Test
    void highRiskNetwork_isTrueWhenAnyFlagIsTrue() {
        assertThat(AccessContext.builder().build().isHighRiskNetwork()).isFalse();
        assertThat(AccessContext.builder().isVpn(false).isProxy(false).isTor(false).build().isHighRiskNetwork()).isFalse();
        assertThat(AccessContext.builder().isVpn(true).build().isHighRiskNetwork()).isTrue();
        assertThat(AccessContext.builder().isProxy(true).build().isHighRiskNetwork()).isTrue();
        assertThat(AccessContext.builder().isTor(true).build().isHighRiskNetwork()).isTrue();
    }

    @Test
    void businessHours_withoutTimestamp_isAssumed() {
        assertThat(AccessContext.builder().build().isDuringBusinessHours()).isTrue();
    }

    @Test
    void businessHours_weekdaysFrom8To18InUserTimezone() {
        assertThat(at("2026-10-21T12:00:00Z").isDuringBusinessHours()).isTrue();   // mercredi
        assertThat(at("2026-10-21T20:00:00Z").isDuringBusinessHours()).isFalse();
        assertThat(at("2026-10-24T12:00:00Z").isDuringBusinessHours()).isFalse();  // samedi
    }

    private static AccessContext at(String instant) {
        return AccessContext.builder().timestamp(Instant.parse(instant)).userTimezone(ZoneOffset.UTC).build();
    }
}
