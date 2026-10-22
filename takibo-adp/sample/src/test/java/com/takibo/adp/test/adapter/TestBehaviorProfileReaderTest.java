package com.takibo.adp.test.adapter;

import com.takibo.adp.api.BehaviorProfileView;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestBehaviorProfileReaderTest {

    private final TestBehaviorProfileReader reader = new TestBehaviorProfileReader();

    private static void assertAgo(Instant instant, Instant before, Instant after, Duration age) {
        assertThat(instant).isBetween(before.minus(age), after.minus(age));
    }

    @Test
    void unknownSubjects_haveNoProfile() {
        assertThat(reader.findBySubjectId("nobody")).isEmpty();
        assertThat(reader.findBySubjectId(null)).isEmpty();
    }

    @Test
    void normalUser_hasAnEstablishedMontrealProfile() {
        Instant before = Instant.now();
        BehaviorProfileView profile = reader.findBySubjectId("user").orElseThrow();
        Instant after = Instant.now();

        assertThat(profile.subjectId()).isEqualTo("user");
        assertThat(profile.fingerprints()).containsOnlyKeys("7a3f8b9c");
        BehaviorProfileView.FingerprintStats device = profile.fingerprints().get("7a3f8b9c");
        assertThat(device.seenCount()).isEqualTo(50);
        assertAgo(device.firstSeen(), before, after, Duration.ofDays(30));
        assertAgo(device.lastSeen(), before, after, Duration.ofHours(1));
        assertThat(profile.velocity()).isEqualTo(new BehaviorProfileView.VelocityStats(5.0, 2.0, 100));
        assertThat(profile.locations()).containsOnlyKeys("CA:Montreal");
        assertThat(profile.locations().get("CA:Montreal").seenCount()).isEqualTo(40);
        assertAgo(profile.locations().get("CA:Montreal").lastSeen(), before, after, Duration.ofHours(1));
    }

    @Test
    void admin_hasALongTrustedProfile() {
        Instant before = Instant.now();
        BehaviorProfileView profile = reader.findBySubjectId("admin").orElseThrow();
        Instant after = Instant.now();

        assertThat(profile.subjectId()).isEqualTo("admin");
        BehaviorProfileView.FingerprintStats device = profile.fingerprints().get("admin123");
        assertThat(device.seenCount()).isEqualTo(100);
        assertAgo(device.firstSeen(), before, after, Duration.ofDays(90));
        assertAgo(device.lastSeen(), before, after, Duration.ofMinutes(10));
        assertThat(profile.velocity()).isEqualTo(new BehaviorProfileView.VelocityStats(3.0, 1.5, 200));
        assertThat(profile.locations().get("CA:Montreal").city()).isEqualTo("Montreal");
        assertThat(profile.locations().get("CA:Montreal").seenCount()).isEqualTo(80);
    }

    @Test
    void suspicious_hasAThinProfileBelowTheVelocityBaselineMinimum() {
        BehaviorProfileView profile = reader.findBySubjectId("suspicious").orElseThrow();

        assertThat(profile.subjectId()).isEqualTo("suspicious");
        assertThat(profile.fingerprints().get("suspicious1").seenCount()).isEqualTo(2);
        assertThat(profile.velocity().sampleCount()).isEqualTo(5);
        assertThat(profile.velocity().stdDev()).isCloseTo(0.5, within(1e-9));
        assertThat(profile.locations()).containsOnlyKeys("RU:Moscow");
        assertThat(profile.locations().get("RU:Moscow").country()).isEqualTo("RU");
    }
}
