package com.takibo.adp.spring.adapter;

import com.takibo.adp.api.DecisionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NoopBehaviorProfileAdaptersTest {

    @Test
    void readerNeverFindsAProfile() {
        assertThat(new NoopBehaviorProfileReader().findBySubjectId("alice")).isEmpty();
        assertThat(new NoopBehaviorProfileReader().findBySubjectId(null)).isEmpty();
    }

    @Test
    void writerAcceptsEveryEventWithoutFailing() {
        DecisionRequest request = new DecisionRequest("alice", null, null, Set.of(), Set.of(), "/", "GET",
                Instant.parse("2026-10-21T14:00:00Z"), null, null, null, null, null, null, null, null, null,
                null, null, 15, "1.0", Map.of());
        NoopBehaviorProfileWriter writer = new NoopBehaviorProfileWriter();

        assertThatCode(() -> {
            writer.recordSuccessfulAccess("alice", request);
            writer.recordFailedAttempt("alice", request);
        }).doesNotThrowAnyException();
    }
}
