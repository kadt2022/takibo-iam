package com.takibo.adp.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorProfileViewTest {

    @Test
    void exposesFingerprintVelocityAndLocationStatistics() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant last = Instant.parse("2026-10-20T00:00:00Z");
        var fingerprint = new BehaviorProfileView.FingerprintStats(12, first, last);
        var velocity = new BehaviorProfileView.VelocityStats(4.5, 1.2, 300L);
        var location = new BehaviorProfileView.LocationStats("FR", "Paris", 8, last);

        BehaviorProfileView profile = new BehaviorProfileView(
                "alice", Map.of("fp-1", fingerprint), velocity, Map.of("FR:Paris", location));

        assertThat(profile.subjectId()).isEqualTo("alice");
        assertThat(profile.fingerprints().get("fp-1").seenCount()).isEqualTo(12);
        assertThat(profile.fingerprints().get("fp-1").firstSeen()).isEqualTo(first);
        assertThat(profile.fingerprints().get("fp-1").lastSeen()).isEqualTo(last);
        assertThat(profile.velocity().avgPerMinute()).isEqualTo(4.5);
        assertThat(profile.velocity().stdDev()).isEqualTo(1.2);
        assertThat(profile.velocity().sampleCount()).isEqualTo(300L);
        assertThat(profile.locations().get("FR:Paris"))
                .isEqualTo(new BehaviorProfileView.LocationStats("FR", "Paris", 8, last));
    }
}
