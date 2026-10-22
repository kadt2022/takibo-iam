package com.takibo.adp.spring.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestVelocityTrackerTest {

    private final RequestVelocityTracker tracker = new RequestVelocityTracker();

    @Test
    void unknownOrMissingSubject_hasNoVelocityData() {
        assertThat(tracker.getVelocity(null)).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(null, null));
        assertThat(tracker.getVelocity("nobody")).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(null, null));
    }

    @Test
    void requestsWithoutSubject_areIgnored() {
        tracker.recordRequest(null);

        assertThat(tracker.getVelocity(null)).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(null, null));
    }

    @Test
    void recentRequests_countInBothWindows_perSubject() {
        tracker.recordRequest("alice");
        tracker.recordRequest("alice");
        tracker.recordRequest("alice");
        tracker.recordRequest("bob");

        assertThat(tracker.getVelocity("alice")).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(3, 3));
        assertThat(tracker.getVelocity("bob")).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(1, 1));
    }

    @Test
    void largeBursts_areAllCounted() {
        // Au-delà de 1000 entrées, un nettoyage des requêtes de plus de 90 s est déclenché :
        // les requêtes récentes doivent toutes rester comptées.
        for (int i = 0; i < 1005; i++) {
            tracker.recordRequest("alice");
        }

        assertThat(tracker.getVelocity("alice")).isEqualTo(new RequestVelocityTracker.VelocitySnapshot(1005, 1005));
    }
}
