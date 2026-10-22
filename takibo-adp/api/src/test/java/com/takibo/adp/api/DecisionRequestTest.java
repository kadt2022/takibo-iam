package com.takibo.adp.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-21T14:00:00Z");

    private static DecisionRequest request(String subjectId, Instant timestamp, int timeoutMs,
                                           Set<String> roles, Set<String> permissions, Map<String, String> metadata) {
        return new DecisionRequest(subjectId, "org", "space", roles, permissions, "/api/data", "GET",
                timestamp, "10.0.0.1", "fp", "agent", "FR", "Paris", false, false, false,
                "session", 1, 5, timeoutMs, "1.0", metadata);
    }

    @Test
    void validRequest_keepsEveryValue() {
        DecisionRequest request = request("alice", NOW, 15, Set.of("USER"), Set.of("read"), Map.of("k", "v"));

        assertThat(request.subjectId()).isEqualTo("alice");
        assertThat(request.organizationId()).isEqualTo("org");
        assertThat(request.spaceId()).isEqualTo("space");
        assertThat(request.roles()).containsExactly("USER");
        assertThat(request.permissions()).containsExactly("read");
        assertThat(request.resourcePath()).isEqualTo("/api/data");
        assertThat(request.httpMethod()).isEqualTo("GET");
        assertThat(request.timestamp()).isEqualTo(NOW);
        assertThat(request.timeoutMs()).isEqualTo(15);
        assertThat(request.policyVersion()).isEqualTo("1.0");
        assertThat(request.metadata()).containsExactlyEntriesOf(Map.of("k", "v"));
    }

    @Test
    void missingCollections_defaultToEmptyImmutableValues() {
        DecisionRequest request = request("alice", NOW, 15, null, null, null);

        assertThat(request.roles()).isEmpty();
        assertThat(request.permissions()).isEmpty();
        assertThat(request.metadata()).isEmpty();
        assertThatThrownBy(() -> request.roles().add("ADMIN")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void subjectIsRequired() {
        for (String subject : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> request(subject, NOW, 15, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("subjectId is required");
        }
    }

    @Test
    void timestampIsRequired() {
        assertThatThrownBy(() -> request("alice", null, 15, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timestamp is required");
    }

    @Test
    void timeoutMustBePositive() {
        for (int timeout : new int[] {0, -1}) {
            assertThatThrownBy(() -> request("alice", NOW, timeout, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("timeoutMs must be positive");
        }
        assertThat(request("alice", NOW, 1, null, null, null).timeoutMs()).isEqualTo(1);
    }
}
